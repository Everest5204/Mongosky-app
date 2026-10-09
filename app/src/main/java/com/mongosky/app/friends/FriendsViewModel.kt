package com.mongosky.app.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Account-scoped caches. Only the visible, resumed route polls; mutations are server-confirmed. */
class FriendsViewModel(
    private val readToken: suspend () -> String?,
    private val repository: FriendsRepository = LiveFriendsRepository(),
    private val refreshIntervalMs: Long = FriendsPolicy.REFRESH_INTERVAL_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    var uiState by mutableStateOf(FriendsUiState())
        private set
    private var token: String? = null
    private var identity: String? = null
    private var foreground = false
    private var started = false
    private var sessionVersion = 0L
    private var readVersion = 0L
    private var badgeVersion = 0L
    private var nextAllowedAt = 0L
    private var tokenJob: Job? = null
    private var readJob: Job? = null
    private var pollJob: Job? = null
    private var badgeJob: Job? = null
    private var badgeRequestIds: Set<String> = emptySet()
    private val mutationJobs = mutableMapOf<String, Job>()
    private data class Badge(val verified: Boolean, val checkedAt: Long)
    private val badges = LinkedHashMap<String, Badge>()

    fun startSession(key: String) {
        if (identity == key && (started || uiState.sessionExpired)) return
        if (identity != null && identity != key) {
            val visible = foreground
            endSession()
            foreground = visible
        }
        identity = key
        started = true
        val version = ++sessionVersion
        tokenJob?.cancel()
        tokenJob = viewModelScope.launch {
            try {
                val saved = readToken()?.takeIf(String::isNotBlank)
                if (version != sessionVersion) return@launch
                if (saved == null) { expireSession(); return@launch }
                token = saved
                if (foreground) { refresh(); startPolling() }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == sessionVersion) {
                    started = false
                    updateList(uiState.activeTab) { it.copy(error = "Could not read your saved session. Please try again.") }
                }
            }
        }
    }

    fun endSession() {
        ++sessionVersion
        tokenJob?.cancel()
        cancelReads()
        pollJob?.cancel()
        mutationJobs.values.toList().forEach(Job::cancel)
        mutationJobs.clear()
        badges.clear()
        token = null
        identity = null
        started = false
        foreground = false
        nextAllowedAt = 0L
        uiState = FriendsUiState()
    }

    fun setForeground(value: Boolean) {
        if (value == foreground) return
        foreground = value
        if (value && !uiState.sessionExpired) {
            if (!started) identity?.let(::startSession)
            if (token != null) { refresh(); startPolling() }
        } else {
            cancelReads()
            pollJob?.cancel()
            // An already submitted follow request may finish (15s deadline). No background reads follow it.
        }
    }

    fun selectTab(tab: FriendsTab) {
        if (tab == uiState.activeTab) return
        cancelReads()
        uiState = uiState.copy(activeTab = tab, message = null)
        refresh()
    }

    fun refresh() {
        if (!foreground || uiState.sessionExpired) return
        if (token == null) { if (!started) identity?.let(::startSession); return }
        if (uiState.pendingIds.isNotEmpty()) return
        requestRefresh(checkHeadOnly = false)
    }

    fun loadMore() {
        val activeToken = token ?: return
        val tab = uiState.activeTab
        val old = uiState.list(tab)
        val cursor = old.nextCursor ?: return
        if (!foreground || uiState.sessionExpired || !old.hasMore || old.refreshing || old.loadingMore ||
            uiState.pendingIds.isNotEmpty() || !allowed(tab)) return
        if (cursor in old.visitedCursors) {
            updateList(tab) { it.copy(moreError = "Could not load the next page. Refresh the list to try again.") }
            return
        }
        cancelReads()
        val version = readVersion
        val session = sessionVersion
        updateList(tab) { it.copy(loadingMore = true, moreError = null) }
        readJob = viewModelScope.launch {
            try {
                val page = repository.page(activeToken, tab, cursor)
                if (!current(version, session, tab)) return@launch
                if (page.hasMore && (page.nextCursor == cursor || page.nextCursor in old.visitedCursors)) throw invalidPage()
                val users = unique(old.users + page.users).map(::withBadge)
                uiState = uiState.withList(tab, old.copy(
                    users = users, loadingMore = false, moreError = null, error = null,
                    loadedPages = old.loadedPages + 1, nextCursor = page.nextCursor, hasMore = page.hasMore,
                    visitedCursors = old.visitedCursors + cursor
                )).copy(followersCount = page.followersCount, followingCount = page.followingCount,
                    countsLoaded = true, uncertainIds = uiState.uncertainIds - page.users.map(FriendsUser::id).toSet())
                resolveVisibleBadges(page.users.map(FriendsUser::id))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current(version, session, tab) && !handleSession(error)) {
                    noteCooldown(error)
                    updateList(tab) { it.copy(loadingMore = false, moreError = message(error)) }
                }
            }
        }
    }

    fun toggleFollow(id: String) {
        val activeToken = token ?: return
        val row = uiState.activeList.users.firstOrNull { it.id == id } ?: return
        if (!foreground || uiState.sessionExpired || row.isSelf || FriendsPolicy.userId(id) != id ||
            id in uiState.pendingIds || id in uiState.uncertainIds || uiState.activeList.refreshing || !allowed(uiState.activeTab)) return
        cancelReads() // An earlier GET cannot undo a newly confirmed follow state.
        val session = sessionVersion
        val desired = !row.isFollowing
        uiState = uiState.copy(pendingIds = uiState.pendingIds + id, message = null)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.setFollowing(activeToken, id, desired)
                if (session != sessionVersion) return@launch
                if (result.isFollowing != desired) throw invalidPage()
                for (tab in FriendsTab.entries) {
                    updateList(tab) { list ->
                        fun update(rows: List<FriendsUser>) = rows.map { if (it.id == id) it.copy(isFollowing = result.isFollowing) else it }
                            .filterNot { tab == FriendsTab.FOLLOWING && it.id == id && !result.isFollowing }
                        list.copy(users = update(list.users), headUsers = update(list.headUsers))
                    }
                }
                // The mutation's followers_count is deliberately ignored; the next GET owns viewer counts.
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && !handleSession(error)) {
                    noteCooldown(error)
                    val ambiguous = (error as? FriendsException)?.failure in setOf(
                        FriendsFailure.NETWORK, FriendsFailure.TIMEOUT, FriendsFailure.SERVER, FriendsFailure.INVALID_RESPONSE)
                    uiState = uiState.copy(
                        uncertainIds = if (ambiguous) uiState.uncertainIds + id else uiState.uncertainIds,
                        message = if (ambiguous) "Could not confirm this change. Refresh to check the current follow state." else message(error)
                    )
                }
            } finally {
                if (session == sessionVersion) {
                    mutationJobs.remove(id)
                    uiState = uiState.copy(pendingIds = uiState.pendingIds - id)
                    if (foreground && uiState.pendingIds.isEmpty() && !uiState.sessionExpired) refresh()
                }
            }
        }
        mutationJobs[id] = job
        job.start()
    }

    fun dismissMessage() { uiState = uiState.copy(message = null) }

    /** Resolve just visible/new rows in batches; optional badge failure never blocks the list. */
    fun resolveVisibleBadges(visibleIds: List<String>) {
        val activeToken = token ?: return
        if (!foreground || uiState.sessionExpired || nextAllowedAt > nowMs()) return
        val ids = visibleIds.distinct().filter { id ->
            FriendsPolicy.userId(id) == id && (badges[id]?.let { nowMs() - it.checkedAt >= FriendsPolicy.BADGE_TTL_MS } ?: true)
        }.take(FriendsPolicy.PAGE_SIZE)
        if (ids.isEmpty()) return
        if (badgeJob?.isActive == true && badgeRequestIds.containsAll(ids)) return
        badgeJob?.cancel()
        badgeRequestIds = ids.toSet()
        val version = ++badgeVersion
        val session = sessionVersion
        badgeJob = viewModelScope.launch {
            try {
                val verified = repository.verifiedIds(activeToken, ids)
                if (!foreground || session != sessionVersion || version != badgeVersion) return@launch
                ids.forEach { badges.remove(it); badges[it] = Badge(it in verified, nowMs()) }
                while (badges.size > FriendsPolicy.BADGE_CACHE_LIMIT) badges.remove(badges.keys.first())
                for (tab in FriendsTab.entries) updateList(tab) { it.copy(users = it.users.map(::withBadge)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && version == badgeVersion) { handleSession(error); noteCooldown(error) }
            } finally {
                if (version == badgeVersion) badgeRequestIds = emptySet()
            }
        }
    }

    private fun requestRefresh(checkHeadOnly: Boolean) {
        val activeToken = token ?: return
        val tab = uiState.activeTab
        if (!allowed(tab)) return
        cancelReads()
        val old = uiState.list(tab)
        val version = readVersion
        val session = sessionVersion
        updateList(tab) { it.copy(refreshing = true, error = null, moreError = null) }
        readJob = viewModelScope.launch {
            try {
                var page = repository.page(activeToken, tab)
                if (!current(version, session, tab)) return@launch
                val head = page.users.map { it.copy(verified = false) }
                val countsUnchanged = page.followersCount == uiState.followersCount && page.followingCount == uiState.followingCount
                if (checkHeadOnly && old.loaded && head == old.headUsers && countsUnchanged) {
                    updateList(tab) { it.copy(refreshing = false, error = null, moreError = old.moreError) }
                    resolveVisibleBadges(head.map(FriendsUser::id))
                    return@launch
                }
                val rows = page.users.toMutableList()
                val visited = mutableSetOf<String>()
                var pages = 1
                // Replay the loaded depth only on real changes or explicit refresh. Never truncate a scrolled list to page one.
                while (pages < old.loadedPages && page.hasMore) {
                    val cursor = page.nextCursor ?: throw invalidPage()
                    if (!visited.add(cursor)) throw invalidPage()
                    page = repository.page(activeToken, tab, cursor)
                    if (!current(version, session, tab)) return@launch
                    if (page.hasMore && page.nextCursor in visited) throw invalidPage()
                    rows.addAll(page.users)
                    ++pages
                }
                val users = unique(rows).map(::withBadge)
                uiState = uiState.withList(tab, FriendsListState(
                    users = users, loaded = true, nextCursor = page.nextCursor, hasMore = page.hasMore,
                    loadedPages = pages, visitedCursors = visited, headUsers = head
                )).copy(followersCount = page.followersCount, followingCount = page.followingCount,
                    countsLoaded = true, uncertainIds = uiState.uncertainIds - users.map(FriendsUser::id).toSet())
                resolveVisibleBadges(head.map(FriendsUser::id))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current(version, session, tab) && !handleSession(error)) {
                    noteCooldown(error)
                    updateList(tab) { it.copy(refreshing = false, error = message(error)) }
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (foreground && token != null && !uiState.sessionExpired) {
                delay(refreshIntervalMs.coerceAtLeast(1))
                if (readJob?.isActive != true && uiState.pendingIds.isEmpty() && nextAllowedAt <= nowMs()) requestRefresh(checkHeadOnly = true)
            }
        }
    }

    private fun cancelReads() {
        ++readVersion
        ++badgeVersion
        readJob?.cancel()
        badgeJob?.cancel()
        badgeRequestIds = emptySet()
        for (tab in FriendsTab.entries) updateList(tab) { it.copy(refreshing = false, loadingMore = false) }
    }
    private fun current(version: Long, session: Long, tab: FriendsTab): Boolean =
        version == readVersion && session == sessionVersion && foreground && uiState.activeTab == tab && !uiState.sessionExpired
    private fun updateList(tab: FriendsTab, transform: (FriendsListState) -> FriendsListState) {
        uiState = uiState.withList(tab, transform(uiState.list(tab)))
    }
    private fun unique(rows: List<FriendsUser>): List<FriendsUser> = LinkedHashMap<String, FriendsUser>().apply {
        rows.forEach { putIfAbsent(it.id, it) }
    }.values.toList()
    private fun withBadge(user: FriendsUser): FriendsUser = user.copy(verified = badges[user.id]?.verified ?: user.verified)
    private fun allowed(tab: FriendsTab): Boolean {
        if (nowMs() >= nextAllowedAt) return true
        val seconds = ((nextAllowedAt - nowMs() + 999) / 1_000).coerceAtLeast(1)
        updateList(tab) { it.copy(error = "Please wait $seconds seconds before trying again.") }
        return false
    }
    private fun noteCooldown(error: Exception) {
        if (error is FriendsException && error.failure == FriendsFailure.RATE_LIMITED)
            nextAllowedAt = maxOf(nextAllowedAt, nowMs() + error.retryAfterSeconds.coerceIn(1, 3_600) * 1_000L)
    }
    private fun handleSession(error: Exception): Boolean {
        if (error !is FriendsException || error.failure != FriendsFailure.SESSION_EXPIRED) return false
        expireSession()
        return true
    }
    private fun expireSession() {
        val key = identity
        val tab = uiState.activeTab
        endSession()
        identity = key
        uiState = FriendsUiState(activeTab = tab, sessionExpired = true)
    }
    private fun message(error: Exception): String = (error as? FriendsException)?.message
        ?: "Could not update connections. Please check your connection and try again."
    private fun invalidPage() = FriendsException("Could not refresh this list. Please try again.", FriendsFailure.INVALID_RESPONSE)
    override fun onCleared() { endSession(); super.onCleared() }

    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FriendsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return FriendsViewModel(readToken) as T
        }
    }
}
