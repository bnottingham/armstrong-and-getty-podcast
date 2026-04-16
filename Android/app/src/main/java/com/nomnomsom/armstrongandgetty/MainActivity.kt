package com.nomnomsom.armstrongandgetty

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.mediarouter.app.MediaRouteButton
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.cast.framework.CastButtonFactory
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListScreen
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.armstrongandgetty.ui.screens.player.PlayerScreen
import com.nomnomsom.armstrongandgetty.ui.screens.xfeed.XFeedScreen
import com.nomnomsom.armstrongandgetty.ui.screens.xfeed.XFeedViewModel
import com.nomnomsom.armstrongandgetty.ui.theme.AGPodcastTheme
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AGPodcastTheme {
                AGPodcastNavigation()
            }
        }
    }
}

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    data object Podcast : BottomTab("episodes", "Podcast", Icons.Filled.Headphones)
    data object XFeed : BottomTab("xfeed", "X", Icons.Filled.Tag)
}

private val bottomTabs = listOf(BottomTab.Podcast, BottomTab.XFeed)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGPodcastNavigation() {
    val navController = rememberNavController()
    val episodeListViewModel: EpisodeListViewModel = hiltViewModel()
    val xFeedViewModel: XFeedViewModel = hiltViewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute == "xfeed") {
            xFeedViewModel.onTabVisible()
        } else {
            xFeedViewModel.onTabHidden()
        }
    }

    val xUnreadCount by xFeedViewModel.unreadCount.collectAsState()

    val showBottomBar = currentRoute in bottomTabs.map { it.route }
    val isPlayerRoute = currentRoute?.startsWith("player/") == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isPlayerRoute) {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            "A&G",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Gold
                        )
                    }
                },
                navigationIcon = {
                    if (isPlayerRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Gold
                            )
                        }
                    }
                },
                actions = {
                    AndroidView(
                        factory = { context ->
                            MediaRouteButton(context).apply {
                                setBackgroundColor(0xFF0E0F13.toInt())
                                CastButtonFactory.setUpMediaRouteButton(context, this)
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Gold
                ) {
                    bottomTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (tab is BottomTab.XFeed && xUnreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = Gold,
                                                contentColor = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = if (xUnreadCount > 99) "99+" else xUnreadCount.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold,
                                selectedTextColor = Gold,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = Gold.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "episodes",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("episodes") {
                EpisodeListScreen(
                    viewModel = episodeListViewModel,
                    onEpisodeClick = { day ->
                        navController.navigate("player/${day.date}")
                    }
                )
            }

            composable("xfeed") {
                XFeedScreen(viewModel = xFeedViewModel)
            }

            composable(
                "player/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: return@composable
                val uiState by episodeListViewModel.uiState.collectAsState()
                val day = uiState.days.find { it.date == date }

                if (day != null) {
                    PlayerScreen(
                        day = day,
                        viewModel = episodeListViewModel
                    )
                }
            }
        }
    }
}
