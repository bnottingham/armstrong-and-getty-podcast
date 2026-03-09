package com.nomnomsom.armstrongandgetty.ui.screens.auth

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.nomnomsom.armstrongandgetty.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = true,
    val isSigningIn: Boolean = false,
    val user: FirebaseUser? = null,
    val error: String? = null,
    val skippedAuth: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = user,
                    // If user signs in after skipping, clear the skipped flag
                    skippedAuth = if (user != null) false else _uiState.value.skippedAuth
                )
            }
        }
    }

    val isAuthenticated: Boolean
        get() = _uiState.value.user != null

    /**
     * Get the Google Sign-In intent to launch.
     */
    fun getSignInIntent(): Intent = authRepository.getSignInIntent()

    /**
     * Handle the result from the Google Sign-In activity.
     */
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSigningIn = true, error = null)
            val result = authRepository.handleSignInResult(data)
            if (result.isSuccess) {
                Log.d("AUTH", "Firebase sign-in SUCCESS: ${result.getOrNull()?.email}")
                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    user = result.getOrNull(),
                    skippedAuth = false
                )
            } else {
                val error = result.exceptionOrNull()
                Log.e("AUTH", "Firebase sign-in FAILED: ${error?.javaClass?.simpleName}: ${error?.message}", error)
                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    error = "${error?.javaClass?.simpleName}: ${error?.message}"
                )
            }
        }
    }

    fun onSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            isSigningIn = false,
            error = message
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * User chose to continue without signing in.
     */
    fun skipAuth() {
        _uiState.value = _uiState.value.copy(skippedAuth = true)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // After sign-out, go back to auth screen (clear skipped flag)
            _uiState.value = _uiState.value.copy(skippedAuth = false)
        }
    }
}
