package com.mongosky.app.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.comments.CommentsController
import com.mongosky.app.comments.PostComment
import com.mongosky.app.mediapost.MediaFeedPage
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostApi
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
import com.mongosky.app.textpost.TextFeedPage
import com.mongosky.app.textpost.TextPost
import com.mongosky.app.textpost.TextPostApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

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
 * Keeps both feed cursors and all network work outside composition. Data belongs
 * to the saved token, never to a display name. A leaving session cancels its work
 * and removes its cached posts, drafts and reactions.
 */
class HomeFeedViewModel internal constructor(
    private val readToken: suspend () -> String?,
    private val loadMedia: suspend (String, String?) -> MediaFeedPage = { token, cursor -> HomeFeedLoader.shared.media(token, cursor) },
    private val loadText: suspend (String, String?) -> TextFeedPage = { token, cursor -> HomeFeedLoader.shared.text(token, cursor) },
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
    private var pager = FeedPager()
    private var feedVersion = 0
    private var sessionVersion = 0
    private var tokenReadVersion = 0
    private var replacingFeed = false
    private val feedJobs = mutableMapOf<FeedSource, Job>()
    private val emptyPages = mutableMapOf<FeedSource, Int>()
    private var foreground = false
    private var scrolling = false
    private var headJob: Job? = null
    private var headVersion = 0
    private data class HeadUpdate(val posts: List<HomePost>, val relationRevision: Long,
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
    internal var download by mutableStateOf<HomeDownloadState?>(null)
        private set

    fun startSession() {
        if (sessionJob?.isActive == true) return
        val request = ++tokenReadVersion
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                ensureActive()
                if (request != tokenReadVersion) return@launch
                if (saved == null) {
                    resetSession()
                    uiState = HomeFeedState(sessionExpired = true, sessionError = "Please sign in again.")
                } else if (saved != token) {
                    resetSession()
                    token = saved
                    uiState = uiState.copy(sessionReady = true)
                    refresh()
                } else {
                    uiState = uiState.copy(sessionError = null, loading = feedJobs.isNotEmpty(),
                        sessionReady = !uiState.sessionExpired)
                    if (uiState.posts.isEmpty() && feedJobs.isEmpty() && uiState.errors.isEmpty()) refresh()
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
        resetSession()
    }

    private fun resetSession() {
        sessionVersion++
        feedVersion++
        workJob.cancel()
        workJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        token = null
        feedJobs.clear()
        emptyPages.clear()
        comments.endSession()
        reactionController.endSession()
        controls.endSession()
        images.clear(); scrolling = false
        headVersion++; headJob?.cancel(); pendingHead = null
        removedPosts.clear(); editedPosts.clear(); visibleKeys = emptySet()
        download = null
        pager = FeedPager()
        replacingFeed = false
        uiState = HomeFeedState()
    }

    fun refresh() {
        if (token == null) { startSession(); return }
        if (uiState.sessionExpired || uiState.refreshing) return
        feedVersion++
        headVersion++; headJob?.cancel(); pendingHead = null
        feedJobs.values.forEach { it.cancel() }
        feedJobs.clear()
        emptyPages.clear()
        pager = FeedPager(pager.unseenPublishedPosts, pager.unseenPublishedTextPosts)
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
        if (!pager.insertPublishedPost(post)) return false

        val posts = visibleSnapshot(pager.drain())
        // During refresh, keep the visible old feed until both sources are ready.
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
        emptyPages.clear()
        uiState = uiState.copy(errors = emptyMap(), sessionError = null)
        loadMore()
    }

    /** Insert the confirmed text response directly; no full-feed reload is needed. */
    fun insertPublishedTextPost(post: TextPost): Boolean {
        if (token == null || !uiState.sessionReady || uiState.sessionExpired) return false
        if (!pager.insertPublishedTextPost(post)) return false
        val posts = visibleSnapshot(pager.drain())
        val visible = if (replacingFeed && !pager.initialized) {
            (posts + uiState.posts).distinctBy { it.key }
        } else posts
        uiState = uiState.copy(posts = visible, hasMore = pager.hasMore)
        return true
    }

    fun loadMore() {
        val auth = token ?: return
        if (uiState.sessionExpired) return
        pager.neededSources().forEach { source ->
            if (feedJobs.containsKey(source) || uiState.errors.containsKey(source)) return@forEach
            val version = feedVersion
            val cursor = pager.cursor(source)
            val relations = controls.relationVersion
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val count = when (source) {
                        FeedSource.MEDIA -> loadMedia(auth, cursor).also { pagerIfCurrent(version) {
                            pager.accept(it); controls.ingest(it.posts.map(HomePost::Media), relations)
                        } }.posts.size
                        FeedSource.TEXT -> loadText(auth, cursor).also { pagerIfCurrent(version) {
                            pager.accept(it); controls.ingest(it.posts.map(HomePost::Text), relations)
                        } }.posts.size
                    }
                    if (version != feedVersion) return@launch
                    emptyPages[source] = if (count == 0) (emptyPages[source] ?: 0) + 1 else 0
                    if ((emptyPages[source] ?: 0) > 3 && pager.hasMore) {
                        throw FeedApiException("No new posts were returned. Pull down to refresh.", FeedApiFailure.INVALID_RESPONSE)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (version == feedVersion) {
                        handleSessionFailure(error)
                        uiState = uiState.copy(errors = uiState.errors + (source to message(error)))
                    }
                } finally {
                    if (version == feedVersion) {
                        feedJobs.remove(source)
                        val posts = visibleSnapshot(pager.drain())
                        val visible = if (pager.initialized) {
                            if (replacingFeed) comments.onFeedReady()
                            replacingFeed = false
                            posts
                        } else uiState.posts
                        uiState = uiState.copy(
                            posts = visible,
                            loading = feedJobs.isNotEmpty(),
                            refreshing = replacingFeed && feedJobs.isNotEmpty() && uiState.posts.isNotEmpty(),
                            hasMore = pager.hasMore
                        )
                        if (uiState.posts.isEmpty() && pager.initialized && pager.hasMore &&
                            uiState.errors.isEmpty() && feedJobs.isEmpty() && !uiState.sessionExpired) loadMore()
                    }
                }
            }
            feedJobs[source] = job
            uiState = uiState.copy(loading = true)
            job.start()
        }
    }

    private inline fun pagerIfCurrent(version: Int, block: () -> Unit) { if (version == feedVersion) block() }

    internal fun setForeground(active: Boolean) {
        foreground = active
        controls.setForeground(active)
        if (!active) { headVersion++; headJob?.cancel(); pendingHead = null }
    }

    internal fun setScrolling(active: Boolean) {
        scrolling = active
        images.setScrolling(active)
        if (!active && foreground) pendingHead?.let { update ->
            pendingHead = null; applyHead(update)
        }
    }

    internal fun visiblePosts(keys: Set<String>) {
        visibleKeys = keys
        if (!foreground || uiState.sessionExpired) return
        val posts = uiState.posts.filter { it.key in keys }
        controls.visible(posts)
        posts.forEach(reactionController::ensureReaction)
    }

    /** Check heads without resetting cursors, loaded history or the current scroll anchor. */
    internal fun checkForUpdates() {
        val auth = token ?: return
        if (!foreground || scrolling || uiState.sessionExpired || uiState.loading || uiState.refreshing ||
            !pager.initialized || uiState.errors.isNotEmpty() || uiState.posts.isEmpty() || headJob?.isActive == true) return
        val session = sessionVersion
        val feed = feedVersion
        val request = ++headVersion
        val relations = controls.relationVersion
        val acceptCounts = comments.beginPostCountRead()
        headJob = scope.launch {
            try {
                val posts = supervisorScope {
                    val media = async { loadMedia(auth, null) }
                    val text = async { loadText(auth, null) }
                    media.await().posts.map(HomePost::Media) + text.await().posts.map(HomePost::Text)
                }
                ensureActive()
                if (session != sessionVersion || feed != feedVersion || request != headVersion || !foreground) return@launch
                val update = HeadUpdate(posts, relations, acceptCounts)
                if (scrolling) pendingHead = update else applyHead(update)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && feed == feedVersion && request == headVersion) handleSessionFailure(error)
                // A background check failure keeps the cached feed usable.
            }
        }
    }

    private fun applyHead(update: HeadUpdate) {
        val posts = update.posts
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
        pager.update(post)
        uiState = uiState.copy(posts = uiState.posts.map { if (it.key == post.key) post else it })
    }

    private fun applyDeletedPost(post: HomePost) {
        removedPosts.add(post.key); editedPosts.remove(post.key); pager.remove(post.key)
        if (commentsState?.postId == post.id) closeComments()
        if (peopleState?.postId == post.id) closeReactionPeople()
        uiState = uiState.copy(posts = uiState.posts.filterNot { it.key == post.key })
    }

    internal fun requestDownload(post: HomePost) {
        if (uiState.sessionExpired) return
        if (download != null) { controls.notify("A download is already in progress."); return }
        controls.closeMenu()
        download = HomeDownloadState(post)
    }

    internal fun completeDownloadPicker(context: Context, uri: Uri?) {
        val pending = download ?: return
        if (!pending.choosing) return
        if (uri == null) { download = null; return }
        val generation = sessionVersion
        download = pending.copy(choosing = false)
        controls.notify("Downloading post…")
        scope.launch {
            try {
                HomeFeedDownloads.write(context.applicationContext, uri, pending.post)
                ensureActive()
                if (generation == sessionVersion) controls.notify("Post downloaded")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == sessionVersion) controls.notify("Could not download the post. Please try again.")
            } finally { if (generation == sessionVersion) download = null }
        }
    }

    internal fun cancelDownloadPicker() { if (download?.choosing == true) download = null }

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
            uiState = uiState.copy(sessionExpired = true, sessionReady = false,
                sessionError = "Your session has expired. Please sign in again.")
        }
    }

    private fun message(error: Exception): String = if (error is FeedApiException) error.message ?: "Please try again."
        else "Could not complete the request. Please try again."
    private fun Int?.orZero() = this ?: 0

    class Factory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeFeedViewModel::class.java))
            return HomeFeedViewModel(readToken = tokenStore::read) as T
        }
    }
}
