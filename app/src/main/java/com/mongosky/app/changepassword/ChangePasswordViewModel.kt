package com.mongosky.app.changepassword

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class ChangePasswordViewModel(private val source: ChangePasswordSource) : ViewModel() {
    var state by mutableStateOf(ChangePasswordState())
        private set
    private var request: Job? = null
    private var generation = 0

    fun update(field: PasswordField, value: String) {
        if (state.busy) return
        val values = when (field) {
            PasswordField.CURRENT -> state.values.copy(current = value)
            PasswordField.NEW -> state.values.copy(new = value)
            PasswordField.CONFIRM -> state.values.copy(confirmation = value)
        }
        state = state.copy(values = values, error = null, success = null)
    }

    fun submit() {
        if (state.busy || request?.isActive == true) return
        val validation = ChangePasswordPolicy.validate(state.values)
        if (validation != null) { state = state.copy(error = validation, success = null); return }
        val values = state.values
        val attempt = ++generation
        state = state.copy(submitting = true, error = null, success = null)
        request = viewModelScope.launch {
            try {
                val message = source.change(values)
                ensureActive()
                if (attempt == generation) state = state.copy(success = message, awaitingAutofill = true,
                    successRevision = state.successRevision + 1)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt == generation) {
                    val failure = e as? ChangePasswordException
                    val expired = failure?.status == 401
                    state = state.copy(error = failure?.message ?: "Unable to change your password. Please try again.",
                        sessionExpired = expired, values = if (expired) PasswordValues() else state.values)
                }
            } finally {
                if (attempt == generation) state = state.copy(submitting = false)
            }
        }
    }

    /** Commit the existing autofill context before emptying the still-mounted fields. */
    fun acknowledgeSuccess(revision: Int) {
        if (state.awaitingAutofill && state.successRevision == revision) {
            state = state.copy(values = PasswordValues(), awaitingAutofill = false)
        }
    }

    fun leave() {
        generation++
        request?.cancel()
        request = null
        state = ChangePasswordState()
    }

    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ChangePasswordViewModel::class.java)
            return ChangePasswordViewModel(ChangePasswordRepository(readToken)) as T
        }
    }
}
