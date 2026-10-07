package com.mongosky.app.home

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

data class HomeFeedState(
    val posts: List<HomePost> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = true,
    val errors: Map<FeedSource, String> = emptyMap(),
    val sessionExpired: Boolean = false,
    val sessionError: String? = null,
    val refreshVersion: Int = 0,
    val sessionReady: Boolean = false
)

/**
 * Keeps both feed cursors and all network work outside composition. Data belongs
 * to the saved token, never to a display name. A leaving session cancels its work
 * and removes its cached posts, drafts and reactions.
 */
class HomeFeedViewModel(
    private val readToken: suspend () -> String?,
    private val loadMedia: suspend (String, String?) -> MediaFeedPage = { token, cursor -> MediaPostApi().loadFeed(token, cursor) },
    private val loadText: suspend (String, String?) -> TextFeedPage = { token, cursor -> TextPostApi().loadFeed(token, cursor) },
    private val actions: PostActions = PostActionsApi()
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
    private var replacingFeed = false
    private val feedJobs = mutableMapOf<FeedSource, Job>()
    private val emptyPages = mutableMapOf<FeedSource, Int>()
    internal val comments = CommentsController(actions, { token }, { scope },
        { uiState.sessionExpired }, { sessionVersion }, ::handleSessionFailure)
    private val reactionController = ReactionsController(actions, { token }, { scope },
        { uiState.sessionExpired }, { sessionVersion }, ::handleSessionFailure)

    fun startSession() {
        if (sessionJob?.isActive == true) return
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
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
                uiState = uiState.copy(sessionError = "Could not restore your saved session. Please try again.", loading = false)
            }
        }
    }

    fun endSession() {
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
        pager = FeedPager()
        replacingFeed = false
        uiState = HomeFeedState()
    }

    fun refresh() {
        if (token == null) { startSession(); return }
        if (uiState.sessionExpired || uiState.refreshing) return
        feedVersion++
        feedJobs.values.forEach { it.cancel() }
        feedJobs.clear()
        emptyPages.clear()
        pager = FeedPager(pager.unseenPublishedPosts, pager.unseenPublishedTextPosts)
        replacingFeed = true
        comments.onFeedRefresh()
        reactionController.onFeedRefresh()
        uiState = uiState.copy(
            loading = uiState.posts.isEmpty(), refreshing = uiState.posts.isNotEmpty(),
            hasMore = true, errors = emptyMap(), sessionError = null, refreshVersion = feedVersion
        )
        loadMore()
    }

    /** Consume only a confirmed upload from the activity's shared media ViewModel. */
    fun insertPublishedPost(post: MediaPost): Boolean {
        if (token == null || !uiState.sessionReady || uiState.sessionExpired) return false
        if (!pager.insertPublishedPost(post)) return false

        val posts = pager.drain()
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
        val posts = pager.drain()
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
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val count = when (source) {
                        FeedSource.MEDIA -> loadMedia(auth, cursor).also { pagerIfCurrent(version) { pager.accept(it) } }.posts.size
                        FeedSource.TEXT -> loadText(auth, cursor).also { pagerIfCurrent(version) { pager.accept(it) } }.posts.size
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
                        val posts = pager.drain()
                        if (pager.initialized) {
                            if (replacingFeed) comments.onFeedReady()
                            replacingFeed = false
                            uiState = uiState.copy(posts = posts)
                        }
                        uiState = uiState.copy(
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
    fun closeOverlays() { closeComments(); closeReactionPeople() }

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
