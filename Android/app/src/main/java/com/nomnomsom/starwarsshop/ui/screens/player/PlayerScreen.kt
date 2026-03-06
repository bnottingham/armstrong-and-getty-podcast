package com.nomnomsom.starwarsshop.ui.screens.player

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.starwarsshop.data.model.PodcastDay
import com.nomnomsom.starwarsshop.data.model.Segment
import com.nomnomsom.starwarsshop.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.starwarsshop.ui.theme.DarkBg
import com.nomnomsom.starwarsshop.ui.theme.Gold
import com.nomnomsom.starwarsshop.ui.theme.LiveRed
import com.nomnomsom.starwarsshop.ui.theme.TextMuted
import com.nomnomsom.starwarsshop.ui.theme.TextSecondary
import com.nomnomsom.starwarsshop.util.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    day: PodcastDay,
    viewModel: EpisodeListViewModel,
    onBack: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val transcriptState by viewModel.transcriptState.collectAsState()
    val segments = remember(day.segmentsJson) { viewModel.getSegmentsForDay(day) }
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0=Segments, 1=Transcript

    // Load playlist if not already loaded for this day (does NOT auto-play)
    LaunchedEffect(day.date) {
        viewModel.loadDay(day)
        viewModel.observeTranscriptsForDay(day.date, segments.size)
    }

    // Periodically save listen progress
    LaunchedEffect(playbackState.isPlaying) {
        while (playbackState.isPlaying) {
            delay(5_000)
            viewModel.saveListenProgress()
        }
    }

    // Save progress on leaving
    DisposableEffect(Unit) {
        onDispose { viewModel.saveListenProgress() }
    }

    // Check for new segments if day is incomplete
    LaunchedEffect(day.isComplete) {
        if (!day.isComplete) {
            while (true) {
                delay(120_000) // Check every 2 min
                viewModel.checkForNewSegments(day.date)
            }
        }
    }

    // Current segment index comes directly from the player (which media item is active)
    val currentSegmentIndex = playbackState.currentSegmentIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Gold
                    )
                }
            },
            actions = { Spacer(modifier = Modifier.width(48.dp)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Album art
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

            // Live indicator
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

            // Title & current segment
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

            // Seek bar
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SeekBar(
                    currentMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs.coerceAtLeast(day.totalDurationMs),
                    onSeek = { viewModel.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }

            // Transport controls
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

            // Tab switcher: Segments | Transcript
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PlayerTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (selectedTab) {
                0 -> {
                    // Segments tab
                    itemsIndexed(segments, key = { index, seg -> "${day.date}_$index" }) { index, segment ->
                        var expanded by rememberSaveable(key = "${day.date}_seg_$index") {
                            mutableStateOf(false)
                        }

                        SegmentRow(
                            segment = segment,
                            isActive = index == currentSegmentIndex,
                            expanded = expanded,
                            onToggleExpand = { expanded = !expanded },
                            onPlay = {
                                viewModel.seekToSegment(index)
                                if (!playbackState.isPlaying) {
                                    viewModel.togglePlayPause()
                                }
                            }
                        )
                    }
                }
                1 -> {
                    // Transcript tab
                    item {
                        TranscriptView(
                            transcripts = transcriptState.transcripts,
                            segments = segments,
                            currentSegmentIndex = currentSegmentIndex,
                            positionInSegmentMs = playbackState.positionInSegmentMs,
                            isTranscribing = transcriptState.isTranscribing,
                            modelStatus = transcriptState.modelStatus,
                            parseWords = { viewModel.parseTimedWords(it) },
                            onTranscribeSegment = { segmentIndex ->
                                viewModel.transcribeSegment(day.date, segmentIndex)
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PlayerTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Segments", "Transcript")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Gold.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) Gold else TextSecondary,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
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
            // -30s
            IconButton(
                onClick = { onSkip(-30_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Replay30, "Rewind 30s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            // -10s
            IconButton(
                onClick = { onSkip(-10_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Replay10, "Rewind 10s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            // Play/Pause
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

            // +10s
            IconButton(
                onClick = { onSkip(10_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Forward10, "Forward 10s", tint = Gold, modifier = Modifier.size(24.dp))
            }

            // +30s
            IconButton(
                onClick = { onSkip(30_000) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Forward30, "Forward 30s", tint = Gold, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed button
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
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit
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
            // Active indicator bar
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(Gold, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(9.dp))
            }

            // Hour label
            Text(
                text = if (segment.hour == "OMT") "OMT" else "Hr ${segment.hour}",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                modifier = Modifier.width(if (isActive) 28.dp else 37.dp)
            )

            // Title — wraps to multiple lines
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
                // Duration below title
                val displayDurationMin = if (segment.actualDurationMs > 0) {
                    segment.actualDurationMs / 60_000
                } else {
                    segment.durationMs / 60_000
                }
                Text(
                    text = "${displayDurationMin} min",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Play button — always visible
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

            Spacer(modifier = Modifier.width(4.dp))

            // Expand/collapse button
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

        // Expanded content: description
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