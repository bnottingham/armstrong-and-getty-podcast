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
                // Pull-to-refresh only intercepts the drag when the WebView is scrolled to the top.
                var webViewAtTop by remember { mutableStateOf(true) }

                // Empty NestedScrollConnection: doesn't consume anything but wires the
                // WebView into Compose's nested-scroll chain so PullToRefreshBox can see drags.
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
 * Hosts one iframe per tweet (platform.twitter.com's embed page) and listens for
 * the embed's `twttr.private.resize` postMessage to size each iframe correctly.
 * `enableParentScroll`/`onScrollChanged` let the parent PullToRefreshBox intercept
 * the downward drag only when the WebView is already at the top.
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

                        // Let Twitter/X embed resources load in-WebView; send everything else to the device browser.
                        if (url.contains("platform.twitter.com")) return false
                        if (url.contains("syndication.twitter.com")) return false
                        if (url.contains("cdn.syndication.twimg.com")) return false
                        if (url.contains("pbs.twimg.com")) return false
                        if (url.contains("abs.twimg.com")) return false
                        if (url.contains("video.twimg.com")) return false
                        if (url.contains("ton.twimg.com")) return false

                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        ctx.startActivity(intent)
                        return true
                    }
                }

                webChromeClient = WebChromeClient()

                // Base URL is on Twitter's own domain so the embed iframes aren't blocked as cross-origin.
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
            // At the top of the page we disable overscroll and release the touch lock so
            // the parent PullToRefreshBox owns the downward drag.
            webView.overScrollMode = if (enableParentScroll) {
                WebView.OVER_SCROLL_NEVER
            } else {
                WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            webView.parent?.requestDisallowInterceptTouchEvent(!enableParentScroll)
        },
        modifier = modifier
    )
}

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
                height: 350px;
            }
        </style>
    </head>
    <body>
        $iframes
        
        <div style="height: 80px;"></div>
        
        <script>
            // Twitter's embed posts 'twttr.embed' / 'twttr.private.resize' messages with
            // the final rendered height; resize the matching iframe to avoid clipping.
            window.addEventListener('message', function(event) {
                try {
                    var data = event.data;

                    if (typeof data === 'object' && data['twttr.embed']) {
                        var embed = data['twttr.embed'];
                        if (embed.method === 'twttr.private.resize') {
                            var params = embed.params;
                            if (params && params.length > 0) {
                                var height = params[0].height;
                                if (height) {
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