package com.nomnomsom.armstrongandgetty

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.mediarouter.app.MediaRouteButton
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
import com.nomnomsom.armstrongandgetty.ui.theme.AGPodcastTheme
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
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

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGPodcastNavigation() {
    val navController = rememberNavController()
    val episodeListViewModel: EpisodeListViewModel = hiltViewModel()

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
