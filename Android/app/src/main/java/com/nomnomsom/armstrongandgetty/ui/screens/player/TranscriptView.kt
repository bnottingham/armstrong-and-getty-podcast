package com.nomnomsom.armstrongandgetty.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.model.TranscriptState
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextPrimary
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary

/**
 * Displays the transcript as plain text, organized by segment.
 * The currently playing segment is visually highlighted with an accent bar.
 *
 * Word-level highlighting was removed because rebuilding an AnnotatedString
 * for thousands of words on every position tick caused severe UI jank
 * during playback.
 *
 * @param transcripts List of transcript entities, one per segment
 * @param segments List of segment metadata
 * @param currentSegmentIndex Currently playing segment
 * @param isFetching Whether transcripts are being fetched from Firebase
 * @param onRetryFetchSegment Callback to retry fetching a specific segment's transcript
 */
@Composable
fun TranscriptView(
    transcripts: List<TranscriptEntity>,
    segments: List<Segment>,
    currentSegmentIndex: Int,
    isFetching: Boolean,
    onRetryFetchSegment: (Int) -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Show fetching indicator at top if actively loading
        if (isFetching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Checking for transcriptions…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        segmentTranscripts.forEachIndexed { index, data ->
            TranscriptSegmentBlock(
                data = data,
                isCurrentSegment = index == currentSegmentIndex,
                onRetryFetch = { onRetryFetchSegment(data.segmentIndex) }
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
    onRetryFetch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Segment header with active indicator
        val headerColor = if (isCurrentSegment) Gold else TextPrimary
        val headerPrefix = if (isCurrentSegment) "▶ " else ""
        Text(
            text = headerPrefix + if (data.segment.hour == "OMT") "One More Thing"
            else "Hour ${data.segment.hour}",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = headerColor
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Active segment accent bar
        if (isCurrentSegment) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Gold.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (data.state) {
            TranscriptState.DONE -> {
                // Plain text transcript — no word-level highlighting
                val displayText = data.transcript?.fullText ?: ""
                val textColor = if (isCurrentSegment) TextPrimary else TextSecondary

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = textColor
                    )
                )
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
                        CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            TranscriptState.ERROR, TranscriptState.NONE -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (data.state == TranscriptState.ERROR)
                                "Transcription not available"
                            else
                                "No transcription yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RetryFetchButton(onClick = onRetryFetch)
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryFetchButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold.copy(alpha = 0.12f),
            contentColor = Gold
        )
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Retry", fontWeight = FontWeight.SemiBold)
    }
}