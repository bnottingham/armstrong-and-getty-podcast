#!/usr/bin/env python3
"""
Armstrong & Getty Podcast Transcriber (faster-whisper, parallel)
with Firebase Firestore upload and continuous loop mode.

Downloads today's podcast segments from the RSS feed,
transcribes segments in parallel using faster-whisper (large-v3) with
GPU acceleration, outputs SRT subtitle files, and uploads each segment's
SRT to Firebase Firestore.

Usage:
    python3 ag_transcribe.py [--output-dir ./ag_transcripts] [--workers 4]

Requirements:
    pip3 install faster-whisper requests feedparser google-cloud-firestore firebase-admin

    brew install ffmpeg

The faster-whisper large-v3 model (~3GB) is downloaded automatically on
first run and cached in ~/.cache/huggingface/
"""

import argparse
import os
import re
import subprocess
import sys
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
from email.utils import parsedate_to_datetime
from pathlib import Path

try:
    import requests
except ImportError:
    print("ERROR: 'requests' not installed. Run: pip3 install requests")
    sys.exit(1)

try:
    import feedparser
except ImportError:
    print("ERROR: 'feedparser' not installed. Run: pip3 install feedparser")
    sys.exit(1)

try:
    from faster_whisper import WhisperModel
except ImportError:
    print("ERROR: 'faster-whisper' not installed. Run: pip3 install faster-whisper")
    sys.exit(1)

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("ERROR: 'firebase-admin' not installed. Run: pip3 install firebase-admin")
    sys.exit(1)


# ── Configuration ─────────────────────────────────────────────────────────────

RSS_FEED_URL = (
    "https://www.omnycontent.com/d/playlist/"
    "e73c998e-6e60-432f-8610-ae210140c5b1/"
    "0516ff28-c0d6-492a-b264-ae3900375fc8/"
    "4db37684-c7ed-4964-843c-ae3900375fd7/podcast.rss"
)

WHISPER_MODEL_SIZE = "large-v3"
DEFAULT_WORKERS = 4  # Good for 64GB RAM

# Path to your Firebase service account key JSON.
# Download from Firebase Console → Project Settings → Service Accounts → Generate New Private Key.
# Place it next to this script or set the env var.
FIREBASE_CREDENTIALS_PATH = os.environ.get(
    "FIREBASE_CREDENTIALS_PATH",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "firebase-service-account.json")
)

LOOP_INTERVAL_MINUTES = 30


# ── Firebase Initialization ───────────────────────────────────────────────────

_firestore_client = None

def get_firestore_client():
    """Lazy-init Firebase and return Firestore client."""
    global _firestore_client
    if _firestore_client is not None:
        return _firestore_client

    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        print(f"  ⚠ Firebase credentials not found at: {FIREBASE_CREDENTIALS_PATH}")
        print(f"    Set FIREBASE_CREDENTIALS_PATH env var or place firebase-service-account.json next to this script.")
        print(f"    SRT files will still be saved locally but NOT uploaded to Firebase.")
        return None

    try:
        cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
        firebase_admin.initialize_app(cred)
        _firestore_client = firestore.client()
        print(f"  ✓ Firebase initialized successfully")
        return _firestore_client
    except Exception as e:
        print(f"  ⚠ Firebase initialization failed: {e}")
        print(f"    SRT files will still be saved locally but NOT uploaded to Firebase.")
        return None


def upload_srt_to_firestore(date_str: str, segment_index: int, srt_path: Path, segment_title: str):
    """
    Upload a single segment's SRT content to Firestore.

    Firestore structure:
        transcripts/{date}/segments/{segIndex}
            - srtContent: String (the full SRT file content)
            - segmentIndex: Int
            - title: String
            - uploadedAt: Timestamp
    """
    db = get_firestore_client()
    if db is None:
        return False

    try:
        srt_content = srt_path.read_text(encoding="utf-8")

        doc_ref = (
            db.collection("transcripts")
            .document(date_str)
            .collection("segments")
            .document(str(segment_index))
        )

        doc_ref.set({
            "srtContent": srt_content,
            "segmentIndex": segment_index,
            "title": segment_title,
            "uploadedAt": firestore.SERVER_TIMESTAMP,
        })

        tprint(f"    ☁ Uploaded seg{segment_index} SRT to Firestore ({date_str})")
        return True
    except Exception as e:
        tprint(f"    ⚠ Firestore upload failed for seg{segment_index}: {e}")
        return False


