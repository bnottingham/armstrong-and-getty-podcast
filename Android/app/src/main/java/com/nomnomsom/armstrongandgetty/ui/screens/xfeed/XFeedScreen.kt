package com.nomnomsom.armstrongandgetty.ui.screens.xfeed

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextPrimary
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary

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
            .background(MaterialTheme.colorScheme.background)
    ) {
        XFeedHeader()

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Gold)
                }
            }

            uiState.error != null && uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't load feed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pull down to retry",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            uiState.items.isNotEmpty() -> {
                // Track whether the WebView is scrolled to the top so we can
                // let PullToRefreshBox intercept the downward drag.
                var webViewAtTop by remember { mutableStateOf(true) }

                // Passthrough nested scroll connection — we don't consume
                // anything here, but wiring it enables the nested scroll chain
                // so PullToRefreshBox can intercept when the WebView allows it.
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset = Offset.Zero
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val refreshKey = remember(uiState.items) {
                            uiState.items.hashCode()
                        }

                        key(refreshKey) {
                            TweetFeedWebView(
                                items = uiState.items,
                                onScrollChanged = { scrollY ->
                                    webViewAtTop = scrollY == 0
                                },
                                enableParentScroll = webViewAtTop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(nestedScrollConnection)
                            )
                        }
                    }

                    // "New posts" banner — slides in from the top when new posts arrive while viewing
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
                                viewModel.refresh()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPostsBanner(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Gold)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count new post${if (count != 1) "s" else ""}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun XFeedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "𝕏",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "A&G Feed",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
            )
            Text(
                text = "POSTS FROM THE A&G COMMUNITY",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Single WebView that loads a page containing one iframe per tweet.
 * Each iframe points to platform.twitter.com/embed/Tweet.html?id=XXX
 * which is Twitter's hosted embed page — it renders fully on its own.
 *
 * A postMessage listener catches resize events from each iframe and
 * adjusts the iframe height so every tweet displays fully without cutoff.
 * The whole page scrolls naturally inside the WebView.
 *
 * @param onScrollChanged reports the WebView's vertical scroll offset so
 *        the parent can enable pull-to-refresh only when scrolled to the top.
 * @param enableParentScroll when true, the WebView disables its overscroll
 *        and allows the parent (PullToRefreshBox) to intercept the downward drag.
 */
@Composable
private fun TweetFeedWebView(
    items: List<XFeedItem>,
    onScrollChanged: (Int) -> Unit,
    enableParentScroll: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val html = remember(items) {
        buildEmbedPageHtml(items)
    }

    AndroidView(
        factory = { ctx ->
            object : WebView(ctx) {
                override fun overScrollBy(
                    deltaX: Int, deltaY: Int,
                    scrollX: Int, scrollY: Int,
                    scrollRangeX: Int, scrollRangeY: Int,
                    maxOverScrollX: Int, maxOverScrollY: Int,
                    isTouchEvent: Boolean
                ): Boolean {
                    onScrollChanged(scrollY + deltaY)
                    return super.overScrollBy(
                        deltaX, deltaY,
                        scrollX, scrollY,
                        scrollRangeX, scrollRangeY,
                        maxOverScrollX, maxOverScrollY,
                        isTouchEvent
                    )
                }

                override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                    super.onScrollChanged(l, t, oldl, oldt)
                    onScrollChanged(t)
                }

                override fun onOverScrolled(
                    scrollX: Int, scrollY: Int,
                    clampedX: Boolean, clampedY: Boolean
                ) {
                    super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
                    onScrollChanged(scrollY)
                }
            }.apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                setBackgroundColor(android.graphics.Color.parseColor("#0E0F13"))

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    setSupportZoom(false)
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false

                        // Allow Twitter/X embed resources to load inside the WebView
                        if (url.contains("platform.twitter.com")) return false
                        if (url.contains("syndication.twitter.com")) return false
                        if (url.contains("cdn.syndication.twimg.com")) return false
                        if (url.contains("pbs.twimg.com")) return false
                        if (url.contains("abs.twimg.com")) return false
                        if (url.contains("video.twimg.com")) return false
                        if (url.contains("ton.twimg.com")) return false

                        // Everything else opens in the device browser
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        ctx.startActivity(intent)
                        return true
                    }
                }

                webChromeClient = WebChromeClient()

                // Load as a web page from Twitter's domain so iframes aren't blocked
                loadDataWithBaseURL(
                    "https://platform.twitter.com",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            // When the WebView is at the top, disable its own overscroll so the
            // parent PullToRefreshBox can intercept the downward drag gesture.
            webView.overScrollMode = if (enableParentScroll) {
                WebView.OVER_SCROLL_NEVER
            } else {
                WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            // Tell the parent ViewGroup it's OK to intercept touch events
            // when the WebView is scrolled to the top (for pull-to-refresh).
            webView.parent?.requestDisallowInterceptTouchEvent(!enableParentScroll)
        },
        modifier = modifier
    )
}

