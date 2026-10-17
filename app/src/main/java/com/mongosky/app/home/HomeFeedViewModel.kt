package com.mongosky.app.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.comments.CommentsController
import com.mongosky.app.comments.PostComment
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedSource
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedPostType
import com.mongosky.app.post.PostActions
import com.mongosky.app.post.PostActionsApi
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.reactions.ReactionsController
import com.mongosky.app.textpost.TextPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlin.random.Random

data class HomeFeedState(
    val posts: List<HomePost> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = true,
    val errors: Map<FeedSource, String> = emptyMap(),
    val sessionExpired: Boolean = false,
    val sessionError: String? = null,
    val refreshVersion: Int = 0,
    val sessionReady: Boolean = false,
    val newPostsCount: Int = 0
)

/**
 * Keeps one feed cursor and all network work outside composition. Data belongs
 * to the saved token, never to a display name. A leaving session cancels its work
 * and removes its cached posts, drafts and reactions.
 */
class HomeFeedViewModel internal constructor(
    private val readToken: suspend () -> String?,
    private val loadHome: suspend (String, String?, List<String>) -> HomeTimelinePage = { token, cursor, visible -> HomeTimelineApi.shared.load(token, cursor, visible) },
    private val cache: HomeFeedCache = NoHomeFeedCache,
    private val updates: (String) -> Flow<Unit> = HomeFeedRealtime::events,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val actions: PostActions = PostActionsApi(),
    homeActions: HomeFeedActions = HomeFeedApi()
) : ViewModel() {
    var uiState by mutableStateOf(HomeFeedState(loading = true))
        private set
    val reactions get() = reactionController.reactions
    val commentCounts get() = comments.commentCounts
    val commentsState get() = comments.commentsState
    val peopleState get() = reactionController.peopleState

    private var token: String? = null
    private var sessionJob: Job? = null
    private var workJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val scope get() = CoroutineScope(viewModelScope.coroutineContext + workJob)
    private var pager = HomeTimeline()
    private var feedVersion = 0
    private var sessionVersion = 0
    private var tokenReadVersion = 0
    private var replacingFeed = false
    private var feedJob: Job? = null
    private var cacheJob: Job? = null
    private var cacheRevision = 0
    private var realtimeJob: Job? = null
    private var reconcileJob: Job? = null
    private var dirty = false
    private var lastHeadRead = 0L
    private var emptyPages = 0
    private var foreground = false
    private var scrolling = false
    private var headJob: Job? = null
    private var headVersion = 0
    private data class HeadUpdate(val posts: List<HomePost>, val missingIds: Set<String>, val relationRevision: Long,
        val acceptCounts: (List<FeedPost>) -> Unit)
    private var pendingHead: HeadUpdate? = null
    private val removedPosts = mutableSetOf<String>()
    private val editedPosts = mutableMapOf<String, HomePost>()
    private var visibleKeys = emptySet<String>()
    internal val images = HomeFeedImageState()
    internal val comments = CommentsController(actions, { token }, { scope },
        { uiState.sessionExpired }, { sessionVersion }, ::handleSessionFailure)
    private val reactionController = ReactionsController(actions, { token }, { scope },
        { uiState.sessionExpired }, { sessionVersion }, ::handleSessionFailure)
    internal val controls = HomeFeedController(homeActions, { token }, { scope }, { sessionVersion },
        { uiState.sessionExpired }, ::handleSessionFailure, ::applyEditedPost, ::applyDeletedPost)

    fun startSession() {
        if (sessionJob?.isActive == true) return
        val request = ++tokenReadVersion
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                ensureActive()
                if (request != tokenReadVersion) return@launch
                if (saved == null) {
                    discardCache()
                    resetSession()
                    uiState = HomeFeedState(sessionExpired = true, sessionError = "Please sign in again.")
                } else if (saved != token) {
                    if (token != null) discardCache()
                    resetSession()
                    token = saved
                    uiState = uiState.copy(sessionReady = true)
                    refresh()
                    val version = feedVersion
                    scope.launch {
                        val cached = try { cache.read(saved) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                        if (version == feedVersion && token == saved && !pager.initialized && uiState.posts.isEmpty() && cached != null) {
                            controls.ingest(cached.posts, controls.relationVersion)
                            uiState = uiState.copy(posts = cached.posts)
                        }
                    }
                    startRealtime()
                } else {
                    uiState = uiState.copy(sessionError = null, loading = feedJob?.isActive == true,
                        sessionReady = !uiState.sessionExpired)
                    if (uiState.posts.isEmpty() && feedJob?.isActive != true && uiState.errors.isEmpty()) refresh()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (request == tokenReadVersion) uiState = uiState.copy(sessionError = "Could not restore your saved session. Please try again.", loading = false)
            }
        }
    }

    fun endSession() {
        tokenReadVersion++
        sessionJob?.cancel()
        discardCache()
        resetSession()
    }

    /** Activity teardown keeps the snapshot; an intentional logout erases it. */
    fun detach() { tokenReadVersion++; sessionJob?.cancel(); setForeground(false); resetSession() }

    private fun resetSession() {
        sessionVersion++
        feedVersion++
        workJob.cancel()
        workJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        token = null
        feedJob = null
        realtimeJob = null; reconcileJob = null; dirty = false; lastHeadRead = 0L
        emptyPages = 0
        comments.endSession()
        reactionController.endSession()
        controls.endSession()
        images.clear(); scrolling = false
        headVersion++; headJob?.cancel(); pendingHead = null
        removedPosts.clear(); editedPosts.clear(); visibleKeys = emptySet()
        pager = HomeTimeline()
        replacingFeed = false
        uiState = HomeFeedState()
    }

    fun refresh() {
        if (token == null) { startSession(); return }
        if (uiState.sessionExpired || uiState.refreshing) return
        feedVersion++
        headVersion++; headJob?.cancel(); pendingHead = null
        feedJob?.cancel()
        feedJob = null
        emptyPages = 0
        val published = pager.unseenPublished
        pager = HomeTimeline().also { next -> published.asReversed().forEach(next::publish) }
        replacingFeed = true
        comments.onFeedRefresh()
        reactionController.onFeedRefresh()
        uiState = uiState.copy(
            loading = uiState.posts.isEmpty(), refreshing = uiState.posts.isNotEmpty(),
            hasMore = true, errors = emptyMap(), sessionError = null, refreshVersion = feedVersion, newPostsCount = 0
        )
        loadMore()
    }

    /** Consume only a confirmed upload from the activity's shared media ViewModel. */
    fun insertPublishedPost(post: MediaPost): Boolean {
        if (token == null || !uiState.sessionReady || uiState.sessionExpired) return false
        if (!post.isSelf || post.id.isBlank() || post.type != com.mongosky.app.mediapost.MediaPostType.IMAGE ||
            post.media.isEmpty() || post.media.any { it.type != com.mongosky.app.post.FeedMediaType.IMAGE }) return false
        pager.publish(HomePost.Media(post)); discardCache()

        val posts = visibleSnapshot(pager.snapshot())
        // During refresh, keep the visible old feed until the fresh page is ready.
        val visible = if (replacingFeed && !pager.initialized) {
            (posts + uiState.posts).distinctBy { it.key }
        } else {
            posts
        }
        uiState = uiState.copy(posts = visible, hasMore = pager.hasMore)
        return true
    }

    fun retryFeed() {
        if (token == null) { startSession(); return }
        emptyPages = 0
        uiState = uiState.copy(errors = emptyMap(), sessionError = null)
        loadMore()
    }

    /** Insert the confirmed text response directly; no full-feed reload is needed. */
    fun insertPublishedTextPost(post: TextPost): Boolean {
        if (token == null || !uiState.sessionReady || uiState.sessionExpired) return false
        if (!post.isSelf || post.id.isBlank() || post.displayText.isBlank()) return false
        pager.publish(HomePost.Text(post)); discardCache()
        val posts = visibleSnapshot(pager.snapshot())
        val visible = if (replacingFeed && !pager.initialized) {
            (posts + uiState.posts).distinctBy { it.key }
        } else posts
        uiState = uiState.copy(posts = visible, hasMore = pager.hasMore)
        return true
    }

    fun loadMore() {
        val auth = token ?: return
        if (uiState.sessionExpired || feedJob?.isActive == true || !pager.hasMore || uiState.errors.isNotEmpty()) return
        val version = feedVersion
        val revision = cacheRevision
        val cursor = pager.cursor
        val relations = controls.relationVersion
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = loadHome(auth, cursor, emptyList())
                ensureActive()
                if (version != feedVersion) return@launch
                pager.accept(page)
                controls.ingest(page.posts, relations)
                emptyPages = if (page.posts.isEmpty()) emptyPages + 1 else 0
                if (emptyPages > 3 && pager.hasMore) throw FeedApiException("No new posts were returned. Pull down to refresh.", FeedApiFailure.INVALID_RESPONSE)
                if (replacingFeed) comments.onFeedReady()
                replacingFeed = false
                lastHeadRead = clockMillis()
                uiState = uiState.copy(posts = visibleSnapshot(pager.snapshot()), hasMore = pager.hasMore)
                if (cursor == null && revision == cacheRevision) page.snapshot?.let { saveCache(auth, it) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == feedVersion) {
                    handleSessionFailure(error)
                    uiState = uiState.copy(errors = mapOf(FeedSource.MEDIA to message(error)))
                }
            } finally {
                if (version == feedVersion) {
                    feedJob = null
                    uiState = uiState.copy(loading = false, refreshing = false, hasMore = pager.hasMore)
                    if (uiState.posts.isEmpty() && pager.initialized && pager.hasMore && uiState.errors.isEmpty() && !uiState.sessionExpired) loadMore()
                    else if (dirty) scheduleReconcile()
                }
            }
        }
        feedJob = job
        uiState = uiState.copy(loading = true)
        job.start()
    }

    private fun saveCache(auth: String, body: String) {
        cacheJob?.cancel()
        cacheJob = scope.launch {
            try { cache.write(auth, body) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A cache write never fails a successful network read. */ }
        }
    }

    private fun discardCache() {
        cacheRevision++
        val auth = token ?: return
        val prior = cacheJob
        prior?.cancel()
        viewModelScope.launch {
            prior?.join()
            try { cache.clear(auth) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    internal fun setForeground(active: Boolean) {
        foreground = active
        controls.setForeground(active)
        if (!active) {
            headVersion++; headJob?.cancel(); pendingHead = null
            realtimeJob?.cancel(); realtimeJob = null
            reconcileJob?.cancel(); reconcileJob = null
        } else {
            startRealtime()
            dirty = true; scheduleReconcile()
        }
    }

    private fun startRealtime() {
        val auth = token ?: return
        if (!foreground || uiState.sessionExpired || realtimeJob?.isActive == true) return
        val session = sessionVersion
        realtimeJob = scope.launch {
            var backoff = 1_000L
            while (isActive && foreground && session == sessionVersion && !uiState.sessionExpired) {
                var missingEndpoint = false
                try {
                    updates(auth).collect {
                        backoff = 1_000L
                        dirty = true; scheduleReconcile()
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (session == sessionVersion) handleSessionFailure(e)
                    // The normal foreground poll covers an older deployment.
                    missingEndpoint = e is FeedApiException && e.isMissingHomeEndpoint()
                }
                delay((if (missingEndpoint) 5 * 60_000L else backoff) + Random.nextLong(500, 2_000))
                backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    private fun scheduleReconcile() {
        if (!foreground || scrolling || !dirty || uiState.sessionExpired || reconcileJob?.isActive == true) return
        reconcileJob = scope.launch {
            val elapsed = clockMillis() - lastHeadRead
            delay((10_000L - elapsed).coerceAtLeast(700L) + Random.nextLong(0, 700))
            reconcileJob = null
            if (foreground && !scrolling && dirty) {
                if (!pager.initialized && feedJob?.isActive != true) {
                    dirty = false
                    retryFeed()
                } else checkForUpdates()
            }
        }
    }

    internal fun setScrolling(active: Boolean) {
        scrolling = active
        images.setScrolling(active)
        if (!active && foreground) {
            pendingHead?.let { update -> pendingHead = null; applyHead(update) }
            scheduleReconcile()
        }
    }

    internal fun visiblePosts(keys: Set<String>) {
        val changed = keys != visibleKeys
        visibleKeys = keys
        if (!foreground || uiState.sessionExpired) return
        val posts = uiState.posts.filter { it.key in keys }
        controls.visible(posts)
        posts.forEach(reactionController::ensureReaction)
        if (changed) { dirty = true; scheduleReconcile() }
    }

    /** Check heads without resetting cursors, loaded history or the current scroll anchor. */
    internal fun checkForUpdates() {
        val auth = token ?: return
        if (!foreground || scrolling || uiState.sessionExpired || uiState.loading || uiState.refreshing ||
            !pager.initialized || uiState.errors.isNotEmpty() || headJob?.isActive == true) return
        val session = sessionVersion
        val feed = feedVersion
        val request = ++headVersion
        val relations = controls.relationVersion
        val acceptCounts = comments.beginPostCountRead()
        dirty = false
        // Failed reads are rate-limited too; a busy stream cannot create a retry storm.
        lastHeadRead = clockMillis()
        headJob = scope.launch {
            try {
                val revision = cacheRevision
                val ids = uiState.posts.asSequence().filter { it.key in visibleKeys }.map { it.id }.take(8).toList()
                val page = loadHome(auth, null, ids)
                ensureActive()
                if (session != sessionVersion || feed != feedVersion || request != headVersion || !foreground) return@launch
                lastHeadRead = clockMillis()
                val posts = (page.posts + page.visiblePosts).distinctBy { it.key }
                val update = HeadUpdate(posts, page.missingIds, relations, acceptCounts)
                if (revision == cacheRevision) page.snapshot?.let { saveCache(auth, it) }
                if (scrolling) pendingHead = update else applyHead(update)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && feed == feedVersion && request == headVersion) handleSessionFailure(error)
                // A background check failure keeps the cached feed usable.
            } finally {
                if (session == sessionVersion && feed == feedVersion && request == headVersion) {
                    headJob = null
                    if (dirty) scheduleReconcile()
                }
            }
        }
    }

    private fun applyHead(update: HeadUpdate) {
        val posts = update.posts
        uiState.posts.filter { it.id in update.missingIds }.forEach(::applyDeletedPost)
        controls.ingest(posts, update.relationRevision)
        posts.filterNot { it.key in removedPosts }.forEach(pager::update)
        val changed = posts.associateBy { it.key }
        val newCount = posts.count { !pager.contains(it) && it.key !in removedPosts &&
            (uiState.posts.firstOrNull { old -> old.source == it.source }?.let { old -> it.createdAt >= old.createdAt } != false) }
        val current = uiState.posts.map { old ->
            changed[old.key]?.takeIf { it.updatedAt >= old.updatedAt } ?: old
        }
        uiState = uiState.copy(posts = visibleSnapshot(current), newPostsCount = newCount)
        val visible = uiState.posts.filter { it.key in visibleKeys }
        update.acceptCounts(posts.filter { pager.contains(it) && it.key !in removedPosts }.map { post ->
            // Only the normalized identity/source/count fields are consumed by the count gate.
            FeedPost(post.id, post.author.id, post.author, post.source, FeedPostType.UNKNOWN,
                post.createdAt, post.updatedAt, commentsCount = post.commentsCount)
        })
        reactionController.refreshVisible(visible)
        visiblePosts(visibleKeys)
    }

    private fun visibleSnapshot(posts: List<HomePost>): List<HomePost> = posts.filterNot { it.key in removedPosts }.map { post ->
        editedPosts[post.key]?.takeIf { it.updatedAt > post.updatedAt } ?: post
    }

    private fun applyEditedPost(post: HomePost) {
        editedPosts[post.key] = post
        discardCache()
        pager.update(post)
        uiState = uiState.copy(posts = uiState.posts.map { if (it.key == post.key) post else it })
    }

    private fun applyDeletedPost(post: HomePost) {
        discardCache()
        removedPosts.add(post.key); editedPosts.remove(post.key); pager.remove(post.key)
        if (commentsState?.postId == post.id) closeComments()
        if (peopleState?.postId == post.id) closeReactionPeople()
        uiState = uiState.copy(posts = uiState.posts.filterNot { it.key == post.key })
    }

    // Public facade keeps existing post callbacks and clients stable.
    fun ensureReaction(post: HomePost) = reactionController.ensureReaction(post)
    /** Public profile metadata refreshes reactions without replacing the home feed or comment drafts. */
    fun refreshProfileReactions() = reactionController.onFeedRefresh()
    fun beginProfileCountRead() = comments.beginPostCountRead()
    fun react(post: HomePost, selected: PostReaction = PostReaction.LOVE) = reactionController.react(post, selected)
    fun openReactionPeople(post: HomePost) = reactionController.openReactionPeople(post)
    fun closeReactionPeople() = reactionController.closeReactionPeople()
    fun selectReactionPeopleFilter(filter: PostReaction?) = reactionController.selectReactionPeopleFilter(filter)
    fun retryReactionPeople() = reactionController.retryReactionPeople()
    fun openComments(post: HomePost) = comments.openComments(post)
    fun closeComments() = comments.closeComments()
    fun backComments() = comments.backComments()
    fun openReplies(comment: PostComment) = comments.openReplies(comment)
    fun retryComments() = comments.retryComments()
    fun loadComments() = comments.loadComments()
    fun setCommentDraft(value: String) = comments.setCommentDraft(value)
    fun editComment(comment: PostComment) = comments.editComment(comment)
    fun cancelEdit() = comments.cancelEdit()
    fun sendComment() = comments.sendComment()
    fun loveComment(comment: PostComment) = comments.loveComment(comment)
    fun deleteComment(comment: PostComment) = comments.deleteComment(comment)
    fun closeOverlays() { controls.closeOverlays(); closeComments(); closeReactionPeople() }

    private fun handleSessionFailure(error: Exception) {
        if (error is FeedApiException && error.failure == FeedApiFailure.SESSION_EXPIRED) {
            discardCache()
            resetSession()
            uiState = HomeFeedState(sessionExpired = true, sessionReady = false,
                sessionError = "Your session has expired. Please sign in again.")
        }
    }

    private fun message(error: Exception): String = if (error is FeedApiException) error.message ?: "Please try again."
        else "Could not complete the request. Please try again."

    class Factory(private val tokenStore: TokenStore, private val context: Context? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeFeedViewModel::class.java))
            return HomeFeedViewModel(readToken = tokenStore::read,
                cache = context?.let { HomeFeedDiskCache(it) } ?: NoHomeFeedCache) as T
        }
    }
}
