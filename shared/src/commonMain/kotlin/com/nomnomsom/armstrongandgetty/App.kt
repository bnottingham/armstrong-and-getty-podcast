package com.nomnomsom.armstrongandgetty

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListScreen
import com.nomnomsom.armstrongandgetty.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.armstrongandgetty.ui.screens.player.PlayerScreen
import com.nomnomsom.armstrongandgetty.ui.theme.AGPodcastTheme
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
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
