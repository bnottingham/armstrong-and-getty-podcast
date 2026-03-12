#!/usr/bin/env python3
"""
Armstrong & Getty Podcast — Commercial Remover

Detects commercial breaks using bracket pairs: a START phrase marks where
a commercial begins and an END phrase marks where it ends. Everything
between is removed. Uses fuzzy matching (default 85% word match) to
account for imperfect Whisper transcriptions.

Then uses ffmpeg to produce a commercial-free MP3 for each segment.

Usage:
    python3 ag_remove_commercials.py [--date 2025-03-10] [--input-dir ./ag_transcripts]
    python3 ag_remove_commercials.py --dry-run           # preview detections without cutting

Requirements:
    pip3 install rapidfuzz
    brew install ffmpeg
"""

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional

try:
    from rapidfuzz import fuzz
except ImportError:
    print("ERROR: 'rapidfuzz' not installed. Run: pip3 install rapidfuzz")
    sys.exit(1)


# ── Configuration ────────────────────────────────────────────────────────────

# Bracket pairs: (START_PHRASE, END_PHRASE)
# When the START phrase is found in the transcript, everything from that point
# until the END phrase is detected will be marked as a commercial and removed.
#
# Matching is fuzzy — about 85% of the words need to match to account for
# Whisper transcription errors. Phrases are compared case-insensitively.
#
# Tips for finding good bracket phrases:
#   1. Listen to a few episodes and note the exact transition phrases
#   2. Check your SRT files to see how Whisper transcribed those phrases
#   3. Use phrases that are unique to ad breaks (not said during normal show)
#   4. Longer phrases (4+ words) reduce false positives
#
# You can have multiple bracket pairs — they're all checked independently.

AD_BRACKETS = [
    # Example pairs — replace these with actual A&G phrases you hear:
    ("let me tell you about our sponsors", "and we're back"),
    ("this segment brought to you by", "back to the show"),
    # ("OPENING_PHRASE_HERE", "CLOSING_PHRASE_HERE"),
]

# What percentage of words need to match (0.0 to 1.0)
# 0.85 means 85% of words must match — tolerant of Whisper mishearing a word or two
FUZZY_MATCH_THRESHOLD = 0.85

# When scanning the transcript for a phrase, we look at sliding windows of
# N consecutive SRT entries. This controls the max window size.
# Larger = catches phrases that Whisper split across multiple subtitle entries.
MAX_SRT_WINDOW = 4

# Crossfade duration (ms) when stitching segments together for smoother transitions
CROSSFADE_MS = 100


# ── Data Classes ─────────────────────────────────────────────────────────────

@dataclass
class SrtEntry:
    """A single subtitle entry from an SRT file."""
    index: int
    start_secs: float
    end_secs: float
    text: str


@dataclass
class CommercialBlock:
    """A detected commercial time range."""
    start_secs: float
    end_secs: float
    start_phrase: str
    end_phrase: str
    start_match_score: float
    end_match_score: float
    matched_start_text: str = ""
    matched_end_text: str = ""


# ── SRT Parsing ──────────────────────────────────────────────────────────────

def parse_srt_timestamp(ts: str) -> float:
    """Parse SRT timestamp (HH:MM:SS,mmm) to seconds."""
    ts = ts.strip()
    match = re.match(r"(\d+):(\d+):(\d+)[,.](\d+)", ts)
    if not match:
        return 0.0
    h, m, s, ms = match.groups()
    return int(h) * 3600 + int(m) * 60 + int(s) + int(ms) / 1000


def parse_srt(srt_path: Path) -> list[SrtEntry]:
    """Parse an SRT file into a list of SrtEntry objects."""
    entries = []
    text = srt_path.read_text(encoding="utf-8")

    blocks = re.split(r"\n\s*\n", text.strip())

    for block in blocks:
        lines = block.strip().split("\n")
        if len(lines) < 3:
            continue

        try:
            index = int(lines[0].strip())
        except ValueError:
            continue

        time_match = re.match(
            r"(\d+:\d+:\d+[,.]\d+)\s*-->\s*(\d+:\d+:\d+[,.]\d+)",
            lines[1].strip()
        )
        if not time_match:
            continue

        start_secs = parse_srt_timestamp(time_match.group(1))
        end_secs = parse_srt_timestamp(time_match.group(2))
        entry_text = " ".join(lines[2:]).strip()

        entries.append(SrtEntry(
            index=index,
            start_secs=start_secs,
            end_secs=end_secs,
            text=entry_text,
        ))

    return entries


