package com.mongosky.app.reels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.comments.CommentActions
import com.mongosky.app.comments.CommentApi
import com.mongosky.app.comments.CommentsController
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.reactions.ReactionActions
import com.mongosky.app.reactions.ReactionApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Session-owned metadata; one feed read at a time, with independent older/catch-up cursors. */
class ReelsViewModel(
    private val readToken: suspend () -> String?,
    private val api: ReelsDataSource = ReelsApi(),
    private val reactionsApi: ReactionActions = ReactionApi(),
    commentApi: CommentActions = CommentApi()
) : ViewModel() {
    var state by mutableStateOf(ReelsState())
        private set
    internal val loves = mutableStateMapOf<String, ReelLoveState>()
    private var token: String? = null
    private var ownerId: String? = null
    private var session = 0
    private var work = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val scope get() = CoroutineScope(viewModelScope.coroutineContext + work)
    private var tokenJob: Job? = null
    private var readJob: Job? = null
    private var badgeJob: Job? = null
    private var readEpoch = 0
    private var badgeEpoch = 0
    private var foreground = false
    private var olderCursor: String? = null
    private val olderSeen = mutableSetOf<String>()
    private var catchupCursor: String? = null
    private val catchupSeen = mutableSetOf<String>()
    private var loveRevision = 0
    private val loveVersions = mutableMapOf<String, Int>()
    private val seeded = mutableSetOf<String>()
    private val checkedAuthors = mutableSetOf<String>()
    private var badgeCheckedAt = 0L
    private var lastFailedAppend = false
    private var failures = 0
    private var pollAfterNanos = 0L
    internal val comments = CommentsController(commentApi, { token }, { scope },
        { state.sessionExpired }, { session }, ::sessionFailure)

    fun enter(userId: String?) {
        val id = userId?.trim()?.lowercase()
        if (id == ownerId && (token != null || tokenJob?.isActive == true)) return
        endSession()
        if (id == null || !Regex("[a-f0-9]{24}").matches(id)) {
            state = ReelsState(sessionExpired = true, error = "Please sign in again.")
            return
        }
        ownerId = id
        val generation = session
        tokenJob = scope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                if (session != generation) return@launch
                if (saved == null) {
                    state = state.copy(sessionExpired = true, error = "Please sign in again.")
                } else {
                    token = saved
                    if (foreground) refresh()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (generation == session) state = state.copy(error = "Could not read your session. Please sign in again.", sessionExpired = true)
            }
        }
    }

    fun endSession() {
        session++; work.cancel(); work = SupervisorJob(viewModelScope.coroutineContext[Job])
        readEpoch++; badgeEpoch++
        token = null; ownerId = null; tokenJob = null; readJob = null; badgeJob = null
        olderCursor = null; olderSeen.clear(); catchupCursor = null; catchupSeen.clear()
        loves.clear(); loveVersions.clear(); loveRevision = 0; seeded.clear(); checkedAuthors.clear()
        badgeCheckedAt = 0; failures = 0; pollAfterNanos = 0; lastFailedAppend = false
        comments.endSession(); state = ReelsState()
    }

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        if (!visible) {
            readEpoch++; badgeEpoch++
            readJob?.cancel(); readJob = null; badgeJob?.cancel(); badgeJob = null
            comments.closeComments()
            state = state.copy(loading = false, loadingMore = false, syncing = false)
        } else if (token != null && !state.sessionExpired) refresh()
    }

    fun select(id: String) { if (state.reels.any { it.id == id }) state = state.copy(activeId = id) }
    /** A tapped Home thumbnail keeps its identity while the first native page loads. */
    fun openReel(reel: Reel) {
        if (ownerId == null || state.sessionExpired) return
        state = state.copy(reels = (listOf(reel) + state.reels).distinctBy { it.id }
            .sortedWith(compareByDescending<Reel> { it.createdAt }.thenByDescending { it.id }), activeId = reel.id)
    }
    fun refresh() = read(append = false)
    fun retry() = read(append = lastFailedAppend)
    fun loadMore() { if (state.hasMore && catchupCursor == null) read(append = true) }
    fun checkForUpdates() { if (foreground && System.nanoTime() >= pollAfterNanos) refresh() }

    private fun read(append: Boolean) {
        val auth = token ?: return
        if (!foreground || state.sessionExpired || readJob?.isActive == true) return
        val generation = session
        val request = ++readEpoch
        val before = state.reels
        val cursor = if (append) olderCursor else catchupCursor
        val revision = loveRevision
        val savingAtRead = loves.filterValues { it.saving || it.uncertain }.keys.toSet()
        if (append) state = state.copy(loadingMore = true, error = null)
        else state = state.copy(loading = before.isEmpty(), syncing = before.isNotEmpty(), error = null)
        if (!append) comments.onFeedRefresh()
        readJob = scope.launch {
            try {
                val active = state.activeId?.takeIf { !append }
                val (page, activeRow, head) = coroutineScope {
                    val feed = async { api.page(auth, cursor) }
                    val latest = if (!append && cursor != null) async { api.page(auth) } else null
                    val selected = active?.let { id -> async {
                        try { api.reel(auth, id) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (error: FeedApiException) { if (error.statusCode == 404) null else throw error }
                    } }
                    Triple(feed.await(), selected?.await(), latest?.await())
                }
                if (generation != session || request != readEpoch || !foreground) return@launch
                validatePage(page, cursor)
                head?.let { validatePage(it, null) }
                val current = state.reels
                val incoming = (page.reels + head?.reels.orEmpty() + listOfNotNull(activeRow)).distinctBy { it.id }
                incoming.forEach { row ->
                    seeded.remove(row.id)
                    if (row.id !in savingAtRead && loves[row.id]?.saving != true && loves[row.id]?.uncertain != true &&
                        (loveVersions[row.id] ?: 0) <= revision) loves.remove(row.id)
                }
                val currentById = current.associateBy { it.id }
                val touchedExisting = page.reels.any { it.id in currentById && it.id !in seeded }
                val canCatchUp = !append && state.initialized && page.hasMore && current.isNotEmpty() && !touchedExisting
                if (append) {
                    cursor?.let { olderSeen.add(it) }
                    if (page.hasMore && page.nextCursor in olderSeen) throw invalidPage()
                    olderCursor = page.nextCursor
                } else if (canCatchUp) {
                    cursor?.let { catchupSeen.add(it) }
                    if (page.nextCursor in catchupSeen) throw invalidPage()
                    catchupCursor = page.nextCursor
                } else {
                    catchupCursor = null; catchupSeen.clear()
                    if (!state.initialized || current.isEmpty()) { olderCursor = page.nextCursor; olderSeen.clear() }
                }
                // Remove only server-covered deleted rows; older unvisited metadata stays visible.
                fun covers(row: Reel, segment: ReelsPage, fromHead: Boolean): Boolean =
                    (fromHead || segment.reels.firstOrNull()?.let { compareRows(row, it) <= 0 } == true) &&
                        (!segment.hasMore || segment.reels.lastOrNull()?.let { compareRows(row, it) >= 0 } == true)
                val incomingIds = incoming.map { it.id }.toSet()
                val covered = if (append) emptySet() else current.filter { row ->
                    row.id !in seeded && row.id !in incomingIds &&
                        (covers(row, page, cursor == null) || head?.let { covers(row, it, true) } == true)
                }.map { it.id }.toSet()
                val activeDeleted = active != null && activeRow == null
                val rows = (current.filterNot { it.id in covered || activeDeleted && it.id == active } + incoming)
                    .associateBy { it.id }.values.sortedWith(compareByDescending<Reel> { it.createdAt }.thenByDescending { it.id })
                val initial = !state.initialized || current.isEmpty()
                if (!append) comments.onFeedReady()
                state = state.copy(reels = rows, initialized = true, loading = false, loadingMore = false, syncing = false,
                    hasMore = if (initial) page.hasMore else state.hasMore && (if (append) page.hasMore else true),
                    activeId = state.activeId?.takeIf { id -> rows.any { it.id == id } } ?: rows.firstOrNull()?.id,
                    error = null, pageVersion = state.pageVersion + 1)
                loadBadges()
                failures = 0; pollAfterNanos = 0
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (generation == session && request == readEpoch && foreground) {
                    lastFailedAppend = append
                    failures = (failures + 1).coerceAtMost(4)
                    pollAfterNanos = System.nanoTime() + (5_000L shl failures) * 1_000_000L
                    sessionFailure(error)
                    state = state.copy(loading = false, loadingMore = false, syncing = false, initialized = true, error = message(error))
                }
            }
        }
    }

    private fun validatePage(page: ReelsPage, previous: String?) {
        if (page.hasMore && (page.nextCursor.isNullOrBlank() || page.nextCursor == previous)) throw invalidPage()
    }
    private fun invalidPage() = FeedApiException("Could not load the next page. Please try again.", FeedApiFailure.INVALID_RESPONSE)
    private fun compareRows(a: Reel, b: Reel): Int = a.createdAt.compareTo(b.createdAt).takeIf { it != 0 } ?: a.id.compareTo(b.id)

    private fun loadBadges() {
        val auth = token ?: return
        if (badgeJob?.isActive == true || !foreground) return
        if (System.nanoTime() - badgeCheckedAt >= 60_000_000_000L) checkedAuthors.clear()
        val ids = state.reels.map { it.author.id }.distinct().filterNot { it in checkedAuthors }.take(200)
        if (ids.isEmpty()) return
        val generation = session
        val request = ++badgeEpoch
        badgeJob = scope.launch {
            try {
                val verified = api.verified(auth, ids)
                if (generation == session && request == badgeEpoch && foreground) {
                    checkedAuthors.addAll(ids)
                    badgeCheckedAt = System.nanoTime()
                    state = state.copy(verifiedAuthors = (state.verifiedAuthors - ids.toSet()) + verified)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (generation == session && request == badgeEpoch) sessionFailure(error) }
        }
    }

    internal fun love(reel: Reel, onlyLove: Boolean = false) {
        val auth = token ?: return
        if (state.sessionExpired || loves[reel.id]?.saving == true) return
        val original = loves[reel.id] ?: ReelLoveState(reel.likesCount, reel.reaction)
        if (onlyLove && original.reaction == PostReaction.LOVE && !original.uncertain) return
        val generation = session
        val version = ++loveRevision
        loveVersions[reel.id] = version
        loves[reel.id] = original.copy(saving = true, error = null)
        scope.launch {
            var baseline = original
            try {
                if (original.uncertain) reactionsApi.summary(auth, reel.id).let { baseline = ReelLoveState(it.total, it.currentReaction) }
                if (generation != session) return@launch
                val next = if (!onlyLove && baseline.reaction == PostReaction.LOVE) null else PostReaction.LOVE
                if (onlyLove && baseline.reaction == PostReaction.LOVE) {
                    loves[reel.id] = baseline
                    return@launch
                }
                val delta = (if (next == null) 0 else 1) - (if (baseline.reaction == null) 0 else 1)
                loves[reel.id] = ReelLoveState((baseline.count + delta).coerceAtLeast(0), next, saving = true)
                val confirmed = reactionsApi.react(auth, reel.id, next)
                if (generation == session) loves[reel.id] = ReelLoveState(confirmed.total, confirmed.currentReaction)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (generation == session) {
                    sessionFailure(error)
                    loves[reel.id] = baseline.copy(uncertain = true, error = message(error))
                }
            }
        }
    }

    internal fun dismissLoveError(id: String) { loves[id]?.let { loves[id] = it.copy(error = null) } }
    internal fun openComments(reel: Reel) = comments.openComments(reel.asPost())

    fun recordPublished(owner: String?, post: MediaPost) {
        if (post.type != MediaPostType.VIDEO || owner?.lowercase() != post.userId.lowercase()) return
        if (ownerId != owner.lowercase()) enter(owner)
        if (state.sessionExpired) return
        val video = post.media.firstOrNull { it.type == FeedMediaType.VIDEO } ?: return
        val row = Reel(post.id, post.author, video.url, post.createdAt, post.updatedAt, post.caption,
            likesCount = post.likesCount, commentsCount = post.commentsCount)
        seeded.add(row.id)
        state = state.copy(reels = (listOf(row) + state.reels).distinctBy { it.id })
    }

    private fun sessionFailure(error: Exception) {
        if (error is FeedApiException && error.failure == FeedApiFailure.SESSION_EXPIRED) {
            state = state.copy(sessionExpired = true, error = message(error))
        }
    }
    private fun message(error: Exception) = (error as? FeedApiException)?.message ?: "Could not load Reels. Check your connection and try again."

    class Factory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReelsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReelsViewModel(tokenStore::read) as T
        }
    }

    companion object { const val UPDATE_INTERVAL_MILLIS = 5_000L }
}
