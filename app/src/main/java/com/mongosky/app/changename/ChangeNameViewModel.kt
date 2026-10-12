package com.mongosky.app.changename

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ChangeNameViewModel(private val source: NameSource) : ViewModel() {
    var state by mutableStateOf(ChangeNameState())
        private set
    private var request: Job? = null
    private var ticker: Job? = null
    fun foreground(active: Boolean) {
        ticker?.cancel()
        if (active) {
            refresh()
            ticker = viewModelScope.launch {
                while (true) { state = state.copy(now = Instant.now()); delay(1000) }
            }
        }
    }
    fun first(value: String) { if (!state.locked && !state.saving) state = state.copy(first = value, error = null, success = false) }
    fun last(value: String) { if (!state.locked && !state.saving) state = state.copy(last = value, error = null, success = false) }
    fun refresh() {
        if (request?.isActive == true || state.expired) return
        state = state.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val result = source.load()
                val keepDraft = state.changed && result == state.status
                state = state.copy(status = result, first = if (keepDraft) state.first else result.first,
                    last = if (keepDraft) state.last else result.last, now = Instant.now())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e) }
            finally { state = state.copy(loading = false) }
        }
    }
    fun save() {
        if (!state.canSave || request?.isActive == true) return
        val first = normalizeName(state.first); val last = normalizeName(state.last)
        state = state.copy(saving = true, error = null, success = false)
        request = viewModelScope.launch {
            try {
                val result = source.save(first, last)
                state = state.copy(status = result, first = result.first, last = result.last,
                    success = true, now = Instant.now(), revision = state.revision + 1)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(e) }
            finally { state = state.copy(saving = false) }
        }
    }
    private fun failed(e: Exception) {
        val failure = e as? NameRequestException
        state = state.copy(error = e.message ?: "Unable to process name request.", expired = failure?.status == 401,
            status = if (failure?.next != null) state.status?.copy(canChange = false, next = failure.next) else state.status)
    }
    fun reset() { request?.cancel(); ticker?.cancel(); state = ChangeNameState() }
    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ChangeNameViewModel::class.java)
            return ChangeNameViewModel(ChangeNameRepository(readToken)) as T
        }
    }
}