# ── Fuzzy Matching ───────────────────────────────────────────────────────────

def normalize_text(text: str) -> str:
    """Normalize text for comparison: lowercase, collapse whitespace, strip punctuation."""
    text = text.lower()
    text = re.sub(r"[^\w\s]", "", text)  # remove punctuation
    text = re.sub(r"\s+", " ", text).strip()
    return text


def fuzzy_match_score(phrase: str, candidate: str) -> float:
    """
    Compute a fuzzy match score between a target phrase and a candidate string.

    Uses token-level partial matching so that:
      - Word order matters but small insertions/deletions are tolerated
      - Individual misheard words don't tank the score
      - The phrase can appear anywhere within the candidate text

    Returns a score from 0.0 to 1.0.
    """
    phrase_norm = normalize_text(phrase)
    candidate_norm = normalize_text(candidate)

    if not phrase_norm or not candidate_norm:
        return 0.0

    # Use rapidfuzz's token_set_ratio for word-level fuzzy matching
    # and partial_ratio to handle the phrase appearing within longer text.
    # We take the best of both approaches.
    token_score = fuzz.token_set_ratio(phrase_norm, candidate_norm) / 100.0
    partial_score = fuzz.partial_ratio(phrase_norm, candidate_norm) / 100.0

    # Weight: partial_ratio is better for finding a phrase embedded in longer text,
    # token_set_ratio is better for handling reordered/extra words
    return max(token_score, partial_score)


# ── Bracket Pair Detection ───────────────────────────────────────────────────

def find_phrase_in_entries(
    phrase: str,
    entries: list[SrtEntry],
    start_from_index: int = 0,
) -> Optional[tuple[int, float, float, str, float]]:
    """
    Scan SRT entries for a fuzzy match to the given phrase.
    Checks sliding windows of 1..MAX_SRT_WINDOW consecutive entries.

    Returns: (entry_index, start_timestamp, end_timestamp, matched_text, score) or None
    """
    best_match = None
    best_score = 0.0

    for i in range(start_from_index, len(entries)):
        # Try windows of 1 to MAX_SRT_WINDOW entries starting at position i
        for window_size in range(1, MAX_SRT_WINDOW + 1):
            if i + window_size > len(entries):
                break

            # Combine text from consecutive entries
            window_entries = entries[i:i + window_size]
            combined_text = " ".join(e.text for e in window_entries)

            score = fuzzy_match_score(phrase, combined_text)

            if score >= FUZZY_MATCH_THRESHOLD and score > best_score:
                best_score = score
                best_match = (
                    i,
                    window_entries[0].start_secs,    # start of window
                    window_entries[-1].end_secs,      # end of window
                    combined_text,
                    score,
                )

        # If we found a strong match at this position, return it immediately
        # so we find the FIRST occurrence, not the best one globally
        if best_match and best_score >= FUZZY_MATCH_THRESHOLD:
            return best_match

    return None


