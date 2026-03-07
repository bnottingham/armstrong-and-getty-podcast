package com.nomnomsom.aandg.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        // Web client ID from google-services.json → oauth_client with client_type 3
        const val WEB_CLIENT_ID = "801903863071-055or60t46oh6vdo6kh1i826bur9ehnr.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    /**
     * Observe auth state changes as a Flow.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Build the Credential Manager request for Google Sign-In.
     * The caller (Activity) must invoke credentialManager.getCredential() with this request.
     */
    fun buildGoogleSignInRequest(): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    /**
     * Handle the credential response from Credential Manager and sign in with Firebase.
     */
    suspend fun handleSignInResult(response: GetCredentialResponse): Result<FirebaseUser> {
        val credential = response.credential

        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                        val user = authResult.user
                        if (user != null) {
                            Result.success(user)
                        } else {
                            Result.failure(Exception("Firebase sign-in returned null user"))
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        Result.failure(Exception("Failed to parse Google ID token: ${e.message}"))
                    }
                } else {
                    Result.failure(Exception("Unexpected credential type: ${credential.type}"))
                }
            }
            else -> Result.failure(Exception("Unexpected credential type"))
        }
    }

    /**
     * Sign out from Firebase and clear credential state.
     */
    suspend fun signOut() {
        firebaseAuth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Clearing credential state can fail on some devices; non-critical
        }
    }
}
