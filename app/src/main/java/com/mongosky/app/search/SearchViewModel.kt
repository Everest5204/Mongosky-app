package com.mongosky.app.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Session-owned state; only the visible, resumed Search route performs network work. */
class SearchViewModel(
    private val readToken: suspend () -> String?,
    private val repository: SearchRepository = LiveSearchRepository(),
    private val refreshIntervalMs: Long = SearchPolicy.REFRESH_INTERVAL_MS,
    private val debounceMs: Long = SearchPolicy.DEBOUNCE_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    var uiState by mutableStateOf(SearchUiState())
        private set
    private var token: String? = null
    private var sessionKey: String? = null
    private var started = false
    private var foreground = false
    private var epoch = 0L
    private var sessionEpoch = 0L
    private var nextAllowedAt = 0L
    private var sessionJob: Job? = null
    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var badgeJob: Job? = null

    fun startSession(identity: String = sessionKey.orEmpty()) {
        if (started && sessionKey == identity) return
        if (sessionKey != null && sessionKey != identity) {
            val wasForeground = foreground
            endSession()
            foreground = wasForeground
        }
        sessionKey = identity
        started = true
        val version = ++sessionEpoch
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                if (version != sessionEpoch) return@launch
                if (saved == null) { expireSession(); return@launch }
                token = saved
                if (foreground) { scheduleSearch(immediate = true); startRefreshing() }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == sessionEpoch) {
                    uiState = uiState.copy(status = SearchStatus.ERROR, error = "Could not read your saved session. Please try again.")
                    started = false
                }
            }
        }
    }

    fun endSession() {
        ++epoch
        ++sessionEpoch
        sessionJob?.cancel()
        searchJob?.cancel()
        refreshJob?.cancel()
        badgeJob?.cancel()
        token = null
        sessionKey = null
        started = false
        foreground = false
        nextAllowedAt = 0L
        uiState = SearchUiState()
    }

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        if (visible) {
            if (!started) startSession()
            if (token != null) { scheduleSearch(immediate = true); startRefreshing() }
        } else {
            ++epoch
            searchJob?.cancel()
            refreshJob?.cancel()
            badgeJob?.cancel()
            // A pending token read must be restarted, rather than applying after a route leaves.
            if (token == null) { ++sessionEpoch; sessionJob?.cancel(); started = false }
            uiState = uiState.copy(refreshing = false)
        }
    }

    fun changeQuery(value: String) {
        val limited = SearchPolicy.limitInput(value)
        if (limited == uiState.query) return
        val previous = uiState.normalizedQuery
        uiState = uiState.copy(query = limited)
        if (uiState.normalizedQuery == previous) return
        ++epoch
        searchJob?.cancel()
        badgeJob?.cancel()
        uiState = uiState.copy(
            users = emptyList(), error = null, refreshing = false, lastUpdatedAt = null,
            status = if (uiState.normalizedQuery.isEmpty()) SearchStatus.IDLE else SearchStatus.LOADING
        )
        if (uiState.normalizedQuery.isNotEmpty()) scheduleSearch(immediate = false)
    }

    fun clear() = changeQuery("")

    fun refresh() {
        if (uiState.sessionExpired) return
        if (token == null) { started = false; startSession() }
        else scheduleSearch(immediate = true)
    }

    private fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (foreground && token != null && !uiState.sessionExpired) {
                delay(refreshIntervalMs)
                if (searchJob?.isActive != true && uiState.normalizedQuery.isNotEmpty()) {
                    scheduleSearch(immediate = true)
                }
            }
        }
    }

    private fun scheduleSearch(immediate: Boolean) {
        val activeToken = token ?: return
        val query = uiState.normalizedQuery
        if (!foreground || query.isEmpty() || uiState.sessionExpired) return
        searchJob?.cancel()
        badgeJob?.cancel()
        val version = ++epoch
        val remaining = nextAllowedAt - nowMs()
        if (remaining > 0) {
            uiState = uiState.copy(
                status = if (uiState.lastUpdatedAt == null) SearchStatus.ERROR else SearchStatus.RESULTS,
                refreshing = false, error = "Please wait ${(remaining + 999) / 1_000} seconds before searching again."
            )
            return
        }
        uiState = uiState.copy(
            status = if (uiState.lastUpdatedAt == null) SearchStatus.LOADING else SearchStatus.RESULTS,
            refreshing = immediate && uiState.status == SearchStatus.RESULTS,
            error = null
        )
        searchJob = viewModelScope.launch {
            try {
                if (!immediate) delay(debounceMs)
                val result = repository.search(activeToken, query)
                if (!isCurrent(version, activeToken, query)) return@launch
                val previousBadges = uiState.users.associate { it.id to it.verified }
                val users = result.users.map { it.copy(verified = previousBadges[it.id] == true) }
                uiState = uiState.copy(
                    users = users, status = SearchStatus.RESULTS,
                    refreshing = false, error = null, lastUpdatedAt = nowMs()
                )
                resolveBadges(version, activeToken, query, users)
            } catch (error: CancellationException) { throw error }
            catch (error: IOException) {
                if (isCurrent(version, activeToken, query)) handleFailure(error)
            }
        }
    }

    private fun isCurrent(version: Long, activeToken: String, query: String) =
        foreground && version == epoch && token == activeToken && uiState.normalizedQuery == query

    private fun resolveBadges(version: Long, activeToken: String, query: String, users: List<SearchUser>) {
        if (users.isEmpty()) return
        badgeJob = viewModelScope.launch {
            try {
                val verified = repository.verifiedIds(activeToken, users.map { it.id })
                if (isCurrent(version, activeToken, query)) {
                    uiState = uiState.copy(users = uiState.users.map { it.copy(verified = it.id in verified) })
                }
            } catch (error: CancellationException) { throw error }
            catch (error: IOException) {
                // Optional badge availability must not delay or erase successful people results.
                if (isCurrent(version, activeToken, query)) {
                    if (error is SearchException && error.failure == SearchFailure.SESSION_EXPIRED) expireSession()
                    else uiState = uiState.copy(users = uiState.users.map { it.copy(verified = false) })
                }
            }
        }
    }

    private fun handleFailure(error: IOException) {
        if (error is SearchException) {
            when (error.failure) {
                SearchFailure.SESSION_EXPIRED -> { expireSession(); return }
                SearchFailure.RATE_LIMITED -> nextAllowedAt = nowMs() + error.retryAfterSeconds.coerceAtLeast(1) * 1_000L
                else -> Unit
            }
        }
        uiState = uiState.copy(
            status = if (uiState.lastUpdatedAt == null) SearchStatus.ERROR else SearchStatus.RESULTS,
            refreshing = false, error = error.message ?: "Search unavailable. Please try again."
        )
    }

    private fun expireSession() {
        endSession()
        uiState = SearchUiState(status = SearchStatus.ERROR, sessionExpired = true,
            error = "Your session has expired. Please sign in again.")
    }

    override fun onCleared() { endSession(); super.onCleared() }

    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java)) { "Unknown Search ViewModel" }
            return SearchViewModel(readToken) as T
        }
    }
}
