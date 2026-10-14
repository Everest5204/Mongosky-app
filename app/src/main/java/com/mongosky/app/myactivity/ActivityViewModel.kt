package com.mongosky.app.myactivity

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One account-bound state holder. All late responses are checked before use. */
internal class ActivityViewModel(private val readToken: suspend () -> String?, private val repository: ActivityRepository = ActivityApi()) : ViewModel() {
    var state by mutableStateOf(ActivityState()); private set
    private val mutationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mutations = mutationEvents.asSharedFlow()
    private var owner = ""; private var revision = Long.MIN_VALUE; private var epoch = 0L; private var queryVersion = 0L; private var targetVersion = 0L
    private var token: String? = null; private var cursor: String? = null
    private var sessionJob: Job? = null; private var pageJob: Job? = null; private var targetJob: Job? = null
    private var mutationJob: Job? = null; private var liveJob: Job? = null; private var pollJob: Job? = null
    private var foreground = false; private var browsing = false

    fun enter(ownerId: String, sessionRevision: Long) {
        if (owner == ownerId && revision == sessionRevision && (token != null || sessionJob?.isActive == true || state.sessionExpired)) return
        endSession(); owner = ownerId; revision = sessionRevision
        if (!ActivityPolicy.validId(ownerId)) { expired(); return }
        restore()
    }
    private fun restore() {
        val expected = epoch; sessionJob?.cancel()
        state = state.copy(loading = state.items.isEmpty(), error = null)
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken(); ensureActive(); if (expected != epoch) return@launch
                if (saved.isNullOrBlank()) { expired(); return@launch }
                token = saved; refresh(); startLive()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (expected == epoch) state = state.copy(loading = false, error = "Could not restore your session. Try again.") }
        }
    }
    fun endSession() {
        epoch++; queryVersion++; targetVersion++; listOf(sessionJob, pageJob, targetJob, mutationJob, liveJob, pollJob).forEach { it?.cancel() }
        sessionJob = null; pageJob = null; targetJob = null; mutationJob = null; liveJob = null; pollJob = null
        token = null; cursor = null; owner = ""; revision = Long.MIN_VALUE; state = ActivityState(); browsing = false
    }
    fun select(category: ActivityCategory) = applyFilter(state.filter.copy(category = category))
    fun select(range: ActivityRange) = applyFilter(state.filter.withRange(range))
    private fun applyFilter(filter: ActivityFilter) {
        if (state.saving || state.sessionExpired || state.filter == filter) return
        pageJob?.cancel(); queryVersion++; cursor = null
        state = state.copy(filter = filter, items = emptyList(), hasMore = false, error = null, pageError = null, updatesAvailable = false)
        refresh()
    }
    fun refresh() {
        if (state.sessionExpired || state.saving) return
        if (token == null) { if (sessionJob?.isActive != true) restore(); return }
        pageJob?.cancel(); queryVersion++; cursor = null
        state = state.copy(loading = state.items.isEmpty(), refreshing = state.items.isNotEmpty(), loadingMore = false,
            error = null, pageError = null, updatesAvailable = false)
        load(false)
    }
    fun loadMore() {
        if (token == null || pageJob?.isActive == true || !state.hasMore || cursor == null || state.sessionExpired || state.saving || state.target != null) return
        state = state.copy(loadingMore = true, pageError = null); load(true)
    }
    private fun load(append: Boolean) {
        val saved = token ?: return; val account = epoch; val query = queryVersion; val filter = state.filter; val next = if (append) cursor else null
        pageJob = viewModelScope.launch {
            try {
                val page = repository.page(saved, owner, filter, next); ensureActive()
                if (account != epoch || query != queryVersion) return@launch
                // A cursor page cannot jump ahead of the last visible row.
                if (append && page.items.isNotEmpty() && state.items.isNotEmpty() && !ActivityPolicy.ordered(state.items.last(), page.items.first())) {
                    throw FeedApiException("Activity changed. Pull down to refresh.", FeedApiFailure.INVALID_RESPONSE)
                }
                val items = if (append) (state.items + page.items).distinctBy { it.key } else page.items
                cursor = page.cursor
                state = state.copy(items = items, counts = page.counts ?: state.counts, hasMore = page.hasMore,
                    loading = false, refreshing = false, loadingMore = false, error = null, pageError = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (account != epoch || query != queryVersion) return@launch
                if (sessionFailure(e)) return@launch
                val needsRefresh = append && e is FeedApiException && (e.statusCode == 400 || e.failure == FeedApiFailure.INVALID_RESPONSE)
                if (needsRefresh) cursor = null
                state = state.copy(loading = false, refreshing = false, loadingMore = false,
                    error = if (append) state.error else message(e),
                    hasMore = if (needsRefresh) false else state.hasMore,
                    updatesAvailable = state.updatesAvailable || needsRefresh,
                    pageError = if (needsRefresh) "Refresh your activity to load more." else if (append) message(e) else null)
            }
        }
    }
    fun open(item: ActivityItem) {
        val saved = token ?: return
        if (!item.available || state.saving || state.sessionExpired) return
        targetJob?.cancel(); val account = epoch; val request = ++targetVersion
        state = state.copy(targetLoading = item.key, target = null)
        targetJob = viewModelScope.launch {
            try {
                val target = repository.target(saved, owner, item); ensureActive()
                if (account == epoch && request == targetVersion) state = state.copy(target = target, targetLoading = null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (account == epoch && request == targetVersion && !sessionFailure(e)) state = state.copy(targetLoading = null, notice = message(e)) }
        }
    }
    fun closeTarget() { targetVersion++; targetJob?.cancel(); state = state.copy(target = null, targetLoading = null) }
    fun askToRemove(item: ActivityItem) {
        if (state.saving || state.sessionExpired || item.action == ActivityAction.NONE || item.action != ActivityPolicy.allowedAction(item.kind)) return
        if (state.items.none { it.key == item.key }) return
        state = state.copy(confirmation = item, actionError = null)
    }
    fun dismissConfirmation() { if (!state.saving) state = state.copy(confirmation = null, actionError = null) }
    fun confirmRemove() {
        val saved = token ?: return; val item = state.confirmation ?: return
        if (state.saving || state.sessionExpired) return
        val account = epoch; pageJob?.cancel(); queryVersion++
        state = state.copy(saving = true, actionError = null, loading = false, refreshing = false, loadingMore = false)
        mutationJob = viewModelScope.launch {
            try {
                repository.remove(saved, item); ensureActive(); if (account != epoch) return@launch
                state = state.copy(saving = false, confirmation = null, items = state.items.filterNot { it.key == item.key }, notice = "Activity removed.")
                mutationEvents.tryEmit(Unit); refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (account != epoch || sessionFailure(e)) return@launch
                // Do not replay a write after a lost response. Reconcile the list.
                state = state.copy(saving = false, confirmation = null, notice = "Could not confirm the change. Your activity is being refreshed.")
                mutationEvents.tryEmit(Unit); refresh()
            }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun setBrowsing(value: Boolean) { browsing = value }
    fun setForeground(value: Boolean) {
        foreground = value
        if (value) { startLive(); if (token != null) checkForUpdates() }
        else { liveJob?.cancel(); pollJob?.cancel(); liveJob = null; pollJob = null; targetVersion++; targetJob?.cancel(); state = state.copy(targetLoading = null) }
    }
    fun checkForUpdates() {
        if (!foreground || state.sessionExpired || token == null || state.saving || state.target != null || state.confirmation != null || pageJob?.isActive == true) return
        val saved = token ?: return; val account = epoch; val query = queryVersion; val filter = state.filter
        pageJob = viewModelScope.launch {
            try {
                val page = repository.page(saved, owner, filter, null); ensureActive()
                if (account != epoch || query != queryVersion) return@launch
                val changed = page.items != state.items.take(20) || page.counts != state.counts
                if (browsing && state.items.isNotEmpty()) {
                    if (changed) state = state.copy(updatesAvailable = true)
                } else if (changed || state.loading || state.error != null) {
                    cursor = page.cursor
                    state = state.copy(items = page.items, counts = page.counts ?: state.counts, hasMore = page.hasMore, loading = false,
                        refreshing = false, loadingMore = false, error = null, pageError = null, updatesAvailable = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (account == epoch && query == queryVersion && !sessionFailure(e) && state.items.isEmpty())
                    state = state.copy(loading = false, error = message(e))
            }
        }
    }
    private fun startLive() {
        val saved = token ?: return
        if (!foreground || state.sessionExpired) return
        val account = epoch
        if (liveJob?.isActive != true) liveJob = viewModelScope.launch {
            var backoff = 1000L
            while (isActive && account == epoch && foreground) {
                try {
                    repository.changes(saved, owner).collect { ensureActive(); if (account == epoch) { checkForUpdates(); backoff = 1000L } }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (account != epoch || sessionFailure(e)) break }
                delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
        if (pollJob?.isActive != true) pollJob = viewModelScope.launch {
            while (isActive && account == epoch && foreground) { delay(20_000); checkForUpdates() }
        }
    }
    private fun sessionFailure(e: Exception): Boolean {
        if (e is FeedApiException && e.failure == FeedApiFailure.SESSION_EXPIRED) { expired(); return true }; return false
    }
    private fun expired() {
        epoch++; queryVersion++; listOf(pageJob, targetJob, mutationJob, liveJob, pollJob).forEach { it?.cancel() }
        token = null; cursor = null; state = ActivityState(loading = false, sessionExpired = true, error = "Your session has expired. Please sign in again.")
    }
    private fun message(e: Exception) = (e as? FeedApiException)?.message ?: "Could not load your activity. Please try again."
    override fun onCleared() { endSession() }
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val tokens = TokenStore(context.applicationContext)
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActivityViewModel::class.java)); return ActivityViewModel(tokens::read) as T
        }
    }
}
