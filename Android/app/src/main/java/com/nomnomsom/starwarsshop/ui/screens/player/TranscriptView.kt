package com.nomnomsom.starwarsshop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.starwarsshop.data.model.Segment
import com.nomnomsom.starwarsshop.data.model.TimedWord
import com.nomnomsom.starwarsshop.data.model.TranscriptEntity
import com.nomnomsom.starwarsshop.data.model.TranscriptState
import com.nomnomsom.starwarsshop.transcription.ModelState
import com.nomnomsom.starwarsshop.transcription.ModelStatus
import com.nomnomsom.starwarsshop.ui.theme.Gold
import com.nomnomsom.starwarsshop.ui.theme.TextMuted
import com.nomnomsom.starwarsshop.ui.theme.TextPrimary
import com.nomnomsom.starwarsshop.ui.theme.TextSecondary

/**
 * Displays the live transcript with word-level highlighting synced to playback.
 *
 * @param transcripts List of transcript entities, one per segment
 * @param segments List of segment metadata
 * @param currentSegmentIndex Currently playing segment
 * @param positionInSegmentMs Current position within the active segment
 * @param isTranscribing Whether transcription is in progress
 * @param modelStatus Status of the Vosk model download
 * @param parseWords Function to parse words JSON
 * @param onTranscribeSegment Callback to transcribe a specific segment by index
 */
@Composable
fun TranscriptView(
    transcripts: List<TranscriptEntity>,
    segments: List<Segment>,
    currentSegmentIndex: Int,
    positionInSegmentMs: Long,
    isTranscribing: Boolean,
    modelStatus: ModelStatus,
    parseWords: (String) -> List<TimedWord>,
    onTranscribeSegment: (Int) -> Unit
) {
    // Build a flat list of transcript paragraphs (one per segment)
    val segmentTranscripts = remember(transcripts) {
        segments.mapIndexed { index, segment ->
            val transcript = transcripts.find { it.segmentIndex == index }
            SegmentTranscriptData(
                segmentIndex = index,
                segment = segment,
                transcript = transcript,
                state = TranscriptState.fromValue(transcript?.state ?: "none")
            )
        }
    }

    // Show model download status if downloading
    if (modelStatus.state == ModelState.DOWNLOADING) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "⟳",
                fontSize = 32.sp,
                color = Gold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Downloading speech model…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { modelStatus.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Gold,
                trackColor = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${modelStatus.progressPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        return
    }

    // Always show segment list — each segment has its own Transcribe button or content

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        segmentTranscripts.forEachIndexed { index, data ->
            TranscriptSegmentBlock(
                data = data,
                isCurrentSegment = index == currentSegmentIndex,
                positionMs = if (index == currentSegmentIndex) positionInSegmentMs else -1L,
                parseWords = parseWords,
                onTranscribe = { onTranscribeSegment(data.segmentIndex) }
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

private data class SegmentTranscriptData(
    val segmentIndex: Int,
    val segment: Segment,
    val transcript: TranscriptEntity?,
    val state: TranscriptState
)

@Composable
private fun TranscriptSegmentBlock(
    data: SegmentTranscriptData,
    isCurrentSegment: Boolean,
    positionMs: Long,
    parseWords: (String) -> List<TimedWord>,
    onTranscribe: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Segment header
        Text(
            text = if (data.segment.hour == "OMT") "One More Thing"
            else "Hour ${data.segment.hour}",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (data.state) {
            TranscriptState.DONE -> {
                val words = remember(data.transcript?.wordsJson) {
                    parseWords(data.transcript?.wordsJson ?: "[]")
                }

                if (words.isNotEmpty() && isCurrentSegment) {
                    // Highlighted transcript with word-level sync
                    HighlightedTranscript(
                        words = words,
                        currentPositionMs = positionMs
                    )
                } else if (words.isNotEmpty()) {
                    // Static transcript (not current segment)
                    Text(
                        text = words.joinToString(" ") { it.word },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = TextSecondary
                        )
                    )
                } else {
                    // Fallback to plain text
                    Text(
                        text = data.transcript?.fullText ?: "",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = TextSecondary
                        )
                    )
                }
            }

            TranscriptState.TRANSCRIBING -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            color = Gold,
                            trackColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transcribing…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            TranscriptState.ERROR -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Transcription failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TranscribeButton(onClick = onTranscribe, label = "Retry")
                }
            }

            TranscriptState.NONE -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TranscribeButton(onClick = onTranscribe, label = "Transcribe")
                }
            }
        }
    }
}

@Composable
private fun TranscribeButton(onClick: () -> Unit, label: String) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold.copy(alpha = 0.12f),
            contentColor = Gold
        )
    ) {
        Icon(
            Icons.Filled.RecordVoiceOver,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HighlightedTranscript(
    words: List<TimedWord>,
    currentPositionMs: Long
) {
    val annotatedText = buildAnnotatedString {
        for ((index, word) in words.withIndex()) {
            val isSpoken = currentPositionMs >= word.startMs
            val isCurrent = currentPositionMs >= word.startMs && currentPositionMs < word.endMs

            when {
                isCurrent -> {
                    withStyle(
                        SpanStyle(
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            background = Gold.copy(alpha = 0.12f)
                        )
                    ) {
                        append(word.word)
                    }
                }
                isSpoken -> {
                    withStyle(
                        SpanStyle(
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                    ) {
                        append(word.word)
                    }
                }
                else -> {
                    withStyle(
                        SpanStyle(
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    ) {
                        append(word.word)
                    }
                }
            }

            if (index < words.size - 1) append(" ")
        }
    }

    Text(
        text = annotatedText,
        lineHeight = 26.sp
    )
}