package com.nomnomsom.aandg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nomnomsom.aandg.ui.screens.episodelist.EpisodeListScreen
import com.nomnomsom.aandg.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.aandg.ui.screens.player.PlayerScreen
import com.nomnomsom.aandg.ui.theme.AGPodcastTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

@Composable
fun AGPodcastNavigation() {
    val navController = rememberNavController()

    // Share the same ViewModel across screens so playback state persists
    val viewModel: EpisodeListViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = "episodes",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("episodes") {
            EpisodeListScreen(
                viewModel = viewModel,
                onEpisodeClick = { day ->
                    navController.navigate("player/${day.date}")
                }
            )
        }

        composable(
            "player/{date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: return@composable
            val uiState by viewModel.uiState.collectAsState()
            val day = uiState.days.find { it.date == date }

            if (day != null) {
                PlayerScreen(
                    day = day,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
