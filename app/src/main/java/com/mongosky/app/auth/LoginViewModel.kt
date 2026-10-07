package com.mongosky.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.AuthRepository
import com.mongosky.app.auth.AuthSession
import com.mongosky.app.auth.LoginException
import com.mongosky.app.auth.SessionApi
import com.mongosky.app.auth.SessionExpiredException
import com.mongosky.app.auth.TokenStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val checkingSession: Boolean = true,
    val restoreError: String? = null,
    val error: String? = null,
    val signedInName: String? = null,
    val signedInUserId: String? = null,
    val signedInProfileImageUrl: String? = null
)

class LoginViewModel(
    private val tokenStore: TokenStore
) : ViewModel() {

    private val repository = AuthRepository()
    private val sessionApi = SessionApi()

    private var session: AuthSession? = null
    private var profileRefreshJob: Job? = null

    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        restoreSession()
    }

    fun restoreSession() {
        if (uiState.loading || session != null) return

        uiState = LoginUiState(
            loading = true,
            checkingSession = true
        )

        viewModelScope.launch {
            try {
                val token = tokenStore.read()

                if (token == null) {
                    uiState = LoginUiState(checkingSession = false)
                    return@launch
                }

                val restored = sessionApi.getSession(token)
                showSignedIn(restored)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SessionExpiredException) {
                try {
                    tokenStore.clear()
                    uiState = LoginUiState(
                        checkingSession = false,
                        error = "Session expired. Please sign in again."
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showRestoreError(
                        "Could not clear the expired session. Try again."
                    )
                }
            } catch (_: Exception) {
                showRestoreError(
                    "Could not restore your session. " +
                            "Check your internet and retry, " +
                            "or return to sign in."
                )
            }
        }
    }

    fun login(emailOrPhone: String, password: String) {
        if (
            uiState.loading ||
            uiState.checkingSession ||
            uiState.restoreError != null ||
            session != null
        ) return

        if (emailOrPhone.isBlank() || password.isEmpty()) {
            uiState = LoginUiState(
                checkingSession = false,
                error = "Enter your email or phone and password."
            )
            return
        }

        uiState = LoginUiState(
            loading = true,
            checkingSession = false
        )

        viewModelScope.launch {
            try {
                val result = repository.login(emailOrPhone, password)

                try {
                    tokenStore.save(result.token)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    uiState = LoginUiState(
                        checkingSession = false,
                        error = "Could not save your session. Please retry."
                    )
                    return@launch
                }

                showSignedIn(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: LoginException) {
                uiState = LoginUiState(
                    checkingSession = false,
                    error = error.message ?: "Sign in failed."
                )
            } catch (_: IOException) {
                uiState = LoginUiState(
                    checkingSession = false,
                    error = "Could not connect. Check your internet."
                )
            } catch (_: Exception) {
                uiState = LoginUiState(
                    checkingSession = false,
                    error = "Unable to sign in. Please try again."
                )
            }
        }
    }

    /** Keep the current avatar visible; this request never blocks opening a composer. */
    fun refreshProfile() {
        val currentSession = session ?: return
        if (uiState.loading || uiState.checkingSession || profileRefreshJob?.isActive == true) return

        profileRefreshJob = viewModelScope.launch {
            try {
                val refreshed = sessionApi.getProfile(currentSession.token)
                ensureActive()
                // A response from an account that signed out must not update the next account.
                if (session !== currentSession || uiState.loading ||
                    refreshed.userId != currentSession.userId
                ) return@launch
                session = refreshed
                uiState = uiState.copy(
                    signedInName = displayName(refreshed),
                    signedInUserId = refreshed.userId,
                    signedInProfileImageUrl = refreshed.profileImageUrl
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // An offline refresh keeps the last known profile and the draft usable.
            }
        }
    }

    fun signOut() {
        if (uiState.loading) return
        profileRefreshJob?.cancel()

        val previousState = uiState

        uiState = previousState.copy(
            loading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                tokenStore.clear()
                session = null
                uiState = LoginUiState(checkingSession = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Could not remove the session. Please retry."

                uiState = if (previousState.restoreError != null) {
                    previousState.copy(
                        loading = false,
                        restoreError = message
                    )
                } else {
                    previousState.copy(
                        loading = false,
                        error = message
                    )
                }
            }
        }
    }

    private fun showSignedIn(result: AuthSession) {
        session = result

        uiState = LoginUiState(
            checkingSession = false,
            signedInName = displayName(result),
            signedInUserId = result.userId,
            signedInProfileImageUrl = result.profileImageUrl
        )
        refreshProfile()
    }

    private fun displayName(user: AuthSession): String =
        "${user.firstName} ${user.lastName}".trim().ifBlank { "Mongosky user" }

    private fun showRestoreError(message: String) {
        uiState = LoginUiState(
            checkingSession = false,
            restoreError = message
        )
    }
}