def check_segment_uploaded(date_str: str, segment_index: int) -> bool:
    """Check if a segment's transcription has already been uploaded to Firestore."""
    db = get_firestore_client()
    if db is None:
        return False

    try:
        doc_ref = (
            db.collection("transcripts")
            .document(date_str)
            .collection("segments")
            .document(str(segment_index))
        )
        doc = doc_ref.get()
        return doc.exists
    except Exception:
        return False


# ── Thread-safe printing ──────────────────────────────────────────────────────

_print_lock = threading.Lock()

def tprint(*args, **kwargs):
    """Thread-safe print."""
    with _print_lock:
        print(*args, **kwargs)


# ── Progress tracker ──────────────────────────────────────────────────────────

class ProgressTracker:
    """Tracks progress of multiple parallel transcription jobs."""

    def __init__(self):
        self._lock = threading.Lock()
        self._jobs = {}  # label -> {status, pct, elapsed, duration}
        self._active = True

    def register(self, label: str, duration_secs: float):
        with self._lock:
            self._jobs[label] = {
                "status": "waiting",
                "pct": 0.0,
                "elapsed": 0.0,
                "duration": duration_secs,
                "start_time": None,
            }

    def start(self, label: str):
        with self._lock:
            self._jobs[label]["status"] = "transcribing"
            self._jobs[label]["start_time"] = time.time()

    def update(self, label: str, pct: float):
        with self._lock:
            job = self._jobs[label]
            job["pct"] = pct
            if job["start_time"]:
                job["elapsed"] = time.time() - job["start_time"]

    def complete(self, label: str):
        with self._lock:
            self._jobs[label]["status"] = "done"
            self._jobs[label]["pct"] = 100.0
            if self._jobs[label]["start_time"]:
                self._jobs[label]["elapsed"] = time.time() - self._jobs[label]["start_time"]

    def fail(self, label: str):
        with self._lock:
            self._jobs[label]["status"] = "failed"

    def stop(self):
        self._active = False

    def display_loop(self):
        """Continuously redraw progress for all jobs until stopped."""
        while self._active:
            self._draw()
            time.sleep(0.5)
        # Final draw
        self._draw()

    def _draw(self):
        with self._lock:
            if not self._jobs:
                return

            lines = []
            for label, job in self._jobs.items():
                status = job["status"]
                pct = job["pct"]
                elapsed = job["elapsed"]
                duration = job["duration"]

                if status == "done":
                    speed = duration / elapsed if elapsed > 0 else 0
                    lines.append(f"  ✓ {label}: Done ({format_duration(elapsed)}, "
                                 f"{speed:.1f}x realtime)")
                elif status == "failed":
                    lines.append(f"  ✗ {label}: Failed")
                elif status == "transcribing":
                    bar_width = 30
                    filled = int(bar_width * pct / 100)
                    bar = "█" * filled + "░" * (bar_width - filled)
                    elapsed_str = format_duration(elapsed)
                    lines.append(f"  ⟳ {label}: [{bar}] {pct:5.1f}% ({elapsed_str})")
                else:
                    lines.append(f"  ◦ {label}: Queued")

            # Move cursor up and overwrite
            output = "\r" + "\033[K" + ("\033[K\n".join(lines)) + "\033[K"
            # Move cursor up to beginning of our block for next redraw
            num_lines = len(lines)

        # Print outside lock
        sys.stdout.write(f"\033[{num_lines}A" if num_lines > 0 else "")
        sys.stdout.write(output + "\n")
        sys.stdout.flush()


# ── Utilities ─────────────────────────────────────────────────────────────────

def format_time_srt(seconds: float) -> str:
    """Convert seconds (float) to SRT timestamp format HH:MM:SS,mmm"""
    ms = int(seconds * 1000)
    hours = ms // 3_600_000
    ms %= 3_600_000
    minutes = ms // 60_000
    ms %= 60_000
    secs = ms // 1_000
    millis = ms % 1_000
    return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"


