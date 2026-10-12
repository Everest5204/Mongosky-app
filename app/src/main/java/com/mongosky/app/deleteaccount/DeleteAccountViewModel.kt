package com.mongosky.app.deleteaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Password/receipt stay in memory. Acceptance and verified completion are different states. */
internal class DeleteAccountViewModel(
    private val source: DeleteAccountSource,
    private val readToken: suspend () -> String?
) : ViewModel() {
    var state by mutableStateOf(DeleteAccountState())
        private set
    private var requestJob: Job? = null
    private var pollJob: Job? = null
    private var exitJob: Job? = null
    private var originalToken: String? = null
    private var receipt: DeletionReceipt? = null
    private var generation = 0
    private var foreground = true

    fun updatePassword(value: String) {
        if (!state.busy && !state.dialogOpen) state = state.copy(password = value, passwordError = null, error = null)
    }

    fun openConfirmation() {
        if (state.busy || state.dialogOpen) return
        if (state.password.isEmpty()) { state = state.copy(passwordError = PASSWORD_REQUIRED); return }
        state = state.copy(dialogOpen = true, passwordError = null, error = null)
    }

    fun submit() {
        if (!state.dialogOpen || state.busy) return
        val password = state.password
        if (password.isEmpty()) { state = state.copy(error = PASSWORD_REQUIRED); return }
        val attempt = generation
        state = state.copy(phase = DeletionPhase.SUBMITTING, error = null, closeRequested = false)
        requestJob = viewModelScope.launch {
            try {
                val token = readToken()
                ensureActive()
                if (attempt != generation) return@launch
                if (token.isNullOrBlank()) {
                    state = state.copy(password = "", phase = DeletionPhase.IDLE, sessionExpired = true, dialogOpen = true,
                        error = "Your session has expired. Please sign in again.")
                    requestExit(); return@launch
                }
                if (originalToken != null && token != originalToken) { changedSession(); return@launch }
                originalToken = token
                val accepted = source.request(password, token)
                ensureActive()
                if (attempt != generation) return@launch
                receipt = accepted
                state = state.copy(password = "", phase = DeletionPhase.PROCESSING, error = null)
                if (!ownsSession()) return@launch
                if (state.closeRequested) requestExit() else startPolling()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (attempt != generation) return@launch
                if (originalToken != null && !ownsSession()) {
                    if (!state.sessionChanged) state = state.copy(phase = DeletionPhase.IDLE, closeRequested = false)
                    return@launch
                }
                if (error is DeleteAccountException && error.status == 401) {
                    state = state.copy(password = "", phase = DeletionPhase.IDLE, dialogOpen = true, sessionExpired = true,
                        error = error.message ?: "Your session has expired. Please sign in again.")
                    requestExit()
                } else {
                    state = state.copy(phase = DeletionPhase.IDLE, closeRequested = false,
                        error = (error as? DeleteAccountException)?.message ?: REQUEST_UNCONFIRMED)
                }
            } finally { if (attempt == generation) requestJob = null }
        }
    }

    /** Dismissing while submission is pending keeps that request alive until its result is known. */
    fun dismiss() {
        when {
            state.sessionChanged -> { state = DeleteAccountState(); originalToken = null }
            state.sessionExpired || state.phase == DeletionPhase.PROCESSING || state.phase == DeletionPhase.DELETED -> requestExit()
            state.phase == DeletionPhase.SUBMITTING -> state = state.copy(dialogOpen = false, password = "", closeRequested = true)
            else -> { state = DeleteAccountState(); originalToken = null }
        }
    }

    /** Return true only when it is safe to pop this native page. */
    fun back(): Boolean {
        if (state.dialogOpen || state.phase != DeletionPhase.IDLE || state.sessionExpired) { dismiss(); return false }
        leave(); return true
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) { pollJob?.cancel(); pollJob = null }
        else if (state.phase == DeletionPhase.PROCESSING && !state.terminalProgressError && !state.exitRequested) startPolling()
    }

    private fun startPolling() {
        val statusReceipt = receipt ?: return
        if (!foreground || pollJob?.isActive == true || state.exitRequested) return
        val attempt = generation
        pollJob = viewModelScope.launch {
            while (foreground && attempt == generation && state.phase == DeletionPhase.PROCESSING && !state.exitRequested) {
                if (!ownsSession()) return@launch
                var nextDelay = 2000L
                try {
                    val progress = source.progress(statusReceipt)
                    ensureActive()
                    if (attempt != generation || !ownsSession()) return@launch
                    if (progress == DeletionProgress.DELETED) {
                        state = state.copy(phase = DeletionPhase.DELETED, error = null, terminalProgressError = false)
                        return@launch
                    }
                    state = state.copy(error = null)
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (attempt != generation || !ownsSession()) return@launch
                    if (error is DeleteAccountException && error.status in setOf(400, 401, 403, 404, 410, 422)) {
                        state = state.copy(error = PROGRESS_UNCONFIRMED, terminalProgressError = true)
                        return@launch
                    }
                    state = state.copy(error = PROGRESS_RETRY)
                    nextDelay = 10000L
                }
                delay(nextDelay)
            }
        }
    }

    fun requestExit() {
        if (exitJob?.isActive == true || state.sessionChanged) return
        if (!state.sessionExpired && state.phase != DeletionPhase.PROCESSING && state.phase != DeletionPhase.DELETED) return
        pollJob?.cancel(); pollJob = null
        val attempt = generation
        exitJob = viewModelScope.launch {
            if (ownsSession() && attempt == generation) {
                state = state.copy(password = "", dialogOpen = true, exitRequested = true, exitRevision = state.exitRevision + 1)
            }
        }
    }

    private suspend fun ownsSession(): Boolean {
        val token = try { readToken() } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            state = state.copy(error = "Could not check your sign-in. Please try again.")
            return false
        }
        if (token != null && token != originalToken) { changedSession(); return false }
        return true
    }

    private fun changedSession() {
        pollJob?.cancel(); pollJob = null
        receipt = null
        state = state.copy(password = "", phase = DeletionPhase.IDLE, sessionChanged = true, error = SESSION_CHANGED,
            exitRequested = false, sessionExpired = false)
    }

    fun leave() {
        generation++
        requestJob?.cancel(); requestJob = null
        pollJob?.cancel(); pollJob = null
        exitJob?.cancel(); exitJob = null
        receipt = null; originalToken = null
        state = DeleteAccountState()
    }

    override fun onCleared() { leave() }

    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == DeleteAccountViewModel::class.java)
            return DeleteAccountViewModel(DeleteAccountRepository(), readToken) as T
        }
    }
}

