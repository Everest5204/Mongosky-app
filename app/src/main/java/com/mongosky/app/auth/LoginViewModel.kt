package com.mongosky.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface LoginSessionStore {
    suspend fun read(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

private class StoredLoginSession(private val store: TokenStore) : LoginSessionStore {
    override suspend fun read() = store.read()
    override suspend fun save(token: String) = store.save(token)
    override suspend fun clear() = store.clear()
}

internal interface LoginBackend {
    suspend fun login(identity: String, password: String): AuthSession
    suspend fun getSession(token: String): AuthSession
    suspend fun getProfile(token: String): AuthSession
}

private object NetworkLoginBackend : LoginBackend {
    private val repository by lazy { AuthRepository() }
    private val sessions by lazy { SessionApi() }
    override suspend fun login(identity: String, password: String) = repository.login(identity, password)
    override suspend fun getSession(token: String) = sessions.getSession(token)
    override suspend fun getProfile(token: String) = sessions.getProfile(token)
}

data class LoginUiState(
    val loading: Boolean = false,
    val checkingSession: Boolean = true,
    val restoreError: String? = null,
    val error: String? = null,
    val signedInName: String? = null,
    val signedInUserId: String? = null,
    val signedInProfileImageUrl: String? = null,
    val sessionRevision: Long = 0
)

/** Drawer login is an isolated form. Only a confirmed login may replace the active session. */
class LoginViewModel internal constructor(
    private val tokenStore: LoginSessionStore,
    private val backend: LoginBackend,
    private val restoreOnStart: Boolean = true,
    private val commitSession: (suspend (AuthSession) -> Unit)? = null
) : ViewModel() {
    constructor(tokenStore: TokenStore, restoreOnStart: Boolean = true,
        commitSession: (suspend (AuthSession) -> Unit)? = null) :
        this(StoredLoginSession(tokenStore), NetworkLoginBackend, restoreOnStart, commitSession)

    private var session: AuthSession? = null
    private var profileRefreshJob: Job? = null
    private var requestJob: Job? = null
    private var generation = 0
    private var revision = 0L

    var uiState by mutableStateOf(LoginUiState(checkingSession = restoreOnStart))
        private set

    init { if (restoreOnStart) restoreSession() }

    fun restoreSession() {
        if (!restoreOnStart || uiState.loading || session != null) return
        uiState = LoginUiState(loading = true, checkingSession = true)
        startRequest { attempt ->
            try {
                val token = tokenStore.read()
                ensureCurrent(attempt)
                if (token == null) {
                    uiState = LoginUiState(checkingSession = false)
                } else {
                    val restored = backend.getSession(token)
                    ensureCurrent(attempt)
                    showSignedIn(restored)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SessionExpiredException) {
                ensureCurrent(attempt)
                try {
                    tokenStore.clear()
                    ensureCurrent(attempt)
                    uiState = LoginUiState(checkingSession = false,
                        error = "Session expired. Please sign in again.")
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { if (attempt == generation) showRestoreError("Could not clear the expired session. Try again.") }
            } catch (_: Exception) {
                if (attempt == generation) showRestoreError("Could not restore your session. Check your internet and retry, or return to sign in.")
            }
        }
    }

    fun login(emailOrPhone: String, password: String) {
        if (uiState.loading || uiState.checkingSession || uiState.restoreError != null || session != null) return
        if (emailOrPhone.isBlank() || password.isEmpty()) {
            uiState = LoginUiState(checkingSession = false, error = "Enter your email or phone and password.")
            return
        }
        uiState = LoginUiState(loading = true, checkingSession = false)
        startRequest { attempt ->
            try {
                val result = backend.login(emailOrPhone, password)
                ensureCurrent(attempt)
                if (commitSession != null) {
                    commitSession.invoke(result)
                    ensureCurrent(attempt)
                    // The activity switches its session; this form never reads the old account token.
                    uiState = LoginUiState(checkingSession = false)
                } else {
                    withContext(NonCancellable) {
                        try { tokenStore.save(result.token) }
                        catch (_: Exception) { throw LoginException("Could not save your session. Please retry.") }
                        showSignedIn(result)
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: LoginException) { if (attempt == generation) showLoginError(error.message ?: "Sign in failed.") }
            catch (_: IOException) { if (attempt == generation) showLoginError("Could not connect. Check your internet.") }
            catch (_: Exception) { if (attempt == generation) showLoginError("Unable to sign in. Please try again.") }
        }
    }

    /** Cancel old feature requests before the token changes; publish disk and memory together. */
    internal suspend fun commitAuthenticatedSession(result: AuthSession, beforeCommit: () -> Unit) {
        if (!viewModelScope.isActive || uiState.loading || uiState.checkingSession ||
            result.token.isBlank() || result.userId.isBlank()) throw LoginException("Could not switch accounts. Please retry.")
        val previousState = uiState
        val previousSession = session
        profileRefreshJob?.cancel()
        uiState = previousState.copy(loading = true, error = null)
        withContext(NonCancellable) {
            try {
                beforeCommit()
                tokenStore.save(result.token)
            } catch (_: Exception) {
                session = previousSession
                uiState = previousState
                refreshProfile()
                throw LoginException("Could not save your session. Please retry.")
            }
            // A cancelled form cannot revert memory after the new token has been stored.
            showSignedIn(result)
        }
    }

    /** Keep the current avatar visible; a response from an earlier session is ignored. */
    fun refreshProfile() {
        val current = session ?: return
        if (uiState.loading || uiState.checkingSession || profileRefreshJob?.isActive == true) return
        profileRefreshJob = viewModelScope.launch {
            try {
                val refreshed = backend.getProfile(current.token)
                ensureActive()
                if (session !== current || uiState.loading || refreshed.userId != current.userId) return@launch
                session = refreshed
                uiState = uiState.copy(signedInName = displayName(refreshed),
                    signedInUserId = refreshed.userId, signedInProfileImageUrl = refreshed.profileImageUrl)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Keep the last known profile when offline. */ }
        }
    }

    fun signOut() {
        if (uiState.loading) return
        profileRefreshJob?.cancel()
        val previousState = uiState
        uiState = previousState.copy(loading = true, error = null)
        startRequest { attempt ->
            try {
                tokenStore.clear()
                ensureCurrent(attempt)
                session = null
                uiState = LoginUiState(checkingSession = false, sessionRevision = revision)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (attempt == generation) uiState = if (previousState.restoreError != null)
                    previousState.copy(loading = false, restoreError = "Could not remove the session. Please retry.")
                else previousState.copy(loading = false, error = "Could not remove the session. Please retry.")
            }
        }
    }

    /** Close an isolated form without signing out, storing credentials, or changing the active token. */
    fun endSession() {
        generation++
        requestJob?.cancel(); requestJob = null
        profileRefreshJob?.cancel(); profileRefreshJob = null
        session = null
        uiState = LoginUiState(checkingSession = false)
    }

    private fun startRequest(block: suspend kotlinx.coroutines.CoroutineScope.(Int) -> Unit) {
        val attempt = generation
        val work = viewModelScope.launch(start = CoroutineStart.LAZY) { block(attempt) }
        requestJob = work
        work.invokeOnCompletion { if (requestJob === work) requestJob = null }
        work.start()
    }
    private suspend fun ensureCurrent(attempt: Int) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (attempt != generation) throw CancellationException("Auth form closed")
    }
    private fun showSignedIn(result: AuthSession) {
        session = result
        revision++
        uiState = LoginUiState(checkingSession = false, signedInName = displayName(result),
            signedInUserId = result.userId, signedInProfileImageUrl = result.profileImageUrl,
            sessionRevision = revision)
        refreshProfile()
    }
    private fun displayName(user: AuthSession) = "${user.firstName} ${user.lastName}".trim().ifBlank { "Mongosky user" }
    private fun showLoginError(message: String) { uiState = LoginUiState(checkingSession = false, error = message) }
    private fun showRestoreError(message: String) { uiState = LoginUiState(checkingSession = false, restoreError = message) }
}
