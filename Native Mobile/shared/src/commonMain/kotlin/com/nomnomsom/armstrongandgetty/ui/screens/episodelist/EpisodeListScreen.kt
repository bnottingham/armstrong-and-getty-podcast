package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nomnomsom.armstrongandgetty.ui.icons.AppIcons
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.displayLabel
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.media.PlaybackState
import com.nomnomsom.armstrongandgetty.ui.theme.ErrorRed
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.GoldDark
import com.nomnomsom.armstrongandgetty.ui.theme.LiveRed
import com.nomnomsom.armstrongandgetty.ui.theme.SuccessGreen
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import com.nomnomsom.armstrongandgetty.util.formatDuration
import com.nomnomsom.armstrongandgetty.util.formatShortDuration
import com.nomnomsom.armstrongandgetty.util.parseDayKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    viewModel: EpisodeListViewModel,
    onEpisodeClick: (PodcastDay) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val downloadProgressMap by viewModel.downloadProgress.collectAsState()

    // Tile priority: active playback → last-listened downloaded day → placeholder.
    val activeDay = if (playbackState.currentDayDate != null && playbackState.isReady) {
        uiState.days.find { it.date == playbackState.currentDayDate }
    } else null

    val lastListenedDay = if (activeDay == null) {
        uiState.days
            .filter { it.listenedPositionMs > 0 && it.state == DownloadState.DOWNLOADED }
            .maxByOrNull { it.lastUpdated }
    } else null

    val nowPlayingDay = activeDay ?: lastListenedDay
    val isActivePlayback = activeDay != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                item(key = "now_playing_header") {
                    SectionHeader(title = "NOW PLAYING")
                }
                item(key = "now_playing_card") {
                    if (nowPlayingDay != null) {
                        val segments = remember(nowPlayingDay.segmentsJson) {
                            viewModel.getSegmentsForDay(nowPlayingDay)
                        }
                        val currentSegmentLabel = if (isActivePlayback && segments.isNotEmpty()) {
                            segments.getOrNull(playbackState.currentSegmentIndex)?.displayLabel
                        } else if (segments.isNotEmpty()) {
                            // No player yet — derive the segment from the saved virtual position.
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
                            onSkipBack30 = { viewModel.seekRelative(-30_000) },
                            onSkipBack10 = { viewModel.seekRelative(-10_000) },
                            onSkipForward10 = { viewModel.seekRelative(10_000) },
                            onSkipForward30 = { viewModel.seekRelative(30_000) }
                        )
                    } else {
                        NowPlayingPlaceholder()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item(key = "episodes_header") {
                    SectionHeader(title = "EPISODES")
                }

                items(uiState.days, key = { it.date }) { day ->
                    val isThisDayPlaying = playbackState.currentDayDate == day.date && playbackState.isReady

                    EpisodeDayCard(
                        day = day,
                        isCurrentlyPlaying = isThisDayPlaying,
                        isPlaying = isThisDayPlaying && playbackState.isPlaying,
                        livePositionMs = if (isThisDayPlaying) playbackState.currentPositionMs else null,
                        liveDurationMs = if (isThisDayPlaying) playbackState.durationMs else null,
                        downloadProgress = downloadProgressMap[day.date],
                        // Always navigate to the player/details — even non-downloaded episodes are
                        // useful there, since the segment list is the recovery UI for partials.
                        onClick = { onEpisodeClick(day) },
                        onDownload = { viewModel.downloadDay(day.date) },
                        onCancelDownload = { viewModel.cancelDownload(day.date) },
                        onReset = { viewModel.resetProgress(day.date) },
                        onDelete = { viewModel.deleteDay(day.date) },
                        onTogglePlay = {
                            if (isThisDayPlaying) viewModel.togglePlayPause()
                            else viewModel.playDay(day)
                        }
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
private fun NowPlayingCard(
    day: PodcastDay,
    playbackState: PlaybackState,
    isActivePlayback: Boolean,
    currentSegmentLabel: String?,
    onTap: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack30: () -> Unit,
    onSkipBack10: () -> Unit,
    onSkipForward10: () -> Unit,
    onSkipForward30: () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(Gold, GoldDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "A&G",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.background
                    )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActivePlayback) {
                    SkipButton(
                        icon = AppIcons.Replay10,
                        contentDescription = "Back 10s",
                        onClick = onSkipBack10
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SkipButton(
                        icon = AppIcons.Replay30,
                        contentDescription = "Back 30s",
                        onClick = onSkipBack30
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Gold)
                ) {
                    Icon(
                        imageVector = if (isActivePlayback && playbackState.isPlaying)
                            AppIcons.Pause
                        else
                            AppIcons.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isActivePlayback) {
                    Spacer(modifier = Modifier.width(14.dp))
                    SkipButton(
                        icon = AppIcons.Forward30,
                        contentDescription = "Forward 30s",
                        onClick = onSkipForward30
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SkipButton(
                        icon = AppIcons.Forward10,
                        contentDescription = "Forward 10s",
                        onClick = onSkipForward10
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Gold.copy(alpha = 0.12f))
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Gold,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NowPlayingPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppIcons.GraphicEq,
                    contentDescription = null,
                    tint = Gold.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Nothing playing",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
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
}

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
    onCancelDownload: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val downloadState = day.state
    val isDownloaded = downloadState == DownloadState.DOWNLOADED
    val isNotDownloaded = downloadState == DownloadState.NONE

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
            .clickable(onClick = onClick),
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
                        if (isCurrentlyPlaying) {
                            Icon(
                                AppIcons.GraphicEq,
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Gold.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause episode" else "Play episode",
                                    tint = Gold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = when {
                                    day.isListened && !isCurrentlyPlaying -> "Completed"
                                    displayPositionMs > 0 ->
                                        "${displayPositionMs.formatDuration()} / ${displayDurationMs.formatDuration()}"
                                    else -> "Ready to play"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (day.isListened && !isCurrentlyPlaying) {
                                OutlinedButton(
                                    onClick = onReset,
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(AppIcons.RestartAlt, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restart", fontSize = 11.sp)
                                }
                            }

                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    AppIcons.Delete,
                                    contentDescription = "Delete episode",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                DownloadState.DOWNLOADING -> {
                    val total = downloadProgress?.totalSegments ?: day.segmentCount
                    val completed = downloadProgress?.segmentsCompleted ?: 0
                    val currentIndex = downloadProgress?.segmentsInProgress?.firstOrNull() ?: completed
                    val failed = downloadProgress?.segmentsFailed?.size ?: 0

                    val overallProgress = when {
                        downloadProgress != null && downloadProgress.totalBytes > 0 ->
                            downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes
                        total > 0 -> completed.toFloat() / total
                        else -> 0f
                    }
                    val overallPercent = (overallProgress * 100).toInt().coerceIn(0, 100)

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = buildString {
                                append("Downloading segment ${currentIndex + 1} of $total — $overallPercent%")
                                if (failed > 0) append(" · $failed failed")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // TextButton (no outline, tight content padding) avoids the OutlinedButton
                        // min-interactive-size enforcement that was pushing the visible bounds past
                        // the card edge.
                        TextButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                        ) {
                            Icon(AppIcons.Stop, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                DownloadState.ERROR -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            AppIcons.ErrorOutline,
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
                            Icon(AppIcons.Refresh, null, modifier = Modifier.size(14.dp))
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
                        Icon(AppIcons.Download, null, modifier = Modifier.size(18.dp))
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
    val parsed = parseDayKey(dateString)

    val dayOfWeek = parsed?.dayOfWeek?.name?.take(3) ?: ""
    val dayNum = parsed?.day?.toString() ?: ""
    val month = parsed?.month?.name?.take(3) ?: ""

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
