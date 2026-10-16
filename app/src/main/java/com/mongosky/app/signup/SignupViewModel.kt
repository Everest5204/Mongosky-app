package com.mongosky.app.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.AuthSession
import com.mongosky.app.auth.AuthValidation
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class SignupUiState(
    val loading: Boolean = false, val error: String? = null,
    val sessionCommitted: Boolean = false, val outcomeUnknown: Boolean = false
)

class SignupViewModel(
    private val api: SignupDataSource = SignupApi(),
    private val commitSession: suspend (AuthSession) -> Unit
) : ViewModel() {
    var uiState by mutableStateOf(SignupUiState())
        private set
    // Memory only: rotation keeps the draft; passwords never enter Bundle/SavedStateHandle/disk.
    var passwordDraft by mutableStateOf("")
        private set
    private var job: Job? = null
    private var generation = 0
    private var confirmedAccount: AuthSession? = null
    private var confirmedRequest: SignupRequest? = null
    private var completed = false

    fun updatePassword(value: String) { if (!uiState.loading) passwordDraft = value }
    fun clearError() { if (!uiState.loading && !uiState.outcomeUnknown) uiState = uiState.copy(error = null) }
    fun reset() { if (!uiState.loading) endSession() }

    fun signup(request: SignupRequest, policyAccepted: Boolean) {
        if (uiState.loading || completed || uiState.sessionCommitted || uiState.outcomeUnknown) return
        if (confirmedAccount != null && confirmedRequest != request) {
            uiState = SignupUiState(error = "Your account was already created. Use Sign in, or restore the same form details to finish signing in.")
            return
        }
        val validation = if (confirmedAccount != null) null
            else if (!policyAccepted) "Accept the Privacy Policy to continue." else AuthValidation.signup(request)
        if (validation != null) { uiState = SignupUiState(error = validation); return }
        uiState = SignupUiState(loading = true)
        val attempt = generation
        val work = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // If storage failed after account creation, retry only the local sign-in commit.
                val result = confirmedAccount ?: api.signup(request)
                ensureActive()
                if (attempt != generation) return@launch
                confirmedAccount = result
                confirmedRequest = request
                // A real session handoff is required in every host; account creation is not an auth page.
                commitSession(result)
                ensureActive()
                if (attempt != generation) return@launch
                passwordDraft = ""
                confirmedAccount = null
                confirmedRequest = null
                completed = true
                uiState = SignupUiState(sessionCommitted = true)
            } catch (error: CancellationException) { throw error }
            catch (error: SignupException) {
                if (attempt == generation) uiState = SignupUiState(
                    error = error.message ?: "Could not create account.", outcomeUnknown = error.outcomeUnknown)
            } catch (_: IOException) {
                if (attempt == generation) showCompletionError()
            } catch (_: Exception) {
                if (attempt == generation) showCompletionError()
            } finally { if (job === coroutineContext[Job]) job = null }
        }
        job = work; work.start()
    }

    /** Exit/logout cancels this client request and prevents its late result entering a different account's screen. */
    fun endSession() {
        generation++; job?.cancel(); job = null
        passwordDraft = ""; confirmedAccount = null; confirmedRequest = null; completed = false; uiState = SignupUiState()
    }
    private fun showCompletionError() {
        val created = confirmedAccount != null
        uiState = SignupUiState(error = if (created)
            "Your account was created. Tap Create account again to finish signing in."
            else UNCERTAIN, outcomeUnknown = !created)
    }
    override fun onCleared() { endSession(); super.onCleared() }
    class Factory(private val commitSession: suspend (AuthSession) -> Unit) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SignupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SignupViewModel(commitSession = commitSession) as T
        }
    }
    private companion object {
        const val UNCERTAIN = "Could not confirm account creation. Try signing in before creating it again."
    }
}
