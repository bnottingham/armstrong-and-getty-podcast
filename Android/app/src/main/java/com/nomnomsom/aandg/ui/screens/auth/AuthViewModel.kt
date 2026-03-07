package com.nomnomsom.aandg.ui.screens.auth

import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.nomnomsom.aandg.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = true, // Start true while checking initial auth state
    val isSigningIn: Boolean = false,
    val user: FirebaseUser? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Observe Firebase auth state
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = user
                )
            }
        }
    }

    /**
     * Build the credential request for the Activity to use with CredentialManager.
     */
    fun buildGoogleSignInRequest() = authRepository.buildGoogleSignInRequest()

    /**
     * Called when the Activity receives a credential response.
     */
    fun handleSignInResult(response: GetCredentialResponse) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSigningIn = true, error = null)
            val result = authRepository.handleSignInResult(response)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    user = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    error = result.exceptionOrNull()?.message ?: "Sign-in failed"
                )
            }
        }
    }

    /**
     * Called when sign-in fails at the credential manager level (before Firebase).
     */
    fun onSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            isSigningIn = false,
            error = message
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