def detect_commercials_bracket(
    entries: list[SrtEntry],
    brackets: list[tuple[str, str]],
) -> list[CommercialBlock]:
    """
    Detect commercial blocks using bracket pairs.

    For each bracket pair (START, END):
      - Scan through the SRT entries looking for fuzzy matches to START
      - Once found, scan forward from that point looking for END
      - Everything between START and END is a commercial block
      - Continue scanning for more instances of the same pair
    """
    all_blocks = []

    for start_phrase, end_phrase in brackets:
        print(f"    Scanning for bracket pair:")
        print(f"      START: \"{start_phrase}\"")
        print(f"      END:   \"{end_phrase}\"")

        search_from = 0

        while search_from < len(entries):
            # Find the START phrase
            start_result = find_phrase_in_entries(start_phrase, entries, search_from)

            if start_result is None:
                break  # No more START matches for this pair

            start_idx, start_time, _, start_text, start_score = start_result
            print(f"      ✓ START matched at {format_timestamp(start_time)} "
                  f"(score: {start_score:.0%})")
            print(f"        Transcript: \"{start_text[:80]}...\"" if len(start_text) > 80
                  else f"        Transcript: \"{start_text}\"")

            # Find the END phrase — search forward from after the start match
            end_result = find_phrase_in_entries(end_phrase, entries, start_idx + 1)

            if end_result is None:
                print(f"      ⚠ No END match found after {format_timestamp(start_time)} "
                      f"— skipping this start match")
                # Move past this start match and keep looking
                search_from = start_idx + 1
                continue

            end_idx, _, end_time, end_text, end_score = end_result
            print(f"      ✓ END matched at {format_timestamp(end_time)} "
                  f"(score: {end_score:.0%})")
            print(f"        Transcript: \"{end_text[:80]}...\"" if len(end_text) > 80
                  else f"        Transcript: \"{end_text}\"")

            duration = end_time - start_time
            print(f"      → Commercial block: {format_timestamp(start_time)} - "
                  f"{format_timestamp(end_time)} ({duration:.0f}s)")

            all_blocks.append(CommercialBlock(
                start_secs=start_time,
                end_secs=end_time,
                start_phrase=start_phrase,
                end_phrase=end_phrase,
                start_match_score=start_score,
                end_match_score=end_score,
                matched_start_text=start_text,
                matched_end_text=end_text,
            ))

            # Continue searching after this END match for more commercials
            search_from = end_idx + 1

    # Sort by start time and remove overlaps
    all_blocks.sort(key=lambda b: b.start_secs)

    # Merge overlapping blocks (in case different bracket pairs overlap)
    if len(all_blocks) > 1:
        merged = [all_blocks[0]]
        for block in all_blocks[1:]:
            prev = merged[-1]
            if block.start_secs <= prev.end_secs:
                # Overlapping — extend the previous block
                prev.end_secs = max(prev.end_secs, block.end_secs)
            else:
                merged.append(block)
        all_blocks = merged

    return all_blocks


# ── Audio Cutting with FFmpeg ────────────────────────────────────────────────

def get_audio_duration(mp3_path: Path) -> float:
    """Get audio duration in seconds using ffprobe."""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "quiet", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(mp3_path)],
            capture_output=True, text=True
        )
        return float(result.stdout.strip())
    except Exception:
        return 0.0


