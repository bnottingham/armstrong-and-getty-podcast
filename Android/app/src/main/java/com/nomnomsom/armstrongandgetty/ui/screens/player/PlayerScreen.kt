package com.nomnomsom.armstrongandgetty.ui.screens.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.armstrongandgetty.ui.theme.DarkBg
import com.nomnomsom.armstrongandgetty.ui.theme.ErrorRed
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.LiveRed
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import com.nomnomsom.armstrongandgetty.util.formatDuration
import kotlinx.coroutines.delay

private enum class SegmentDownloadStatus { DOWNLOADED, DOWNLOADING, MISSING }

@Composable
fun PlayerScreen(
    day: PodcastDay,
    viewModel: EpisodeListViewModel
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val downloadProgressMap by viewModel.downloadProgress.collectAsState()
    val segments = remember(day.segmentsJson) { viewModel.getSegmentsForDay(day) }

    val dayProgress = downloadProgressMap[day.date]
    val missingIndices = remember(day.date, day.segmentCount, dayProgress) {
        viewModel.missingSegmentIndices(day)
    }
    val inFlightIndices = dayProgress?.segmentsInProgress ?: emptySet()

    val isDayDownloading = day.state == DownloadState.DOWNLOADING
    val anySegmentOnDisk = segments.isNotEmpty() && missingIndices.size < segments.size

    // Load the playlist without auto-playing, so the user can resume manually. preparePlaylist
    // now allows partial days, so this is safe to call regardless of downloadState. Re-keyed on
    // missingIndices.size so a successful per-segment retry pulls the new file into the playlist.
    LaunchedEffect(day.date, missingIndices.size) {
        viewModel.loadDay(day)
    }

    LaunchedEffect(playbackState.isPlaying) {
        if (playbackState.isPlaying) {
            while (true) {
                delay(5_000)
                viewModel.saveListenProgress()
            }
        } else {
            viewModel.saveListenProgress()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveListenProgress() }
    }

    LaunchedEffect(day.isComplete) {
        if (!day.isComplete) {
            while (true) {
                delay(120_000)
                viewModel.checkForNewSegments(day.date)
            }
        }
    }

    val currentSegmentIndex = playbackState.currentSegmentIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "A&G",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-2).sp,
                            color = Gold
                        )
                        Text(
                            text = day.date,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            if (!day.isComplete) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "● In progress — new segments auto-append",
                        color = LiveRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
                if (segments.isNotEmpty()) {
                    val seg = segments[currentSegmentIndex]
                    Text(
                        text = "Hr ${seg.hour}: ${seg.title}",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 30.dp)
                            .padding(top = 4.dp)
                    )
                }
            }

            // Day-level download controls. Show whenever the day isn't fully on disk so users
            // landing on the details screen for a NONE/ERROR day have an obvious way forward.
            if (missingIndices.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    DayDownloadActions(
                        isDownloading = isDayDownloading,
                        anySegmentOnDisk = anySegmentOnDisk,
                        onDownloadAll = { viewModel.downloadDay(day.date) },
                        onCancelAll = { viewModel.cancelDownload(day.date) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                val seekDurationMs = if (playbackState.currentDayDate == day.date && playbackState.durationMs > 0) {
                    playbackState.durationMs
                } else {
                    day.totalDurationMs
                }
                SeekBar(
                    currentMs = playbackState.currentPositionMs,
                    durationMs = seekDurationMs,
                    onSeek = { viewModel.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                TransportControls(
                    isPlaying = playbackState.isPlaying,
                    playbackSpeed = playbackState.playbackSpeed,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onSkip = { viewModel.seekRelative(it) },
                    onCycleSpeed = { viewModel.cycleSpeed() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "SEGMENTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(segments, key = { index, seg -> "${day.date}_$index" }) { index, segment ->
                var expanded by rememberSaveable {
                    mutableStateOf(false)
                }

                val status = when {
                    index in inFlightIndices -> SegmentDownloadStatus.DOWNLOADING
                    index in missingIndices -> SegmentDownloadStatus.MISSING
                    else -> SegmentDownloadStatus.DOWNLOADED
                }

                SegmentRow(
                    segment = segment,
                    isActive = index == currentSegmentIndex,
                    expanded = expanded,
                    status = status,
                    onToggleExpand = { expanded = !expanded },
                    onPlay = {
                        viewModel.seekToSegment(index)
                        if (!playbackState.isPlaying) {
                            viewModel.togglePlayPause()
                        }
                    },
                    onRetry = { viewModel.retrySegment(day.date, index) },
                    onCancel = { viewModel.cancelSegment(day.date, index) }
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
}

@Composable
private fun SeekBar(
    currentMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    val displayValue = if (isSeeking) seekValue
    else if (durationMs > 0) currentMs.toFloat() / durationMs else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayValue.coerceIn(0f, 1f),
            onValueChange = {
                isSeeking = true
                seekValue = it
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek((seekValue * durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = Gold,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = if (isSeeking) (seekValue * durationMs).toLong() else currentMs
            Text(
                text = displayMs.formatDuration(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            Text(
                text = "-${(durationMs - displayMs).coerceAtLeast(0).formatDuration()}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    playbackSpeed: Float,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onCycleSpeed: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 30.dp)
        ) {
            IconButton(
                onClick = { onSkip(-10_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Replay10, "Rewind 10s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = { onSkip(-30_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Replay30, "Rewind 30s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Gold)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = DarkBg,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { onSkip(30_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Forward30, "Forward 30s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = { onSkip(10_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Forward10, "Forward 10s", tint = Gold, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onCycleSpeed,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "${playbackSpeed}x Speed",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: Segment,
    isActive: Boolean,
    expanded: Boolean,
    status: SegmentDownloadStatus,
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(if (isActive) Gold.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(Gold, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(9.dp))
            }

            Text(
                text = if (segment.hour == "OMT") "OMT" else "Hr ${segment.hour}",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                modifier = Modifier.width(if (isActive) 28.dp else 37.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = segment.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                )
                val subtitle = when (status) {
                    SegmentDownloadStatus.DOWNLOADING -> "Downloading… tap to cancel"
                    SegmentDownloadStatus.MISSING -> "Not downloaded — tap retry"
                    SegmentDownloadStatus.DOWNLOADED -> {
                        val mins = (segment.actualDurationMs.takeIf { it > 0 } ?: segment.durationMs) / 60_000
                        "$mins min"
                    }
                }
                val subtitleColor = when (status) {
                    SegmentDownloadStatus.MISSING -> ErrorRed
                    SegmentDownloadStatus.DOWNLOADING -> Gold
                    SegmentDownloadStatus.DOWNLOADED -> TextMuted
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            SegmentActionIcon(
                status = status,
                onPlay = onPlay,
                onRetry = onRetry,
                onCancel = onCancel
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (expanded && segment.description.isNotBlank()) {
            Text(
                text = segment.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 16.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun SegmentActionIcon(
    status: SegmentDownloadStatus,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    when (status) {
        SegmentDownloadStatus.DOWNLOADED -> {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play segment",
                    tint = Gold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        SegmentDownloadStatus.DOWNLOADING -> {
            // Tappable cancel button. The faint spinner behind it shows liveness so a stuck
            // segment is still visually distinguishable from one that's just queued.
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Gold.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel segment download",
                        tint = Gold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        SegmentDownloadStatus.MISSING -> {
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ErrorRed.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry download",
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DayDownloadActions(
    isDownloading: Boolean,
    anySegmentOnDisk: Boolean,
    onDownloadAll: () -> Unit,
    onCancelAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (isDownloading) {
            OutlinedButton(
                onClick = onCancelAll,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel download", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onDownloadAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold.copy(alpha = 0.12f),
                    contentColor = Gold
                )
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (anySegmentOnDisk) "Download missing segments" else "Download all segments",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
