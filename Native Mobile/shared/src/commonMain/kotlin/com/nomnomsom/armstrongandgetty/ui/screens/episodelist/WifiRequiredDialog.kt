package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nomnomsom.armstrongandgetty.download.NetworkKind
import com.nomnomsom.armstrongandgetty.platform.mobileDataTerm
import com.nomnomsom.armstrongandgetty.review.HoldReviewPromptWhile
import com.nomnomsom.armstrongandgetty.ui.theme.Gold

/** Dialog title and body for one held-back tap. */
internal data class WifiRequiredCopy(val title: String, val body: String)

/**
 * Pure copy for [WifiRequiredDialog]. Only the metered copy mentions turning the setting off
 * ([WifiRequiredPrompt.offersTurnOff]): offline, or with no reading, a download would just
 * fail, so the dialog never pushes the listener to give up their data-saving choice there.
 */
internal fun wifiRequiredCopy(prompt: WifiRequiredPrompt, dataTerm: String = mobileDataTerm): WifiRequiredCopy {
    val what = when {
        prompt.partial -> "the rest of this episode"
        prompt.segmentIndex == null -> "this episode"
        else -> "this segment"
    }
    return when (prompt.network) {
        NetworkKind.METERED -> WifiRequiredCopy(
            title = "Wi-Fi Only Is On",
            body = (if (prompt.partial) "Part of this episode downloaded before Wi-Fi dropped. " else "") +
                "You're on $dataTerm or a hotspot, and \"Download over Wi-Fi only\" is on. " +
                "Turn it off to download $what now, or download it later on Wi-Fi. " +
                "You can change this anytime on the About tab.",
        )
        NetworkKind.OFFLINE -> WifiRequiredCopy(
            title = "You're Offline",
            body = "Connect to Wi-Fi to download $what.",
        )
        // UNKNOWN (no reading arrived) or, defensively, UNMETERED.
        else -> WifiRequiredCopy(
            title = "Wi-Fi Only Is On",
            body = "Couldn't confirm a Wi-Fi connection, so $what didn't download. Try again in a moment.",
        )
    }
}

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
    val copy = wifiRequiredCopy(p)
    AlertDialog(
        onDismissRequest = viewModel::dismissWifiRequiredPrompt,
        title = { Text(copy.title) },
        text = { Text(copy.body) },
        confirmButton = {
            if (p.offersTurnOff) {
                TextButton(onClick = viewModel::turnOffWifiOnlyAndRetry) {
                    Text("Turn Off & Download", color = Gold)
                }
            } else {
                TextButton(onClick = viewModel::dismissWifiRequiredPrompt) {
                    Text("OK", color = Gold)
                }
            }
        },
        dismissButton = if (p.offersTurnOff) {
            {
                TextButton(onClick = viewModel::dismissWifiRequiredPrompt) {
                    Text("Not Now")
                }
            }
        } else {
            null
        },
    )
}
