package com.nomnomsom.armstrongandgetty.ui.screens.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    viewModel: AuthViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Activity Result launcher for Google Sign-In
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("AUTH", "Sign-in result: resultCode=${result.resultCode}, data=${result.data}")
        // Always try to handle the result — Google Sign-In can return
        // RESULT_OK or RESULT_CANCELED but still have valid data
        if (result.data != null) {
            viewModel.handleSignInResult(result.data)
        } else {
            Log.e("AUTH", "No data in result")
            viewModel.onSignInFailed("Sign-in returned no data")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A&G",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp,
                    color = Gold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Armstrong & Getty",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "PODCAST · ON DEMAND",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Sign in to sync your listening progress across devices",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Google Sign-In button
            Button(
                onClick = {
                    Log.d("AUTH", "Button clicked — launching Google Sign-In intent")
                    viewModel.clearError()
                    val signInIntent = viewModel.getSignInIntent()
                    signInLauncher.launch(signInIntent)
                },
                enabled = !uiState.isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledContainerColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Signing in…",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                } else {
                    Text(
                        text = "G",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Continue with Google",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            // Error message
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = uiState.error ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Your data stays private and is only used\nto sync your podcast progress.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            )
        }
    }
}