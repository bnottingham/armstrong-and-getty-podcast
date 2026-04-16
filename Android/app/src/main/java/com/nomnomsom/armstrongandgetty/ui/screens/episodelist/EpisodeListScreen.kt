package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.displayLabel
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.ui.theme.ErrorRed
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.LiveRed
import com.nomnomsom.armstrongandgetty.ui.theme.SuccessGreen
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import com.nomnomsom.armstrongandgetty.util.formatDuration
import com.nomnomsom.armstrongandgetty.util.formatShortDuration
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    viewModel: EpisodeListViewModel,
    onEpisodeClick: (PodcastDay) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val downloadProgressMap by viewModel.downloadProgress.collectAsState()

    // Determine what to show in the Now Playing tile:
    // 1. Currently active playback (player is ready with a day loaded)
    // 2. Last listened episode (has progress but player isn't active)
    // 3. Placeholder (nothing played yet)
    val activeDay = if (playbackState.currentDayDate != null && playbackState.isReady) {
        uiState.days.find { it.date == playbackState.currentDayDate }
    } else null

    val lastListenedDay = if (activeDay == null) {
        uiState.days
            .filter { it.listenedPositionMs > 0 && it.downloadState == DownloadState.DOWNLOADED.value }
            .maxByOrNull { it.lastUpdated }
    } else null

    // The day to show in the tile (active playback takes priority)
    val nowPlayingDay = activeDay ?: lastListenedDay
    val isActivePlayback = activeDay != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        EpisodeListHeader()

        // Pull to refresh wraps everything
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp
                )
            ) {
                // ── Now Playing section (always visible) ──
                item(key = "now_playing_header") {
                    SectionHeader(title = "NOW PLAYING")
                }
                item(key = "now_playing_card") {
                    if (nowPlayingDay != null) {
                        // Compute current segment label
                        val segments = remember(nowPlayingDay.segmentsJson) {
                            viewModel.getSegmentsForDay(nowPlayingDay)
                        }
                        val currentSegmentLabel = if (isActivePlayback && segments.isNotEmpty()) {
                            segments.getOrNull(playbackState.currentSegmentIndex)?.displayLabel
                        } else if (segments.isNotEmpty()) {
                            // Cold start — figure out which segment from saved position
                            var remaining = nowPlayingDay.listenedPositionMs
                            var segIdx = 0
                            for (i in segments.indices) {
                                val dur = segments[i].effectiveDurationMs
                                if (remaining < dur) { segIdx = i; break }
                                remaining -= dur
                                segIdx = i
                            }
                            segments[segIdx].displayLabel
                        } else null

                        NowPlayingCard(
                            day = nowPlayingDay,
                            playbackState = playbackState,
                            isActivePlayback = isActivePlayback,
                            currentSegmentLabel = currentSegmentLabel,
                            onTap = { onEpisodeClick(nowPlayingDay) },
                            onTogglePlay = {
                                if (isActivePlayback) viewModel.togglePlayPause()
                                else viewModel.playDay(nowPlayingDay)
                            },
                            onSkipBack = { viewModel.seekRelative(-30_000) },
                            onSkipForward = { viewModel.seekRelative(30_000) }
                        )
                    } else {
                        NowPlayingPlaceholder()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Episodes section ──
                item(key = "episodes_header") {
                    SectionHeader(title = "EPISODES")
                }

                items(uiState.days, key = { it.date }) { day ->
                    val isThisDayPlaying = playbackState.currentDayDate == day.date && playbackState.isReady

                    EpisodeDayCard(
                        day = day,
                        isCurrentlyPlaying = isThisDayPlaying,
                        isPlaying = isThisDayPlaying && playbackState.isPlaying,
                        // Show live position from player if this day is active
                        livePositionMs = if (isThisDayPlaying) playbackState.currentPositionMs else null,
                        liveDurationMs = if (isThisDayPlaying) playbackState.durationMs else null,
                        downloadProgress = downloadProgressMap[day.date],
                        onClick = {
                            if (day.downloadState == DownloadState.DOWNLOADED.value) {
                                onEpisodeClick(day)
                            }
                        },
                        onDownload = { viewModel.downloadDay(day.date) },
                        onReset = { viewModel.resetProgress(day.date) },
                        onDelete = { viewModel.deleteDay(day.date) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = TextMuted
        ),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun EpisodeListHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A&G",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = Gold
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Armstrong & Getty",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "PODCAST · ON DEMAND",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ── Now Playing Card (enhanced) ──

@Composable
private fun NowPlayingCard(
    day: PodcastDay,
    playbackState: PlaybackState,
    isActivePlayback: Boolean,
    currentSegmentLabel: String?,
    onTap: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Gold.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A&G", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentSegmentLabel != null) {
                        Text(
                            text = currentSegmentLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = TextSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = when {
                            isActivePlayback && playbackState.isPlaying -> "● Playing"
                            isActivePlayback -> "● Paused"
                            else -> "● Last played — tap to resume"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = when {
                                isActivePlayback && playbackState.isPlaying -> Gold
                                isActivePlayback -> TextSecondary
                                else -> TextMuted
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar — use live position if active, saved position otherwise
            val positionMs = if (isActivePlayback) playbackState.currentPositionMs else day.listenedPositionMs
            val durationMs = if (isActivePlayback && playbackState.durationMs > 0) {
                playbackState.durationMs
            } else {
                day.totalDurationMs
            }
            val progress = if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Gold,
                trackColor = MaterialTheme.colorScheme.outline,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = positionMs.formatDuration(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Gold,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "-${(durationMs - positionMs).coerceAtLeast(0).formatDuration()}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transport controls — show skip buttons only for active playback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActivePlayback) {
                    // -30s
                    IconButton(
                        onClick = onSkipBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Gold.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Filled.Replay30,
                            contentDescription = "Back 30s",
                            tint = Gold,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Play/Pause
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Gold)
                ) {
                    Icon(
                        imageVector = if (isActivePlayback && playbackState.isPlaying)
                            Icons.Filled.Pause
                        else
                            Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isActivePlayback) {
                    Spacer(modifier = Modifier.width(16.dp))

                    // +30s
                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Gold.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Filled.Forward30,
                            contentDescription = "Forward 30s",
                            tint = Gold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Now Playing Placeholder ──

@Composable
private fun NowPlayingPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("A&G", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nothing playing",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick an episode below to start listening",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = TextMuted
                )
            )
        }
    }
}

// ── Episode Day Card ──

@Composable
private fun EpisodeDayCard(
    day: PodcastDay,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    livePositionMs: Long?,
    liveDurationMs: Long?,
    downloadProgress: DownloadProgress?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val downloadState = DownloadState.fromValue(day.downloadState)
    val isDownloaded = downloadState == DownloadState.DOWNLOADED
    val isNotDownloaded = downloadState == DownloadState.NONE

    // Confirmation dialog for delete
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Episode") },
            text = { Text("Delete ${day.title}? This will remove the downloaded audio files.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isNotDownloaded) 0.6f else 1f)
            .clickable(enabled = isDownloaded, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying)
                Gold.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                DateBadge(day.date)
                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Playing indicator
                        if (isCurrentlyPlaying) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = "Now playing",
                                tint = Gold,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (isDownloaded && day.isListened) {
                            Badge(text = "✓", color = SuccessGreen)
                        } else if (isDownloaded) {
                            Badge(text = "✓", color = Gold)
                        }
                    }

                    // Playing status line
                    if (isCurrentlyPlaying) {
                        Text(
                            text = if (isPlaying) "Playing now" else "Paused",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Gold,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${day.totalDurationMs.formatShortDuration()} · ${day.segmentCount} segments",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!day.isComplete) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "● LIVE",
                                color = LiveRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = day.summary,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (downloadState) {
                DownloadState.DOWNLOADED -> {
                    // Use live position from player if this day is active, otherwise saved position
                    val displayPositionMs = livePositionMs ?: day.listenedPositionMs
                    val displayDurationMs = liveDurationMs?.takeIf { it > 0 } ?: day.totalDurationMs

                    val progress = if (displayDurationMs > 0) {
                        (displayPositionMs.toFloat() / displayDurationMs).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Gold,
                        trackColor = MaterialTheme.colorScheme.outline,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                day.isListened && !isCurrentlyPlaying -> "Completed"
                                displayPositionMs > 0 ->
                                    "${displayPositionMs.formatDuration()} / ${displayDurationMs.formatDuration()}"
                                else -> "Ready to play"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (day.isListened && !isCurrentlyPlaying) {
                                OutlinedButton(
                                    onClick = onReset,
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restart", fontSize = 11.sp)
                                }
                            }

                            // Delete button — only show when NOT currently playing
                            if (!isCurrentlyPlaying) {
                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete episode",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                DownloadState.DOWNLOADING -> {
                    val segNum = (downloadProgress?.currentSegment ?: 0) + 1
                    val segTotal = downloadProgress?.totalSegments ?: day.segmentCount
                    val segFraction = if (downloadProgress != null && downloadProgress.segmentTotalBytes > 0) {
                        downloadProgress.segmentBytesDownloaded.toFloat() / downloadProgress.segmentTotalBytes
                    } else 0f
                    // Overall = (completed segments + current segment fraction) / total
                    val overallProgress = if (segTotal > 0) {
                        ((segNum - 1) + segFraction) / segTotal
                    } else 0f
                    val segPercent = (segFraction * 100).toInt()

                    LinearProgressIndicator(
                        progress = { overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Gold,
                        trackColor = MaterialTheme.colorScheme.outline,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloading segment $segNum of $segTotal — $segPercent%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                DownloadState.ERROR -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download failed",
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }

                DownloadState.NONE -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold.copy(alpha = 0.08f),
                            contentColor = Gold
                        )
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Episode", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateBadge(dateString: String) {
    val parsed = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateString)
    } catch (_: Exception) { null }

    val dayOfWeek = parsed?.let {
        SimpleDateFormat("EEE", Locale.US).format(it).uppercase()
    } ?: ""
    val dayNum = parsed?.let {
        SimpleDateFormat("d", Locale.US).format(it)
    } ?: ""
    val month = parsed?.let {
        SimpleDateFormat("MMM", Locale.US).format(it).uppercase()
    } ?: ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Gold.copy(alpha = 0.07f))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = dayOfWeek, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold, letterSpacing = 0.8.sp)
        Text(text = dayNum, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        Text(text = month, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
