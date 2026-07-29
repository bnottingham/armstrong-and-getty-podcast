package com.nomnomsom.armstrongandgetty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.nomnomsom.armstrongandgetty.ui.icons.AppIcons
import com.nomnomsom.armstrongandgetty.ui.icons.Headset
import com.nomnomsom.armstrongandgetty.ui.icons.Info
import com.nomnomsom.armstrongandgetty.ui.screens.about.AboutScreen
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListScreen
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.armstrongandgetty.ui.screens.player.PlayerScreen
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.ui.theme.AGPodcastTheme
import com.nomnomsom.armstrongandgetty.ui.theme.CardBg
import com.nomnomsom.armstrongandgetty.ui.theme.DarkBg
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.GoldDark
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Cast button on Android (Chromecast), AirPlay route picker on iOS. */
@Composable
expect fun MediaRouteAction(modifier: Modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    AGPodcastTheme {
        val navController = rememberNavController()
        val episodeListViewModel: EpisodeListViewModel = koinViewModel()

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val analytics: AnalyticsTracker = koinInject()
        LaunchedEffect(currentRoute) {
            when {
                currentRoute == "episodes" -> analytics.logScreen("episodes")
                currentRoute == "about" -> analytics.logScreen("about")
                currentRoute?.startsWith("player/") == true -> analytics.logScreen("player")
            }
        }

        val isPlayerRoute = currentRoute?.startsWith("player/") == true
        val isTopLevelRoute = currentRoute == "episodes" || currentRoute == "about"

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        when {
                            isPlayerRoute -> Text(
                                "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall
                            )
                            // The About tab carries its own hero branding.
                            currentRoute == "about" -> Text(
                                "A&G",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Gold
                            )
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Brush.linearGradient(listOf(Gold, GoldDark)))
                                ) {
                                    Text(
                                        "A&G",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = DarkBg
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Armstrong & Getty",
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp)
                                    )
                                    Text(
                                        "PODCAST · ON DEMAND",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            letterSpacing = 1.4.sp
                                        ),
                                        color = Gold
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (isPlayerRoute) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    AppIcons.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Gold
                                )
                            }
                        }
                    },
                    actions = {
                        MediaRouteAction(modifier = Modifier)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (isTopLevelRoute) {
                    NavigationBar(containerColor = CardBg) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            indicatorColor = Gold.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                        NavigationBarItem(
                            selected = currentRoute == "episodes",
                            onClick = {
                                navController.navigate("episodes") {
                                    popUpTo("episodes") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(AppIcons.Headset, contentDescription = null) },
                            label = { Text("Podcast") },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = currentRoute == "about",
                            onClick = {
                                navController.navigate("about") {
                                    popUpTo("episodes") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(AppIcons.Info, contentDescription = null) },
                            label = { Text("About") },
                            colors = itemColors
                        )
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

                composable("about") {
                    AboutScreen()
                }

                composable(
                    "player/{date}",
                    arguments = listOf(navArgument("date") { type = NavType.StringType })
                ) { backStackEntry ->
                    val date = backStackEntry.arguments?.read { getStringOrNull("date") }
                        ?: return@composable
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
}