def create_commercial_free_mp3(
    mp3_path: Path,
    output_path: Path,
    commercial_blocks: list[CommercialBlock],
    total_duration: float,
) -> bool:
    """
    Use ffmpeg to create a commercial-free version of the MP3.
    Builds a filter_complex that concatenates the non-commercial segments.
    """
    if not commercial_blocks:
        print(f"    ℹ No commercials detected — copying original")
        subprocess.run(["cp", str(mp3_path), str(output_path)], check=True)
        return True

    # Build the list of segments to KEEP (inverse of commercial blocks)
    keep_segments = []
    current_pos = 0.0

    for block in sorted(commercial_blocks, key=lambda b: b.start_secs):
        if block.start_secs > current_pos:
            keep_segments.append((current_pos, block.start_secs))
        current_pos = block.end_secs

    # Add the final segment after the last commercial
    if current_pos < total_duration:
        keep_segments.append((current_pos, total_duration))

    if not keep_segments:
        print(f"    ⚠ No content segments remain — something went wrong")
        return False

    # Build ffmpeg filter_complex for concatenation
    filter_parts = []
    concat_inputs = []

    for i, (start, end) in enumerate(keep_segments):
        label = f"a{i}"
        filter_parts.append(
            f"[0:a]atrim=start={start:.3f}:end={end:.3f},asetpts=PTS-STARTPTS[{label}]"
        )
        concat_inputs.append(f"[{label}]")

    concat_str = "".join(concat_inputs)
    filter_parts.append(f"{concat_str}concat=n={len(keep_segments)}:v=0:a=1[outa]")

    filter_complex = ";\n".join(filter_parts)

    cmd = [
        "ffmpeg", "-y",
        "-i", str(mp3_path),
        "-filter_complex", filter_complex,
        "-map", "[outa]",
        "-codec:a", "libmp3lame",
        "-q:a", "2",  # High quality VBR
        str(output_path),
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if result.returncode != 0:
            print(f"    ✗ ffmpeg error: {result.stderr[:500]}")
            return False
        return True
    except subprocess.TimeoutExpired:
        print(f"    ✗ ffmpeg timed out")
        return False
    except Exception as e:
        print(f"    ✗ ffmpeg failed: {e}")
        return False


# ── Transcript Extraction ────────────────────────────────────────────────────

def get_transcript_for_range(
    entries: list[SrtEntry],
    start_secs: float,
    end_secs: float,
) -> str:
    """
    Collect all SRT entry text that falls within a time range.
    An entry is included if it overlaps with the range at all.
    """
    texts = []
    for e in entries:
        # Include if there's any overlap between the entry and the range
        if e.end_secs > start_secs and e.start_secs < end_secs:
            texts.append(e.text.strip())
    return " ".join(texts)


# ── Formatting Helpers ───────────────────────────────────────────────────────

def format_timestamp(secs: float) -> str:
    """Format seconds as MM:SS or H:MM:SS."""
    m, s = divmod(int(secs), 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{h}:{m:02d}:{s:02d}"
    return f"{m}:{s:02d}"


# ── Main Processing ──────────────────────────────────────────────────────────

def process_segment(
    mp3_path: Path,
    srt_path: Optional[Path],
    output_path: Path,
    report_path: Path,
    dry_run: bool = False,
) -> dict:
    """
    Process a single podcast segment:
    1. Parse the SRT transcript
    2. Scan for bracket pair matches (fuzzy)
    3. Cut commercials and produce clean MP3
    """
    seg_name = mp3_path.stem
    result = {
        "segment": seg_name,
        "success": False,
        "commercials_found": 0,
        "commercial_duration_secs": 0,
        "original_duration_secs": 0,
        "clean_duration_secs": 0,
    }

    print(f"\n  🔍 Processing: {seg_name}")

    # Get audio duration
    total_duration = get_audio_duration(mp3_path)
    if total_duration <= 0:
        print(f"    ✗ Could not determine audio duration")
        return result
    result["original_duration_secs"] = total_duration
    print(f"    Duration: {format_timestamp(total_duration)}")

    # Parse SRT
    if not srt_path or not srt_path.exists():
        print(f"    ✗ No SRT file found at: {srt_path}")
        print(f"      Run ag_transcribe.py first to generate transcripts.")
        return result

    entries = parse_srt(srt_path)
    if not entries:
        print(f"    ✗ SRT file is empty or could not be parsed")
        return result
    print(f"    📝 Loaded {len(entries)} subtitle entries from SRT")

    # Detect commercials using bracket pairs
    print(f"    🔍 Scanning for commercial brackets (threshold: {FUZZY_MATCH_THRESHOLD:.0%})...")
    commercial_blocks = detect_commercials_bracket(entries, AD_BRACKETS)

    # Report
    if commercial_blocks:
        total_commercial = sum(b.end_secs - b.start_secs for b in commercial_blocks)
        print(f"\n    ── Found {len(commercial_blocks)} commercial block(s) "
              f"({format_timestamp(total_commercial)} total) ──")
        for i, b in enumerate(commercial_blocks):
            dur = b.end_secs - b.start_secs
            removed_text = get_transcript_for_range(entries, b.start_secs, b.end_secs)

            print(f"\n      Removed section {i+1}: "
                  f"{format_timestamp(b.start_secs)} - {format_timestamp(b.end_secs)} "
                  f"({dur:.0f}s)")
            print(f"      ┌──────────────────────────────────────────────────")
            # Word-wrap the transcript text to ~76 chars per line for readability
            words = removed_text.split()
            line = "      │ "
            for word in words:
                if len(line) + len(word) + 1 > 78:
                    print(line)
                    line = "      │ " + word
                else:
                    line = line + " " + word if line != "      │ " else line + word
            if line.strip("│ "):
                print(line)
            print(f"      └──────────────────────────────────────────────────")

        content_duration = total_duration - total_commercial
        print(f"\n    Content to keep: {format_timestamp(content_duration)} "
              f"({content_duration / total_duration * 100:.1f}%)")
    else:
        print(f"\n    ℹ No commercials detected in this segment.")

    # Save detection report as JSON
    report_data = {
        "segment": seg_name,
        "total_duration_secs": total_duration,
        "fuzzy_threshold": FUZZY_MATCH_THRESHOLD,
        "bracket_pairs_used": [
            {"start": s, "end": e} for s, e in AD_BRACKETS
        ],
        "commercial_blocks": [
            {
                "start_secs": b.start_secs,
                "end_secs": b.end_secs,
                "duration_secs": b.end_secs - b.start_secs,
                "start_phrase": b.start_phrase,
                "end_phrase": b.end_phrase,
                "start_match_score": b.start_match_score,
                "end_match_score": b.end_match_score,
                "matched_start_text": b.matched_start_text,
                "matched_end_text": b.matched_end_text,
                "removed_transcript": get_transcript_for_range(
                    entries, b.start_secs, b.end_secs
                ),
            }
            for b in commercial_blocks
        ],
    }
    report_path.write_text(json.dumps(report_data, indent=2), encoding="utf-8")
    print(f"    📄 Report saved: {report_path.name}")

    if dry_run:
        print(f"    ⏭  Dry run — skipping MP3 creation")
        result["success"] = True
        result["commercials_found"] = len(commercial_blocks)
        result["commercial_duration_secs"] = sum(
            b.end_secs - b.start_secs for b in commercial_blocks
        )
        return result

    # Create commercial-free MP3
    print(f"    ✂️  Creating commercial-free MP3...")
    success = create_commercial_free_mp3(mp3_path, output_path, commercial_blocks, total_duration)

    if success:
        clean_duration = get_audio_duration(output_path)
        commercial_duration = sum(b.end_secs - b.start_secs for b in commercial_blocks)
        result["success"] = True
        result["commercials_found"] = len(commercial_blocks)
        result["commercial_duration_secs"] = commercial_duration
        result["clean_duration_secs"] = clean_duration

        original_size = mp3_path.stat().st_size / (1024 * 1024)
        clean_size = output_path.stat().st_size / (1024 * 1024)

        print(f"    ✓ Saved: {output_path.name}")
        print(f"      Original: {format_timestamp(total_duration)} ({original_size:.1f} MB)")
        print(f"      Clean:    {format_timestamp(clean_duration)} ({clean_size:.1f} MB)")
        if commercial_duration > 0:
            print(f"      Removed:  {format_timestamp(commercial_duration)} of commercials")
    else:
        print(f"    ✗ Failed to create commercial-free version")

    return result


def run(input_dir: Path, date_str: str, dry_run: bool = False):
    """Process all segments for a given date."""
    audio_dir = input_dir / date_str / "audio"
    srt_dir = input_dir / date_str / "srt"
    clean_dir = input_dir / date_str / "clean"
    report_dir = input_dir / date_str / "reports"

    if not audio_dir.exists():
        print(f"✗ Audio directory not found: {audio_dir}")
        print(f"  Run ag_transcribe.py first to download and transcribe episodes.")
        return

    clean_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    # Find all MP3 files
    mp3_files = sorted(audio_dir.glob("seg*.mp3"))
    if not mp3_files:
        print(f"✗ No segment MP3 files found in: {audio_dir}")
        return

    # Validate that we have bracket pairs configured
    valid_brackets = [(s, e) for s, e in AD_BRACKETS if s.strip() and e.strip()]
    if not valid_brackets:
        print(f"✗ No bracket pairs configured!")
        print(f"  Edit AD_BRACKETS at the top of this script to add your start/end phrases.")
        print(f"  Example:")
        print(f"    AD_BRACKETS = [")
        print(f"        (\"let me tell you about our sponsors\", \"and we're back\"),")
        print(f"    ]")
        print(f"\n  Tip: Check your SRT files to see how Whisper transcribed the ad transitions.")
        return

    print(f"╔══════════════════════════════════════════════════════════════╗")
    print(f"║    Armstrong & Getty — Commercial Remover                   ║")
    print(f"╚══════════════════════════════════════════════════════════════╝")
    print(f"  Date:       {date_str}")
    print(f"  Segments:   {len(mp3_files)}")
    print(f"  Audio dir:  {audio_dir}")
    print(f"  Output dir: {clean_dir}")
    print(f"  Brackets:   {len(valid_brackets)} pair(s) configured")
    print(f"  Threshold:  {FUZZY_MATCH_THRESHOLD:.0%} fuzzy match")
    if dry_run:
        print(f"  Mode:       DRY RUN (detect only, no MP3 output)")

    results = []
    for mp3_path in mp3_files:
        srt_name = mp3_path.stem + ".srt"
        srt_path = srt_dir / srt_name

        output_name = mp3_path.stem + "_no_commercials.mp3"
        output_path = clean_dir / output_name

        report_name = mp3_path.stem + "_report.json"
        report_path = report_dir / report_name

        # Skip if already processed (unless dry run)
        if not dry_run and output_path.exists() and output_path.stat().st_size > 0:
            print(f"\n  ✓ Already processed: {output_name}")
            continue

        result = process_segment(mp3_path, srt_path, output_path, report_path, dry_run)
        results.append(result)

    # Final summary
    successful = [r for r in results if r["success"]]
    if successful:
        total_original = sum(r["original_duration_secs"] for r in successful)
        total_removed = sum(r["commercial_duration_secs"] for r in successful)
        total_clean = sum(r.get("clean_duration_secs", 0) for r in successful)

        print(f"\n{'='*60}")
        print(f"✓ {'DRY RUN ' if dry_run else ''}COMPLETE")
        print(f"{'='*60}")
        print(f"  Segments processed: {len(successful)}/{len(results)}")
        print(f"  Original total:     {format_timestamp(total_original)}")
        if not dry_run and total_clean > 0:
            print(f"  Clean total:        {format_timestamp(total_clean)}")
        print(f"  Commercials found:  {format_timestamp(total_removed)}")
        if total_original > 0 and total_removed > 0:
            print(f"  Content retained:   {(total_original - total_removed) / total_original * 100:.1f}%")

        if not dry_run:
            print(f"\n  Clean files in: {clean_dir.resolve()}")
            for f in sorted(clean_dir.glob("*_no_commercials.mp3")):
                size_mb = f.stat().st_size / (1024 * 1024)
                dur = get_audio_duration(f)
                print(f"    🎧 {f.name} ({format_timestamp(dur)}, {size_mb:.1f} MB)")
    elif results:
        print(f"\n  ⚠ No segments were successfully processed.")
    else:
        print(f"\n  ✓ All segments already processed!")


# ── CLI ──────────────────────────────────────────────────────────────────────

def main():
    global FUZZY_MATCH_THRESHOLD
    parser = argparse.ArgumentParser(
        description="Remove commercials from Armstrong & Getty podcast segments"
    )
    parser.add_argument(
        "--date", "-d",
        default=datetime.now().strftime("%Y-%m-%d"),
        help="Date to process (YYYY-MM-DD, default: today)"
    )
    parser.add_argument(
        "--input-dir", "-i",
        default="./ag_transcripts",
        help="Base directory with transcripts/audio (default: ./ag_transcripts)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Detect commercials and show report without creating MP3s"
    )
    parser.add_argument(
        "--threshold", "-t",
        type=float,
        default=None,
        help=f"Fuzzy match threshold 0.0-1.0 (default: {FUZZY_MATCH_THRESHOLD})"
    )
    args = parser.parse_args()

    if args.threshold is not None:
        FUZZY_MATCH_THRESHOLD = args.threshold

    input_dir = Path(args.input_dir)
    if not input_dir.exists():
        print(f"✗ Input directory not found: {input_dir}")
        print(f"  Run ag_transcribe.py first.")
        sys.exit(1)

    import shutil
    if not shutil.which("ffmpeg"):
        print("ERROR: ffmpeg not found. Install with: brew install ffmpeg")
        sys.exit(1)

    run(input_dir, args.date, args.dry_run)


if __name__ == "__main__":
    main()