def progress_bar(current: int, total: int, width: int = 40, label: str = "") -> str:
    if total <= 0:
        return f"  {label} [{'?' * width}] ?%"
    pct = min(current / total, 1.0)
    filled = int(width * pct)
    bar = "█" * filled + "░" * (width - filled)
    return f"  {label} [{bar}] {pct * 100:.1f}%"


def download_with_progress(url: str, dest_path: str, label: str = "Downloading") -> None:
    response = requests.get(url, stream=True, headers={"User-Agent": "AGTranscriber/1.0"})
    response.raise_for_status()
    total = int(response.headers.get("content-length", 0))
    downloaded = 0
    chunk_size = 65536

    with open(dest_path, "wb") as f:
        for chunk in response.iter_content(chunk_size=chunk_size):
            f.write(chunk)
            downloaded += len(chunk)
            print(f"\r{progress_bar(downloaded, total, label=label)}", end="", flush=True)
    print()


def format_duration(secs: float) -> str:
    m, s = divmod(int(secs), 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{h}h {m}m {s}s"
    elif m > 0:
        return f"{m}m {s}s"
    return f"{s}s"


def get_audio_duration(path: Path) -> float:
    """Get audio duration in seconds using ffprobe."""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "quiet", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True
        )
        return float(result.stdout.strip())
    except Exception:
        return 0.0


# ── RSS Feed Parsing ──────────────────────────────────────────────────────────

def fetch_and_group_episodes(target_dates: list) -> dict:
    print(f"\n{'='*60}")
    print("FETCHING RSS FEED")
    print(f"{'='*60}")
    print(f"  URL: {RSS_FEED_URL}")

    feed = feedparser.parse(RSS_FEED_URL)
    if feed.bozo and not feed.entries:
        print(f"  ✗ ERROR: Failed to parse RSS feed: {feed.bozo_exception}")
        return {}

    print(f"  ✓ Found {len(feed.entries)} items in feed")

    days = {}
    for entry in feed.entries:
        try:
            pub_date = parsedate_to_datetime(entry.published)
        except Exception:
            continue

        date_str = pub_date.strftime("%Y-%m-%d")
        if date_str not in target_dates:
            continue

        audio_url = None
        for link in entry.get("links", []):
            if link.get("type", "").startswith("audio/") or link.get("href", "").endswith(".mp3"):
                audio_url = link["href"]
                break
        if not audio_url:
            for enc in entry.get("enclosures", []):
                if enc.get("type", "").startswith("audio/"):
                    audio_url = enc["href"]
                    break
        if not audio_url:
            continue

        duration_str = entry.get("itunes_duration", "0")
        try:
            parts = str(duration_str).split(":")
            if len(parts) == 1:
                dur_secs = int(parts[0])
            elif len(parts) == 2:
                dur_secs = int(parts[0]) * 60 + int(parts[1])
            else:
                dur_secs = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
        except (ValueError, IndexError):
            dur_secs = 0

        segment = {
            "title": entry.get("title", "Unknown"),
            "description": re.sub(r"<[^>]+>", "", entry.get("summary", "")),
            "pub_date": pub_date,
            "audio_url": audio_url,
            "duration_secs": dur_secs,
        }
        days.setdefault(date_str, []).append(segment)

    for date_str in days:
        days[date_str].sort(key=lambda s: s["pub_date"])

    for date_str in sorted(days.keys(), reverse=True):
        segs = days[date_str]
        total_dur = sum(s["duration_secs"] for s in segs)
        print(f"\n  📅 {date_str}: {len(segs)} segments ({total_dur // 60} min total)")
        for i, seg in enumerate(segs):
            dur_min = seg["duration_secs"] // 60
            uploaded = "☁" if check_segment_uploaded(date_str, i) else "○"
            print(f"     {uploaded} {i+1}. {seg['title']} ({dur_min} min)")

    return days


# ── Audio Download ────────────────────────────────────────────────────────────

def download_segment(url: str, dest_path: Path, label: str) -> None:
    if dest_path.exists() and dest_path.stat().st_size > 0:
        size_mb = dest_path.stat().st_size / (1024 * 1024)
        print(f"  ✓ {label}: Already downloaded ({size_mb:.1f} MB)")
        return
    download_with_progress(url, str(dest_path), label=label)


# ── Transcription ─────────────────────────────────────────────────────────────

