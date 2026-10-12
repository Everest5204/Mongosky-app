package com.mongosky.app.notifications

import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class NotificationsController(
    private val scope: CoroutineScope, private val actions: NotificationActions,
    private val events: NotificationEvents, private val readToken: suspend () -> String?,
    private val onAuthenticated: suspend (String, String) -> Unit = { _, _ -> },
    private val onExpired: () -> Unit = {}
) {
    private val mutable = MutableStateFlow(NotificationsState())
    val state = mutable.asStateFlow()
    private var identity: String? = null
    private var token: String? = null
    private var epoch = 0L
    private var revision = 0L
    private var streamVersion = 0L
    private var pageVersion = 0L
    private var listVersion = 0L
    private var foreground = false
    private var cursor: String? = null
    private val seenCursors = HashSet<String>()
    private var sessionJob: Job? = null
    private var streamJob: Job? = null
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var readJob: Job? = null
    private var badgeJob: Job? = null
    private val readQueue = LinkedHashSet<String>()
    private var serverUnreadCount = 0L
    // IDs included in the last server count but already seen locally.
    private val countedReadIds = HashSet<String>()
    private val confirmedReadIds = LinkedHashSet<String>()
    private val badgeCache = LinkedHashMap<String, Boolean>()

    fun startSession(key: String) {
        if (identity == key && (token != null || sessionJob?.isActive == true)) return
        val active = foreground; endSession(); foreground = active; identity = key
        val run = epoch
        sessionJob = scope.launch {
            try {
                val saved = readToken()
                if (run != epoch) return@launch
                if (saved.isNullOrBlank()) { expire(); return@launch }
                token = saved
                onAuthenticated(key, saved)
                if (run != epoch) return@launch
                mutable.value = mutable.value.copy(ready = true)
                if (foreground) { refresh(); restartStream() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (run == epoch) mutable.value = mutable.value.copy(error = "Could not read your saved session. Please try again.") }
        }
    }
    fun endSession() {
        ++epoch; ++revision; ++streamVersion; ++pageVersion; ++listVersion
        sessionJob?.cancel(); streamJob?.cancel(); listJob?.cancel(); moreJob?.cancel(); readJob?.cancel(); badgeJob?.cancel()
        sessionJob = null; listJob = null; moreJob = null; readJob = null; badgeJob = null
        identity = null; token = null; foreground = false; cursor = null
        readQueue.clear(); countedReadIds.clear(); confirmedReadIds.clear(); serverUnreadCount = 0
        seenCursors.clear(); badgeCache.clear(); mutable.value = NotificationsState()
    }
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value && token != null) { refresh(); restartStream() }
        else if (!value) {
            ++streamVersion; ++pageVersion; ++revision; ++listVersion
            streamJob?.cancel(); listJob?.cancel(); moreJob?.cancel(); badgeJob?.cancel()
            listJob = null; moreJob = null; badgeJob = null
            mutable.value = mutable.value.copy(loading = false, loadingMore = false, connection = NotificationConnection.CLOSED)
        }
    }
    fun refresh() {
        val auth = token ?: return
        if (!foreground || listJob?.isActive == true) return
        val run = epoch; val version = revision; val read = ++listVersion
        cancelMore()
        mutable.value = mutable.value.copy(loading = true, error = null, moreError = null)
        listJob = scope.launch {
            try {
                val page = actions.list(auth)
                if (current(run, auth) && foreground && read == listVersion && version == revision) { ++revision; applyFirst(page, false) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (current(run, auth) && foreground && read == listVersion) failed(error) { it.copy(error = "Couldn't refresh notifications. Please try again.") } }
            finally { if (current(run, auth) && read == listVersion) mutable.value = mutable.value.copy(loading = false) }
        }
        if (mutable.value.connection == NotificationConnection.ERROR) restartStream()
    }
    private fun restartStream() {
        val auth = token ?: return
        if (!foreground) return
        val run = epoch; val stream = ++streamVersion
        streamJob?.cancel()
        streamJob = scope.launch {
            try {
                events.frames(auth).collect { frame ->
                    if (!current(run, auth) || stream != streamVersion || !foreground) return@collect
                    when (frame) {
                        is NotificationFrame.Connection -> {
                            if (frame.status == NotificationConnection.UNAUTHORIZED) expire()
                            else mutable.value = mutable.value.copy(connection = frame.status)
                        }
                        is NotificationFrame.Snapshot -> {
                            val reset = mutable.value.loading
                            ++listVersion; listJob?.cancel(); listJob = null; ++revision
                            applyFirst(frame.page, !reset)
                            mutable.value = mutable.value.copy(loading = false, connection = NotificationConnection.CONNECTED)
                        }
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (current(run, auth) && stream == streamVersion) failed(error) { it.copy(connection = NotificationConnection.ERROR) } }
        }
    }
    private fun applyFirst(page: NotificationPage, preserve: Boolean) {
        val merged = NotificationPolicy.firstPage(page, mutable.value.items, preserve)
            .map { if (it.id in confirmedReadIds && !it.isRead) it.copy(isRead = true) else it }
        val head = merged.take(page.items.size).associateBy { it.id }
        val incomingIds = page.items.mapTo(HashSet()) { it.id }
        // A snapshot can precede an in-flight write. Do not restore a seen badge,
        // or subtract an ID which that snapshot already reports as read.
        countedReadIds.retainAll(mutable.value.pendingReadIds - incomingIds)
        page.items.filter { !it.isRead && (head[it.id]?.isRead == true || it.id in mutable.value.pendingReadIds) }
            .forEach { countedReadIds += it.id }
        serverUnreadCount = page.unreadCount
        val failedReads = mutable.value.failedReadIds - merged.filter { it.isRead }.mapTo(HashSet()) { it.id }
        val tailRetained = merged.size > page.items.size
        if (!tailRetained) { cancelMore(); cursor = page.cursor; seenCursors.clear() }
        mutable.value = mutable.value.copy(items = merged, unreadCount = displayUnreadCount(), loaded = true,
            hasMore = if (tailRetained) mutable.value.hasMore else page.hasMore, error = null, moreError = null,
            failedReadIds = failedReads, readError = if (failedReads.isEmpty()) null else mutable.value.readError)
    }
    fun loadMore() {
        val auth = token ?: return; val position = cursor ?: return
        if (!foreground || !mutable.value.hasMore || mutable.value.loading || moreJob?.isActive == true) return
        val run = epoch; val version = ++pageVersion
        mutable.value = mutable.value.copy(loadingMore = true, moreError = null)
        moreJob = scope.launch {
            try {
                val page = actions.list(auth, position)
                if (!current(run, auth) || version != pageVersion || !foreground) return@launch
                if (page.hasMore && (page.cursor == position || page.cursor in seenCursors)) error("Repeated cursor")
                seenCursors += position; cursor = page.cursor
                val existing = mutable.value.items.associateBy { it.id }
                val rows = page.items.filter { it.id !in existing }
                    .map { if (it.id in confirmedReadIds && !it.isRead) it.copy(isRead = true) else it }
                mutable.value = mutable.value.copy(items = mutable.value.items + rows, hasMore = page.hasMore, moreError = null)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (current(run, auth) && version == pageVersion) failed(error) { it.copy(moreError = "Couldn't load older notifications. Please try again.") } }
            finally { if (current(run, auth) && version == pageVersion) mutable.value = mutable.value.copy(loadingMore = false) }
        }
    }
    fun markRead(id: String) {
        queueReads(listOf(id))
    }
    fun markVisibleRead(ids: List<String>): List<String> {
        if (!foreground) return emptyList()
        val unread = mutable.value.items.filter { !it.isRead }.mapTo(HashSet()) { it.id }
        return queueReads(ids.filter { it in unread && it !in mutable.value.failedReadIds })
    }
    fun retryReads() { queueReads(mutable.value.failedReadIds.toList()) }
    private fun displayUnreadCount() = (serverUnreadCount - countedReadIds.size).coerceAtLeast(0)
    private fun queueReads(candidates: List<String>): List<String> {
        val auth = token ?: return emptyList()
        val previous = mutable.value
        val alreadyRead = previous.items.filter { it.isRead }.mapTo(HashSet()) { it.id }
        val ids = candidates.distinct().filter { NotificationPolicy.validId(it) && it !in previous.pendingReadIds && it !in alreadyRead && it !in confirmedReadIds }
        if (ids.isEmpty()) return emptyList()
        val knownUnread = previous.items.filter { !it.isRead }.mapTo(HashSet()) { it.id }
        readQueue.addAll(ids)
        countedReadIds.addAll(ids.filter { it in knownUnread })
        val failedReads = previous.failedReadIds - ids.toSet()
        mutable.value = previous.copy(pendingReadIds = previous.pendingReadIds + ids, unreadCount = displayUnreadCount(),
            failedReadIds = failedReads, readError = if (failedReads.isEmpty()) null else previous.readError)
        if (readJob?.isActive == true) return ids
        val run = epoch
        readJob = scope.launch {
            while (readQueue.isNotEmpty() && current(run, auth)) {
                val ids = readQueue.take(100); readQueue.removeAll(ids.toSet()); val version = revision
                try {
                    val result = actions.markRead(auth, ids)
                    if (!current(run, auth)) return@launch
                    val remaining = mutable.value.pendingReadIds - ids.toSet()
                    if (version == revision) {
                        serverUnreadCount = result.unreadCount
                        countedReadIds.retainAll(remaining)
                    }
                    confirmedReadIds.addAll(ids)
                    while (confirmedReadIds.size > 512) confirmedReadIds.remove(confirmedReadIds.first())
                    ++revision; ++listVersion; listJob?.cancel(); listJob = null; cancelMore()
                    mutable.value = mutable.value.copy(items = mutable.value.items.map { if (it.id in ids) it.copy(isRead = true) else it },
                        unreadCount = displayUnreadCount(), pendingReadIds = remaining, loading = false,
                        readError = if (mutable.value.failedReadIds.isEmpty()) null else mutable.value.readError)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    if (current(run, auth)) {
                        countedReadIds.removeAll(ids.toSet())
                        failed(error) {
                            val confirmed = it.items.filter { row -> row.isRead }.mapTo(HashSet()) { row -> row.id }
                            val failures = it.failedReadIds + (ids.toSet() - confirmed)
                            it.copy(unreadCount = displayUnreadCount(), failedReadIds = failures,
                                readError = if (failures.isEmpty()) null else "Couldn't save seen notifications. Check your connection and retry.")
                        }
                    }
                }
                finally { if (current(run, auth)) mutable.value = mutable.value.copy(pendingReadIds = mutable.value.pendingReadIds - ids.toSet()) }
            }
            if (current(run, auth) && foreground) {
                // REST reconciliation also works when the live stream is unavailable.
                // Replace the old subscription so its buffered pre-write frames cannot win.
                restartStream(); refresh()
            }
        }
        return ids
    }
    fun visibleAuthors(ids: List<String>) {
        val auth = token ?: return
        if (!foreground || badgeJob?.isActive == true) return
        val batch = ids.distinct().filter { NotificationPolicy.validId(it) && it !in badgeCache }.take(64)
        if (batch.isEmpty()) return
        val run = epoch
        badgeJob = scope.launch {
            try {
                val verified = actions.verified(auth, batch)
                if (!current(run, auth) || !foreground) return@launch
                batch.forEach { badgeCache[it] = it in verified }
                while (badgeCache.size > 400) badgeCache.remove(badgeCache.keys.first())
                mutable.value = mutable.value.copy(verifiedAuthors = badgeCache.filterValues { it }.keys.toSet())
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (current(run, auth)) failed(error) { it } }
        }
    }
    private fun cancelMore() { ++pageVersion; moreJob?.cancel(); moreJob = null; mutable.value = mutable.value.copy(loadingMore = false) }
    private fun current(run: Long, auth: String) = run == epoch && token == auth
    private fun failed(error: Exception, patch: (NotificationsState) -> NotificationsState) {
        if ((error as? FeedApiException)?.failure == FeedApiFailure.SESSION_EXPIRED) expire() else mutable.value = patch(mutable.value)
    }
    private fun expire() { endSession(); onExpired(); mutable.value = NotificationsState(connection = NotificationConnection.UNAUTHORIZED) }
}