/**
 * Builds an HTML page with one iframe per tweet.
 *
 * Each iframe loads Twitter's hosted embed page directly:
 *   https://platform.twitter.com/embed/Tweet.html?id=XXX&theme=dark&dnt=true
 *
 * This is a fully self-contained Twitter page that renders the tweet
 * with profile picture, text, images, videos, link cards, and action buttons.
 *
 * The JavaScript listens for postMessage events from the iframes
 * (Twitter's embed sends 'twttr.embed' resize messages) and adjusts
 * each iframe's height so the tweet displays fully.
 */
private fun buildEmbedPageHtml(items: List<XFeedItem>): String {
    val iframes = items.mapIndexed { index, item ->
        val embedUrl = "https://platform.twitter.com/embed/Tweet.html" +
                "?id=${item.tweetId}" +
                "&theme=dark" +
                "&dnt=true"

        """
        <div class="tweet-container" id="container-$index">
            <iframe
                id="frame-$index"
                src="$embedUrl"
                class="tweet-frame"
                frameborder="0"
                scrolling="no"
                allowtransparency="true"
            ></iframe>
        </div>
        """.trimIndent()
    }.joinToString("\n")

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                background-color: #0E0F13;
                padding: 4px;
                -webkit-overflow-scrolling: touch;
            }
            .tweet-container {
                margin-bottom: 8px;
                border-radius: 12px;
                overflow: hidden;
            }
            .tweet-frame {
                width: 100%;
                border: none;
                display: block;
                /* Start with a reasonable default height */
                height: 350px;
            }
        </style>
    </head>
    <body>
        $iframes
        
        <div style="height: 80px;"></div>
        
        <script>
            // Listen for resize messages from the Twitter embed iframes.
            // Twitter's embed page sends postMessage events with sizing info.
            window.addEventListener('message', function(event) {
                try {
                    var data = event.data;
                    
                    // Twitter embeds send messages in different formats
                    if (typeof data === 'object' && data['twttr.embed']) {
                        var embed = data['twttr.embed'];
                        if (embed.method === 'twttr.private.resize') {
                            var params = embed.params;
                            if (params && params.length > 0) {
                                var height = params[0].height;
                                if (height) {
                                    // Find which iframe sent this message
                                    var frames = document.querySelectorAll('.tweet-frame');
                                    for (var i = 0; i < frames.length; i++) {
                                        if (frames[i].contentWindow === event.source) {
                                            frames[i].style.height = height + 'px';
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Also handle string-format messages
                    if (typeof data === 'string') {
                        try {
                            var parsed = JSON.parse(data);
                            if (parsed.height && parsed.height > 0) {
                                var frames = document.querySelectorAll('.tweet-frame');
                                for (var i = 0; i < frames.length; i++) {
                                    if (frames[i].contentWindow === event.source) {
                                        frames[i].style.height = parsed.height + 'px';
                                        break;
                                    }
                                }
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
            });
        </script>
    </body>
    </html>
    """.trimIndent()
}