package com.mongosky.app.deactivateaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Passwords and pending operations stay in memory and never enter saved state. */
internal class DeactivateAccountViewModel(
    private val readToken: suspend () -> String?,
    private val source: DeactivationSource = DeactivateAccountRepository(),
    private val newOperation: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    var state by mutableStateOf(DeactivateAccountState())
        private set
    private var request: Job? = null
    private var active = true
    private var originalToken: String? = null
    private var operationId: String? = null

    fun updatePassword(value: String) {
        if (!active || state.submitting || state.deactivated || state.uncertain) return
        state = state.copy(password = value, passwordError = null, error = null)
        operationId = null
    }
    fun toggleVisibility() {
        if (!state.submitting) state = state.copy(passwordVisible = !state.passwordVisible)
    }
    fun openConfirmation() {
        if (!active || state.submitting || state.deactivated) return
        val error = DeactivateAccountPolicy.passwordError(state.password)
        state = state.copy(passwordError = error, dialogOpen = error == null, error = null)
    }
    fun dismissConfirmation() {
        if (!state.submitting && !state.uncertain) state = state.copy(dialogOpen = false, error = null)
    }
    fun submit() {
        if (!active || !state.dialogOpen || state.submitting || state.deactivated || state.exitRequested) return
        val password = state.password
        val error = DeactivateAccountPolicy.passwordError(password)
        if (error != null) { state = state.copy(passwordError = error, dialogOpen = false); return }
        state = state.copy(submitting = true, error = null, passwordVisible = false)
        val operation = operationId ?: newOperation().also { operationId = it }
        request = viewModelScope.launch {
            var captured: String? = null
            try {
                captured = readToken()
                ensureActive()
                if (captured.isNullOrBlank()) throw DeactivationException("Please sign in again.", 401)
                if (originalToken != null && originalToken != captured) {
                    state = DeactivateAccountState(error = "Your account changed. Open Settings again.")
                    return@launch
                }
                originalToken = captured
                val receipt = source.deactivate(captured, password, operation)
                ensureActive()
                if (!active) return@launch
                if (readToken() != captured) {
                    state = DeactivateAccountState(error = "Your account changed. Open Settings again.")
                    return@launch
                }
                if (receipt.operationId != operation) throw DeactivationException("Deactivation could not be confirmed.", 502)
                state = DeactivateAccountState(deactivated = true, exitRequested = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!active) return@launch
                val current = runCatching { readToken() }.getOrNull()
                ensureActive()
                if (captured != null && current != captured) {
                    state = DeactivateAccountState(error = "Your account changed. Open Settings again.")
                    return@launch
                }
                val status = (error as? DeactivationException)?.status ?: 0
                if (status == 401) {
                    state = DeactivateAccountState(exitRequested = true, error = "Please sign in again.")
                } else {
                    val uncertain = status == 0 || status >= 500
                    state = state.copy(submitting = false, uncertain = uncertain,
                        error = (error as? DeactivationException)?.message ?: "We could not confirm deactivation. Retry the same request.")
                }
            }
        }
    }
    fun enter() { if (!active) { active = true; state = DeactivateAccountState() } }
    fun requestSignOut() {
        if (!state.submitting) state = DeactivateAccountState(exitRequested = true)
    }
    fun leave(): Boolean {
        if (state.submitting || state.deactivated || state.exitRequested) return false
        close()
        return true
    }
    fun close() {
        active = false
        request?.cancel()
        originalToken = null; operationId = null
        state = DeactivateAccountState()
    }
    override fun onCleared() {
        active = false; originalToken = null; operationId = null
        state = DeactivateAccountState()
    }
    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeactivateAccountViewModel(readToken) as T
    }
}
