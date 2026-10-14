package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nomnomsom.armstrongandgetty.review.HoldReviewPromptWhile
import com.nomnomsom.armstrongandgetty.ui.theme.Gold

/**
 * Shown when a manual Download / Retry tap is held back by "Download over Wi-Fi only".
 * Rendered from App() (the view model is app-scoped there), so it appears over the
 * Episodes list and the player alike. Automatic downloads never show it.
 */
@Composable
fun WifiRequiredDialog(viewModel: EpisodeListViewModel) {
    val prompt by viewModel.wifiRequiredPrompt.collectAsState()
    // On iOS a Compose dialog shares the window, so window focus alone wouldn't see it.
    HoldReviewPromptWhile(prompt != null)
    val p = prompt ?: return
    val what = if (p.segmentIndex == null) "this episode" else "this segment"
    AlertDialog(
        onDismissRequest = viewModel::dismissWifiRequiredPrompt,
        title = { Text("Wi-Fi Only Is On") },
        text = {
            Text(
                "Download over Wi-Fi only is on, and you're not connected to Wi-Fi, so $what " +
                    "can't download right now. Turn the setting off to download now using mobile " +
                    "data, or try again on Wi-Fi. You can change it anytime on the About tab."
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::turnOffWifiOnlyAndRetry) {
                Text("Turn Off & Download", color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissWifiRequiredPrompt) {
                Text("Not Now")
            }
        },
    )
}
