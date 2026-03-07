package com.nomnomsom.aandg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nomnomsom.aandg.ui.screens.auth.AuthScreen
import com.nomnomsom.aandg.ui.screens.auth.AuthViewModel
import com.nomnomsom.aandg.ui.screens.episodelist.EpisodeListScreen
import com.nomnomsom.aandg.ui.screens.episodelist.EpisodeListViewModel
import com.nomnomsom.aandg.ui.screens.player.PlayerScreen
import com.nomnomsom.aandg.ui.theme.AGPodcastTheme
import com.nomnomsom.aandg.ui.theme.Gold
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AGPodcastTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

    when {
        authState.isLoading -> {
            // Splash / loading while checking auth state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Gold)
            }
        }
        authState.user == null -> {
            // Not signed in — show auth screen
            AuthScreen(viewModel = authViewModel)
        }
        else -> {
            // Signed in — show main app
            AGPodcastNavigation(authViewModel = authViewModel)
        }
    }
}

@Composable
fun AGPodcastNavigation(authViewModel: AuthViewModel) {
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
                authViewModel = authViewModel,
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