def write_srt(segments, srt_path: Path) -> int:
    """Write faster-whisper segments to an SRT file."""
    count = 0
    with open(srt_path, "w", encoding="utf-8") as f:
        for i, seg in enumerate(segments, 1):
            start = format_time_srt(seg.start)
            end = format_time_srt(seg.end)
            text = seg.text.strip()
            if text:
                f.write(f"{i}\n")
                f.write(f"{start} --> {end}\n")
                f.write(f"{text}\n\n")
                count += 1
    return count


def transcribe_one_segment(
    model: WhisperModel,
    mp3_path: Path,
    srt_path: Path,
    label: str,
    duration_secs: float,
    tracker: ProgressTracker,
    date_str: str,
    segment_index: int,
    segment_title: str,
) -> dict:
    """Transcribe a single segment. Called from a worker thread."""
    result = {"label": label, "srt_path": srt_path, "success": False, "entries": 0, "elapsed": 0}

    # Check if already uploaded to Firestore
    already_uploaded = check_segment_uploaded(date_str, segment_index)

    if srt_path.exists() and srt_path.stat().st_size > 0:
        if already_uploaded:
            tracker.complete(label)
            result["success"] = True
            result["entries"] = -1  # already existed
            return result
        else:
            # SRT exists locally but not uploaded — upload it now
            tracker.complete(label)
            upload_srt_to_firestore(date_str, segment_index, srt_path, segment_title)
            result["success"] = True
            result["entries"] = -1
            return result

    # Get actual duration for progress tracking
    actual_duration = get_audio_duration(mp3_path)
    if actual_duration <= 0:
        actual_duration = duration_secs if duration_secs > 0 else 3600.0

    tracker.start(label)
    t0 = time.time()

    try:
        segments_iter, info = model.transcribe(
            str(mp3_path),
            language="en",
            task="transcribe",
            beam_size=5,
            vad_filter=True,  # Skip silence for speed
        )

        # Collect segments and update progress based on timestamps
        collected_segments = []
        for seg in segments_iter:
            collected_segments.append(seg)
            if actual_duration > 0:
                pct = min(seg.end / actual_duration * 100, 99.9)
                tracker.update(label, pct)

        entry_count = write_srt(collected_segments, srt_path)

        elapsed = time.time() - t0
        tracker.complete(label)
        result["success"] = True
        result["entries"] = entry_count
        result["elapsed"] = elapsed

        # Upload to Firestore immediately after transcription
        upload_srt_to_firestore(date_str, segment_index, srt_path, segment_title)

    except Exception as e:
        tracker.fail(label)
        tprint(f"    ✗ {label} failed: {e}")

    return result


# ── Single Run ────────────────────────────────────────────────────────────────

