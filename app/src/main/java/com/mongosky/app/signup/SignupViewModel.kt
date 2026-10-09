package com.mongosky.app.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.AuthValidation
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class SignupUiState(
    val loading: Boolean = false, val error: String? = null,
    val createdName: String? = null, val outcomeUnknown: Boolean = false
)

class SignupViewModel(private val api: SignupDataSource = SignupApi()) : ViewModel() {
    var uiState by mutableStateOf(SignupUiState())
        private set
    // Memory only: rotation keeps the draft; passwords never enter Bundle/SavedStateHandle/disk.
    var passwordDraft by mutableStateOf("")
        private set
    private var job: Job? = null
    private var generation = 0

    fun updatePassword(value: String) { if (!uiState.loading) passwordDraft = value }
    fun clearError() { if (!uiState.loading && !uiState.outcomeUnknown) uiState = uiState.copy(error = null) }
    fun reset() { if (!uiState.loading) endSession() }

    fun signup(request: SignupRequest, policyAccepted: Boolean) {
        if (uiState.loading || uiState.createdName != null || uiState.outcomeUnknown) return
        val validation = if (!policyAccepted) "Accept the Privacy Policy to continue." else AuthValidation.signup(request)
        if (validation != null) { uiState = SignupUiState(error = validation); return }
        uiState = SignupUiState(loading = true)
        val attempt = generation
        val work = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = api.signup(request)
                ensureActive()
                if (attempt != generation) return@launch
                val name = "${result.firstName} ${result.lastName}".trim()
                    .ifBlank { "${request.firstName.trim()} ${request.lastName.trim()}".trim() }
                passwordDraft = ""
                uiState = SignupUiState(createdName = name)
            } catch (error: CancellationException) { throw error }
            catch (error: SignupException) {
                if (attempt == generation) uiState = SignupUiState(
                    error = error.message ?: "Could not create account.", outcomeUnknown = error.outcomeUnknown)
            } catch (_: IOException) {
                if (attempt == generation) uiState = SignupUiState(error = UNCERTAIN, outcomeUnknown = true)
            } catch (_: Exception) {
                if (attempt == generation) uiState = SignupUiState(error = UNCERTAIN, outcomeUnknown = true)
            } finally { if (job === coroutineContext[Job]) job = null }
        }
        job = work; work.start()
    }

    /** Exit/logout cancels this client request and prevents its late result entering a different account's screen. */
    fun endSession() {
        generation++; job?.cancel(); job = null
        passwordDraft = ""; uiState = SignupUiState()
    }
    override fun onCleared() { endSession(); super.onCleared() }
    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SignupViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SignupViewModel() as T
        }
    }
    private companion object {
        const val UNCERTAIN = "Could not confirm account creation. Try signing in before creating it again."
    }
}
