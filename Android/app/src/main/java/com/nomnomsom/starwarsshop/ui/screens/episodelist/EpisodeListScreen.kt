package com.nomnomsom.starwarsshop.ui.screens.episodelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.starwarsshop.data.model.DownloadState
import com.nomnomsom.starwarsshop.data.model.PodcastDay
import com.nomnomsom.starwarsshop.media.PlaybackState
import com.nomnomsom.starwarsshop.ui.theme.ErrorRed
import com.nomnomsom.starwarsshop.ui.theme.Gold
import com.nomnomsom.starwarsshop.ui.theme.LiveRed
import com.nomnomsom.starwarsshop.ui.theme.SuccessGreen
import com.nomnomsom.starwarsshop.ui.theme.TextMuted
import com.nomnomsom.starwarsshop.ui.theme.TextSecondary
import com.nomnomsom.starwarsshop.util.formatDuration
import com.nomnomsom.starwarsshop.util.formatShortDuration
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        EpisodeListHeader()

        // Now playing mini bar
        if (playbackState.currentDayDate != null && playbackState.isReady) {
            val currentDay = uiState.days.find { it.date == playbackState.currentDayDate }
            if (currentDay != null) {
                MiniNowPlayingBar(
                    day = currentDay,
                    playbackState = playbackState,
                    onTap = { onEpisodeClick(currentDay) },
                    onTogglePlay = { viewModel.togglePlayPause() }
                )
            }
        }

        // Episode list with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp
                )
            ) {
                items(uiState.days, key = { it.date }) { day ->
                    EpisodeDayCard(
                        day = day,
                        isCurrentlyPlaying = playbackState.currentDayDate == day.date && playbackState.isPlaying,
                        onClick = {
                            if (day.downloadState == DownloadState.DOWNLOADED.value) {
                                onEpisodeClick(day)
                            }
                        },
                        onDownload = { viewModel.downloadDay(day.date) },
                        onReset = { viewModel.resetProgress(day.date) }
                    )
                }
            }
        }
    }
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
        // Logo mark
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
        Column {
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

@Composable
private fun MiniNowPlayingBar(
    day: PodcastDay,
    playbackState: PlaybackState,
    onTap: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Gold.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("A&G", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    maxLines = 1
                )
                Text(
                    text = if (playbackState.isPlaying) "Playing now" else "Paused",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
                )
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Gold
                )
            }
        }
    }
}

@Composable
private fun EpisodeDayCard(
    day: PodcastDay,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onReset: () -> Unit
) {
    val downloadState = DownloadState.fromValue(day.downloadState)
    val isDownloaded = downloadState == DownloadState.DOWNLOADED
    val isNotDownloaded = downloadState == DownloadState.NONE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isNotDownloaded) 0.6f else 1f)
            .clickable(enabled = isDownloaded, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                // Date badge
                DateBadge(day.date)
                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isDownloaded && day.isListened) {
                            Badge(text = "✓", color = SuccessGreen)
                        } else if (isDownloaded) {
                            Badge(text = "✓", color = Gold)
                        }
                    }

                    // Duration & segment count
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

                    // Summary
                    Text(
                        text = day.summary,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // State-dependent bottom section
            when (downloadState) {
                DownloadState.DOWNLOADED -> {
                    // Progress bar
                    val progress = if (day.totalDurationMs > 0) {
                        (day.listenedPositionMs.toFloat() / day.totalDurationMs).coerceIn(0f, 1f)
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
                                day.isListened -> "Completed"
                                day.listenedPositionMs > 0 ->
                                    "${day.listenedPositionMs.formatDuration()} / ${day.totalDurationMs.formatShortDuration()}"
                                else -> "Ready to play"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (day.isListened) {
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
                    }
                }

                DownloadState.DOWNLOADING -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Gold.copy(alpha = 0.6f),
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloading segments…",
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