def run_once(output_dir: Path, num_workers: int, model_holder: dict):
    """Run one transcription pass for today's episodes. Returns True if work was done."""

    today = datetime.now()
    target_dates = [today.strftime("%Y-%m-%d")]
    print(f"\n  Target date: {target_dates[0]} (today)")

    # Fetch RSS feed
    days = fetch_and_group_episodes(target_dates)
    if not days:
        print("\n  ⚠ No episodes found for today.")
        return False

    # Download all audio first
    print(f"\n{'='*60}")
    print("DOWNLOADING AUDIO")
    print(f"{'='*60}")

    all_jobs = []  # (mp3_path, srt_path, label, duration, title, date_str, seg_index)

    for date_str in sorted(days.keys(), reverse=True):
        segments = days[date_str]
        audio_dir = output_dir / date_str / "audio"
        srt_dir = output_dir / date_str / "srt"
        audio_dir.mkdir(parents=True, exist_ok=True)
        srt_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n  📅 {date_str}:")
        for i, seg in enumerate(segments):
            seg_label = f"Seg {i+1}/{len(segments)}"
            safe_title = re.sub(r'[^\w\s-]', '', seg["title"])[:60].strip().replace(" ", "_")
            base_name = f"seg{i:02d}_{safe_title}"
            mp3_path = audio_dir / f"{base_name}.mp3"
            srt_path = srt_dir / f"{base_name}.srt"

            download_segment(seg["audio_url"], mp3_path, f"{seg_label} ({seg['title'][:40]})")

            short_title = seg["title"][:35]
            job_label = f"{date_str} {short_title}"
            all_jobs.append((mp3_path, srt_path, job_label, seg["duration_secs"], seg["title"], date_str, i))

    # Check how many actually need transcription (including upload check)
    pending = []
    for mp3, srt, lbl, dur, t, ds, si in all_jobs:
        srt_exists = srt.exists() and srt.stat().st_size > 0
        already_uploaded = check_segment_uploaded(ds, si)
        if not srt_exists or not already_uploaded:
            pending.append((mp3, srt, lbl, dur, t, ds, si))

    already_done = len(all_jobs) - len(pending)

    if already_done > 0:
        print(f"\n  ✓ {already_done} segment(s) already transcribed & uploaded, skipping")

    if not pending:
        print(f"\n  ✓ All segments already transcribed and uploaded!")
        _print_final_summary(output_dir, days)
        return False

    # Load model (only once, reuse across loops)
    if model_holder.get("model") is None:
        print(f"\n{'='*60}")
        print(f"LOADING MODEL: faster-whisper {WHISPER_MODEL_SIZE}")
        print(f"{'='*60}")

        # Detect compute device
        compute_type = "float32"
        device = "cpu"
        try:
            import torch
            if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
                device = "cpu"
                print(f"  Device     : CPU (CTranslate2 optimized)")
            elif torch.cuda.is_available():
                device = "cuda"
                compute_type = "float16"
                print(f"  Device     : CUDA GPU (float16)")
            else:
                print(f"  Device     : CPU (CTranslate2 optimized)")
        except ImportError:
            print(f"  Device     : CPU (CTranslate2 optimized)")

        model_holder["device"] = device
        model_holder["compute_type"] = compute_type

        print(f"  Loading model (may take 10-20 seconds)...")
        t0 = time.time()
        model_holder["model"] = WhisperModel(
            WHISPER_MODEL_SIZE,
            device=device,
            compute_type=compute_type,
            cpu_threads=os.cpu_count() or 8,
            num_workers=num_workers,
        )
        load_time = time.time() - t0
        print(f"  ✓ Model loaded in {format_duration(load_time)}")

    model = model_holder["model"]
    device = model_holder["device"]
    compute_type = model_holder["compute_type"]

    # Transcribe in parallel
    actual_workers = min(num_workers, len(pending))
    print(f"\n{'='*60}")
    print(f"TRANSCRIBING: {len(pending)} segments with {actual_workers} workers")
    print(f"{'='*60}")

    total_audio = sum(dur for _, _, _, dur, _, _, _ in pending)
    print(f"  Total audio to transcribe: {format_duration(total_audio)}")
    print()

    # Set up progress tracker
    tracker = ProgressTracker()
    for _, _, label, dur, _, _, _ in pending:
        tracker.register(label, dur)

    # Reserve blank lines for the progress display
    for _ in pending:
        print()

    # Start display thread
    display_thread = threading.Thread(target=tracker.display_loop, daemon=True)
    display_thread.start()

    t_start = time.time()
    results = []

    if actual_workers == 1:
        for mp3_path, srt_path, label, dur, title, date_str, seg_idx in pending:
            r = transcribe_one_segment(model, mp3_path, srt_path, label, dur, tracker, date_str, seg_idx, title)
            results.append(r)
    else:
        n_models = min(actual_workers, 3)
        print(f"\r\033[{len(pending)}A  Loading {n_models} model instances for true parallelism...")
        for _ in pending:
            print()

        models = [model]
        for i in range(1, n_models):
            models.append(WhisperModel(
                WHISPER_MODEL_SIZE,
                device=device,
                compute_type=compute_type,
                cpu_threads=max((os.cpu_count() or 8) // n_models, 2),
            ))

        def _worker(idx, mp3_path, srt_path, label, dur, date_str, seg_idx, title):
            m = models[idx % n_models]
            return transcribe_one_segment(m, mp3_path, srt_path, label, dur, tracker, date_str, seg_idx, title)

        with ThreadPoolExecutor(max_workers=n_models) as executor:
            futures = {}
            for idx, (mp3_path, srt_path, label, dur, title, date_str, seg_idx) in enumerate(pending):
                f = executor.submit(_worker, idx, mp3_path, srt_path, label, dur, date_str, seg_idx, title)
                futures[f] = label

            for future in as_completed(futures):
                results.append(future.result())

    total_elapsed = time.time() - t_start
    tracker.stop()
    time.sleep(0.6)

    # Summary
    print(f"\n{'='*60}")
    print("✓ TRANSCRIPTION COMPLETE!")
    print(f"{'='*60}")
    successful = [r for r in results if r["success"]]
    failed = [r for r in results if not r["success"]]
    total_transcribed_audio = sum(dur for _, _, _, dur, _, _, _ in pending)

    print(f"  Segments transcribed: {len(successful)}/{len(pending)}")
    if failed:
        print(f"  Failed: {len(failed)}")
        for r in failed:
            print(f"    ✗ {r['label']}")
    print(f"  Total audio: {format_duration(total_transcribed_audio)}")
    print(f"  Total wall time: {format_duration(total_elapsed)}")
    if total_elapsed > 0:
        print(f"  Effective speed: {total_transcribed_audio / total_elapsed:.1f}x realtime")

    _print_final_summary(output_dir, days)
    return True


def _print_final_summary(output_dir: Path, days: dict):
    print(f"\n  SRT files in: {output_dir.resolve()}")
    for date_str in sorted(days.keys(), reverse=True):
        srt_dir = output_dir / date_str / "srt"
        if srt_dir.exists():
            srt_files = sorted(srt_dir.glob("*.srt"))
            if srt_files:
                print(f"\n  📅 {date_str}/srt/")
                for f in srt_files:
                    size_kb = f.stat().st_size / 1024
                    print(f"     📄 {f.name} ({size_kb:.1f} KB)")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Transcribe Armstrong & Getty podcast segments to SRT (parallel, GPU-accelerated)"
    )
    parser.add_argument(
        "--output-dir", "-o",
        default="./ag_transcripts",
        help="Output directory (default: ./ag_transcripts)"
    )
    parser.add_argument(
        "--workers", "-w",
        type=int, default=DEFAULT_WORKERS,
        help=f"Number of parallel transcription workers (default: {DEFAULT_WORKERS})"
    )
    parser.add_argument(
        "--once",
        action="store_true",
        help="Run once and exit instead of looping"
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    num_workers = args.workers

    # Check for ffmpeg
    import shutil
    if not shutil.which("ffmpeg"):
        print("ERROR: ffmpeg not found. Install with: brew install ffmpeg")
        sys.exit(1)

    print("╔══════════════════════════════════════════════════════════════╗")
    print("║   Armstrong & Getty Transcriber (faster-whisper, parallel)  ║")
    print("║               with Firebase upload & loop mode              ║")
    print("╚══════════════════════════════════════════════════════════════╝")
    print(f"  Output dir : {output_dir.resolve()}")
    print(f"  Model      : faster-whisper {WHISPER_MODEL_SIZE}")
    print(f"  Workers    : {num_workers} parallel")
    print(f"  Mode       : {'Single run' if args.once else f'Loop every {LOOP_INTERVAL_MINUTES} min'}")

    # Initialize Firebase
    get_firestore_client()

    # Model holder — persists across loop iterations
    model_holder = {}

    if args.once:
        run_once(output_dir, num_workers, model_holder)
    else:
        while True:
            try:
                now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                print(f"\n{'─'*60}")
                print(f"  🔄 Check at {now}")
                print(f"{'─'*60}")

                run_once(output_dir, num_workers, model_holder)

                next_check = datetime.now() + timedelta(minutes=LOOP_INTERVAL_MINUTES)
                print(f"\n  💤 Sleeping until {next_check.strftime('%H:%M:%S')} "
                      f"({LOOP_INTERVAL_MINUTES} min)...")
                time.sleep(LOOP_INTERVAL_MINUTES * 60)

            except KeyboardInterrupt:
                print("\n\n  ⏹ Stopped by user. Goodbye!")
                break
            except Exception as e:
                print(f"\n  ✗ Error in loop iteration: {e}")
                print(f"  Retrying in {LOOP_INTERVAL_MINUTES} minutes...")
                time.sleep(LOOP_INTERVAL_MINUTES * 60)


if __name__ == "__main__":
    main()