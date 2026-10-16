package com.mongosky.app.auth

import com.mongosky.app.language.appText

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mongosky.app.session.SavedLoginSession
import com.mongosky.app.session.SessionStartupPolicy
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
    suspend fun read(): SavedLoginSession?
    suspend fun save(session: AuthSession)
    suspend fun update(session: AuthSession): Boolean
    suspend fun clearIfCurrent(token: String): Boolean
    suspend fun clear()
}

private class StoredLoginSession(private val store: TokenStore) : LoginSessionStore {
    override suspend fun read() = store.readSession()
    override suspend fun save(session: AuthSession) = store.saveSession(session)
    override suspend fun update(session: AuthSession) = store.updateSession(session)
    override suspend fun clearIfCurrent(token: String) = store.clearIfCurrent(token)
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
    private var validationJob: Job? = null
    private var requestJob: Job? = null
    private var generation = 0
    private var revision = 0L
    private var lastVerifiedAtNanos = 0L

    var uiState by mutableStateOf(LoginUiState(checkingSession = restoreOnStart))
        private set

    init { if (restoreOnStart) restoreSession() }

    fun restoreSession() {
        if (!restoreOnStart || uiState.loading || session != null || requestJob?.isActive == true) return
        // An explicit retry keeps its error form; initial startup has no intermediate auth UI.
        val retryError = uiState.restoreError
        uiState = LoginUiState(loading = retryError != null,
            checkingSession = retryError == null, restoreError = retryError)
        startRequest { attempt ->
            try {
                val saved = tokenStore.read()
                ensureCurrent(attempt)
                if (saved == null) {
                    uiState = LoginUiState(checkingSession = false)
                } else if (SessionStartupPolicy.isExpired(saved.token)) {
                    tokenStore.clearIfCurrent(saved.token)
                    ensureCurrent(attempt)
                    uiState = LoginUiState(checkingSession = false,
                        error = "Session expired. Please sign in again.")
                } else {
                    val cached = saved.account ?: SessionStartupPolicy.legacyAccount(saved.token)
                    if (cached != null) {
                        // This is the first route: no server call, delay, spinner or profile request.
                        showSignedIn(cached, verified = false)
                        validateSessionInBackground()
                    } else {
                        // One-time migration for legacy opaque tokens without an identity hint.
                        val restored = backend.getSession(saved.token)
                        ensureCurrent(attempt)
                        try { tokenStore.update(restored) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* The verified session is usable even if migration cannot be cached. */ }
                        ensureCurrent(attempt)
                        showSignedIn(restored)
                    }
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
                        try { tokenStore.save(result) }
                        catch (error: Exception) { throw LoginException("Could not save your session. Please retry.", LoginFailure.STORAGE, cause = error) }
                        showSignedIn(result)
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: LoginException) { if (attempt == generation) showLoginError(error.message ?: "Sign in failed.") }
            catch (_: IOException) { if (attempt == generation) showLoginError("Could not connect. Check your internet.") }
            catch (_: Exception) { if (attempt == generation) showLoginError("Unable to sign in. Please try again.") }
        }
    }

    /** Signup already returned an authenticated account; never send its password through a second login. */
    internal suspend fun acceptCreatedAccount(result: AuthSession) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!viewModelScope.isActive || uiState.loading || uiState.checkingSession ||
            uiState.restoreError != null || !validAuthToken(result.token) || result.userId.isBlank()) {
            throw LoginException("Could not start your session. Please retry.")
        }
        if (commitSession != null) commitSession.invoke(result)
        else commitAuthenticatedSession(result) {}
    }

    /** Cancel old feature requests before the token changes; publish disk and memory together. */
    internal suspend fun commitAuthenticatedSession(result: AuthSession, beforeCommit: () -> Unit) {
        if (!viewModelScope.isActive || uiState.loading || uiState.checkingSession ||
            result.token.isBlank() || result.userId.isBlank()) throw LoginException("Could not switch accounts. Please retry.")
        val previousState = uiState
        val previousSession = session
        cancelSessionRequests()
        uiState = previousState.copy(loading = true, error = null)
        withContext(NonCancellable) {
            try {
                beforeCommit()
                tokenStore.save(result)
            } catch (_: Exception) {
                session = previousSession
                uiState = previousState
                lastVerifiedAtNanos = 0L
                validateSessionInBackground()
                throw LoginException("Could not save your session. Please retry.", LoginFailure.STORAGE)
            }
            // A cancelled form cannot revert memory after the new token has been stored.
            showSignedIn(result)
        }
    }

    /** A warm foreground entry also revalidates silently, coalesced and throttled after success. */
    fun validateSessionInBackground() {
        val current = session ?: return
        if (uiState.loading || uiState.checkingSession || validationJob?.isActive == true) return
        if (lastVerifiedAtNanos != 0L && System.nanoTime() - lastVerifiedAtNanos < 60_000_000_000L) return
        val attempt = generation
        validationJob = viewModelScope.launch {
            try {
                if (SessionStartupPolicy.isExpired(current.token)) throw SessionExpiredException()
                val verified = backend.getSession(current.token)
                ensureCurrent(attempt)
                val active = session?.takeIf { it.token == current.token } ?: return@launch
                if (verified.token != active.token || verified.userId != active.userId) throw SessionExpiredException()
                // /auth/me has no avatar: retain the snapshot until /user/me refreshes it.
                val updated = AuthSession(active.token, active.userId,
                    verified.firstName.ifBlank { active.firstName },
                    verified.lastName.ifBlank { active.lastName }, active.profileImageUrl)
                session = updated
                uiState = uiState.copy(signedInName = displayName(updated))
                lastVerifiedAtNanos = System.nanoTime()
                cacheSession(updated, attempt)
                ensureCurrent(attempt)
                refreshProfile()
            } catch (error: CancellationException) { throw error }
            catch (_: SessionExpiredException) { expireSession(current, attempt) }
            catch (_: Exception) {
                // Offline, 429 and 5xx do not log out a cached account. Retry on next foreground.
            }
        }
    }

    /** Keep the current avatar visible; a response from an earlier session is ignored. */
    fun refreshProfile() {
        val current = session ?: return
        if (uiState.loading || uiState.checkingSession || profileRefreshJob?.isActive == true) return
        val attempt = generation
        profileRefreshJob = viewModelScope.launch {
            try {
                val refreshed = backend.getProfile(current.token)
                ensureActive()
                if (attempt != generation || session?.token != current.token || uiState.loading ||
                    refreshed.token != current.token || refreshed.userId != current.userId) return@launch
                session = refreshed
                uiState = uiState.copy(signedInName = displayName(refreshed),
                    signedInUserId = refreshed.userId, signedInProfileImageUrl = refreshed.profileImageUrl)
                cacheSession(refreshed, attempt)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Keep the last known profile when offline. */ }
        }
    }

    fun signOut() {
        if (uiState.loading) return
        val previousState = uiState
        cancelSessionRequests()
        uiState = previousState.copy(loading = true, error = null)
        startRequest { attempt ->
            try {
                withContext(NonCancellable) { tokenStore.clear() }
                ensureCurrent(attempt)
                session = null
                uiState = LoginUiState(checkingSession = false, sessionRevision = revision)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (attempt == generation) uiState = if (previousState.restoreError != null)
                    previousState.copy(loading = false, restoreError = "Could not remove the session. Please retry.")
                else previousState.copy(loading = false, error = "Could not remove the session. Please retry.")
                if (attempt == generation) {
                    lastVerifiedAtNanos = 0L
                    validateSessionInBackground()
                }
            }
        }
    }

    /** Close an isolated form without signing out, storing credentials, or changing the active token. */
    fun endSession() {
        cancelSessionRequests()
        session = null
        uiState = LoginUiState(checkingSession = false)
    }

    private fun cancelSessionRequests() {
        generation++
        requestJob?.cancel(); requestJob = null
        validationJob?.cancel(); validationJob = null
        profileRefreshJob?.cancel(); profileRefreshJob = null
    }

    private suspend fun cacheSession(account: AuthSession, attempt: Int) {
        ensureCurrent(attempt)
        if (session !== account) return
        try { tokenStore.update(account) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* A cache-write failure must not hide an already usable Home. */ }
    }

    private suspend fun expireSession(current: AuthSession, attempt: Int) {
        ensureCurrent(attempt)
        if (session?.token != current.token) return
        profileRefreshJob?.cancel()
        session = null
        uiState = LoginUiState(loading = true, checkingSession = false, sessionRevision = revision)
        try {
            withContext(NonCancellable) { tokenStore.clearIfCurrent(current.token) }
            ensureCurrent(attempt)
            uiState = LoginUiState(checkingSession = false,
                error = "Session expired. Please sign in again.", sessionRevision = revision)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            if (attempt == generation) showRestoreError("Could not clear the expired session. Try again.")
        }
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
    private fun showSignedIn(result: AuthSession, verified: Boolean = true) {
        session = result
        lastVerifiedAtNanos = if (verified) System.nanoTime() else 0L
        revision++
        uiState = LoginUiState(checkingSession = false, signedInName = displayName(result),
            signedInUserId = result.userId, signedInProfileImageUrl = result.profileImageUrl,
            sessionRevision = revision)
        if (verified) refreshProfile()
    }
    private fun displayName(user: AuthSession) = "${user.firstName} ${user.lastName}".trim().ifBlank { appText("Mongosky user") }
    private fun showLoginError(message: String) { uiState = LoginUiState(checkingSession = false, error = message) }
    private fun showRestoreError(message: String) { uiState = LoginUiState(checkingSession = false, restoreError = message) }
}
