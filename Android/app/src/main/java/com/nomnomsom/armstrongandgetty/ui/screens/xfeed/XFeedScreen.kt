package com.nomnomsom.armstrongandgetty.ui.screens.xfeed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.data.model.XFeedMedia
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextPrimary
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val XBlue = Color(0xFF1D9BF0)
private val FeedLine = Color(0xFF2F3336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XFeedScreen(
    viewModel: XFeedViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val newPostsWhileViewing by viewModel.newPostsWhileViewing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        XFeedHeader()

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = XBlue)
                }
            }

            uiState.error != null && uiState.items.isEmpty() -> {
                RefreshableEmptyFeedMessage(
                    title = "Couldn't load feed",
                    subtitle = "Pull down to retry",
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                )
            }

            uiState.items.isEmpty() -> {
                RefreshableEmptyFeedMessage(
                    title = "No posts yet",
                    subtitle = "Pull down to refresh",
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh
                )
            }

            else -> {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                LaunchedEffect(listState, uiState.canLoadMore, uiState.isLoadingMore) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                        layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 4
                    }
                        .distinctUntilChanged()
                        .collect { shouldLoad ->
                            if (shouldLoad) viewModel.loadNextPage()
                        }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (uiState.error != null) {
                                item(key = "feed-error") {
                                    FeedErrorStrip(uiState.error.orEmpty())
                                }
                            }

                            items(
                                items = uiState.items,
                                key = { it.tweetId }
                            ) { item ->
                                XPostRow(item = item)
                            }

                            if (uiState.isLoadingMore) {
                                item(key = "loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = XBlue,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                item(key = "feed-bottom-space") {
                                    Spacer(modifier = Modifier.height(56.dp))
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = newPostsWhileViewing > 0,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        NewPostsBanner(
                            count = newPostsWhileViewing,
                            onClick = {
                                viewModel.dismissNewPostsBanner()
                                viewModel.reloadLatest()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableEmptyFeedMessage(
    title: String,
    subtitle: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        EmptyFeedMessage(title = title, subtitle = subtitle)
    }
}

@Composable
private fun EmptyFeedMessage(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun FeedErrorStrip(message: String) {
    Text(
        text = message,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3A1F24))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NewPostsBanner(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(XBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count new post${if (count != 1) "s" else ""}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun XFeedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "A&G List",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = TextPrimary
            )
            Text(
                text = "LATEST POSTS",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
    HorizontalDivider(color = FeedLine, thickness = 1.dp)
}

@Composable
private fun XPostRow(item: XFeedItem) {
    val context = LocalContext.current
    val relativeTime = remember(item.timestampMs) { formatRelativeTime(item.timestampMs) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.tweetUrl)))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                name = item.author.name.ifBlank { item.author.username },
                url = item.author.profileImageUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.author.name.ifBlank { item.author.username.ifBlank { "X user" } },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.author.verified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = XBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@${item.author.username} · $relativeTime",
                        color = TextMuted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = item.text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                if (item.media.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MediaGrid(media = item.media)
                } else {
                    val url = item.urls.firstOrNull { !it.displayUrl.isNullOrBlank() || !it.expandedUrl.isNullOrBlank() }
                    if (url != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinkPill(
                            display = url.displayUrl ?: url.expandedUrl.orEmpty(),
                            target = url.expandedUrl
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MetricRow(item = item)
            }
        }
    }
    HorizontalDivider(color = FeedLine, thickness = 1.dp)
}

@Composable
private fun Avatar(
    name: String,
    url: String?
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF202327)),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "X",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MediaGrid(media: List<XFeedMedia>) {
    val items = media
        .mapNotNull { item ->
            val url = item.previewImageUrl ?: item.url
            if (url == null) null else item to url
        }
        .take(4)

    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FeedLine)
    ) {
        when (items.size) {
            1 -> MediaTile(
                media = items[0].first,
                imageUrl = items[0].second,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(mediaAspectRatio(items[0].first))
            )

            2 -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                items.forEach { (mediaItem, imageUrl) ->
                    MediaTile(
                        media = mediaItem,
                        imageUrl = imageUrl,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            rowItems.forEach { (mediaItem, imageUrl) ->
                                MediaTile(
                                    media = mediaItem,
                                    imageUrl = imageUrl,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.35f)
                                )
                            }

                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    media: XFeedMedia,
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color(0xFF16181C))) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (media.type == "video" || media.type == "animated_gif") {
            Text(
                text = if (media.type == "animated_gif") "GIF" else "VIDEO",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LinkPill(
    display: String,
    target: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16181C))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display,
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!target.isNullOrBlank() && target != display) {
                Text(
                    text = target,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetricRow(item: XFeedItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Metric(
            icon = Icons.Outlined.ChatBubbleOutline,
            value = item.metrics.replies
        )
        Metric(
            icon = Icons.Outlined.Repeat,
            value = item.metrics.reposts + item.metrics.quotes
        )
        Metric(
            icon = Icons.Outlined.FavoriteBorder,
            value = item.metrics.likes
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun Metric(
    icon: ImageVector,
    value: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(64.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (value > 0) formatCompactNumber(value) else "",
            color = TextMuted,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

private fun mediaAspectRatio(media: XFeedMedia): Float {
    val width = media.width ?: return 16f / 9f
    val height = media.height ?: return 16f / 9f
    return if (width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.8f, 1.9f)
    } else {
        16f / 9f
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val age = Duration.between(Instant.ofEpochMilli(timestampMs), Instant.now()).toMillis()
    return when {
        age < 60_000 -> "now"
        age < 60 * 60_000 -> "${age / 60_000}m"
        age < 24 * 60 * 60_000 -> "${age / (60 * 60_000)}h"
        age < 7 * 24 * 60 * 60_000 -> "${age / (24 * 60 * 60_000)}d"
        else -> DateTimeFormatter
            .ofPattern("MMM d", Locale.US)
            .format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
    }
}

private fun formatCompactNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> "${formatOneDecimal(value / 1_000_000f)}M"
        value >= 1_000 -> "${formatOneDecimal(value / 1_000f)}K"
        else -> value.toString()
    }
}

private fun formatOneDecimal(value: Float): String {
    return String.format(Locale.US, "%.1f", value).removeSuffix(".0")
}
