package com.mongosky.app.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.data.local.TokenStore
import com.mongosky.app.data.remote.FeedApiException
import com.mongosky.app.data.remote.FeedApiFailure
import com.mongosky.app.data.remote.feed.PostActions
import com.mongosky.app.data.remote.feed.PostActionsApi
import com.mongosky.app.data.remote.mediapost.MediaPostApi
import com.mongosky.app.data.remote.textpost.TextPostApi
import com.mongosky.app.domain.model.FeedSource
import com.mongosky.app.domain.model.feed.PostComment
import com.mongosky.app.domain.model.feed.PostReaction
import com.mongosky.app.domain.model.feed.ReactionPerson
import com.mongosky.app.domain.model.feed.ReactionSummary
import com.mongosky.app.domain.model.mediapost.MediaFeedPage
import com.mongosky.app.domain.model.textpost.TextFeedPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HomeFeedState(
    val posts: List<HomePost> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = true,
    val errors: Map<FeedSource, String> = emptyMap(),
    val sessionExpired: Boolean = false,
    val sessionError: String? = null,
    val refreshVersion: Int = 0
)

data class PostReactionState(
    val summary: ReactionSummary = ReactionSummary(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

data class CommentThread(
    val parent: PostComment? = null,
    val comments: List<PostComment> = emptyList(),
    val cursor: String? = null,
    val seenCursors: Set<String> = emptySet(),
    val hasMore: Boolean = true,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

data class CommentsState(
    val postKey: String,
    val postId: String,
    val threads: List<CommentThread> = listOf(CommentThread()),
    val draft: String = "",
    val editing: PostComment? = null,
    val sending: Boolean = false,
    val busyIds: Set<String> = emptySet(),
    val error: String? = null
) {
    val current: CommentThread get() = threads.last()
}

data class ReactionPeopleState(
    val postId: String, val loading: Boolean = true,
    val people: List<ReactionPerson> = emptyList(), val error: String? = null
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
    val reactions = mutableStateMapOf<String, PostReactionState>()
    val commentCounts = mutableStateMapOf<String, Long>()
    var commentsState by mutableStateOf<CommentsState?>(null)
        private set
    var peopleState by mutableStateOf<ReactionPeopleState?>(null)
        private set

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
    private val commentVersions = mutableMapOf<String, Int>()
    private var commentsAtRefresh: Map<String, Int> = emptyMap()
    private val reactionJobs = mutableMapOf<String, Job>()
    private val reactionVersions = mutableMapOf<String, Int>()
    private val summarySlots = Semaphore(3)
    private var commentReadJob: Job? = null
    private var commentWindow = 0
    private var commentRevision = 0
    private val changedComments = mutableMapOf<String, Pair<Int, PostComment>>()
    private val deletedComments = mutableSetOf<String>()
    private var peopleJob: Job? = null

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
                    refresh()
                } else {
                    uiState = uiState.copy(sessionError = null, loading = feedJobs.isNotEmpty())
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
        reactionJobs.clear()
        reactionVersions.clear()
        emptyPages.clear()
        reactions.clear()
        commentCounts.clear()
        commentVersions.clear()
        commentsAtRefresh = emptyMap()
        commentsState = null
        commentRevision = 0
        changedComments.clear()
        deletedComments.clear()
        peopleState = null
        commentWindow++
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
        pager = FeedPager()
        replacingFeed = true
        commentsAtRefresh = commentVersions.toMap()
        reactions.keys.toList().forEach { key ->
            val state = reactions[key] ?: return@forEach
            if (!state.saving) {
                reactionJobs.remove(key)?.cancel()
                reactionVersions[key] = (reactionVersions[key] ?: 0) + 1
                reactions[key] = state.copy(loaded = false, loading = false, error = null)
            }
        }
        uiState = uiState.copy(
            loading = uiState.posts.isEmpty(), refreshing = uiState.posts.isNotEmpty(),
            hasMore = true, errors = emptyMap(), sessionError = null, refreshVersion = feedVersion
        )
        loadMore()
    }

    fun retryFeed() {
        if (token == null) { startSession(); return }
        emptyPages.clear()
        uiState = uiState.copy(errors = emptyMap(), sessionError = null)
        loadMore()
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
                            if (replacingFeed) commentCounts.keys.toList().forEach { key ->
                                if (commentVersions[key] == commentsAtRefresh[key]) commentCounts.remove(key)
                            }
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

    fun ensureReaction(post: HomePost) {
        val auth = token ?: return
        if (uiState.sessionExpired) return
        val current = reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
        if (current.loaded || current.loading || current.saving) return
        val version = reactionVersions[post.key] ?: 0
        val session = sessionVersion
        reactions[post.key] = current.copy(loading = true, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val summary = summarySlots.withPermit { actions.summary(auth, post.id) }
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) {
                    reactions[post.key] = PostReactionState(summary = summary, loaded = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) {
                    handleSessionFailure(error)
                    reactions[post.key] = current.copy(loading = false, error = message(error))
                }
            } finally {
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) reactionJobs.remove(post.key)
            }
        }
        reactionJobs[post.key] = job
        job.start()
    }

    fun react(post: HomePost, selected: PostReaction = PostReaction.LOVE) {
        val auth = token ?: return
        if (uiState.sessionExpired || reactions[post.key]?.saving == true) return
        reactionJobs.remove(post.key)?.cancel()
        val version = reactionVersions[post.key].orZero() + 1
        reactionVersions[post.key] = version
        val session = sessionVersion
        val before = reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
        reactions[post.key] = before.copy(saving = true, loading = false, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var baseline = before.summary
            try {
                if (!before.loaded) baseline = summarySlots.withPermit { actions.summary(auth, post.id) }
                if (session != sessionVersion || reactionVersions[post.key] != version) return@launch
                val next = if (baseline.currentReaction == selected) null else selected
                reactions[post.key] = PostReactionState(baseline.withReaction(next), loaded = true, saving = true)
                val confirmed = actions.react(auth, post.id, next)
                if (session == sessionVersion && reactionVersions[post.key] == version) reactions[post.key] = PostReactionState(confirmed, loaded = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session == sessionVersion && reactionVersions[post.key] == version) {
                    handleSessionFailure(error)
                    // A timeout can follow a committed write. The next tap reads
                    // the server state before choosing POST versus DELETE.
                    reactions[post.key] = PostReactionState(baseline, loaded = false, error = message(error))
                }
            } finally {
                if (session == sessionVersion && reactionVersions[post.key] == version) reactionJobs.remove(post.key)
            }
        }
        reactionJobs[post.key] = job
        job.start()
    }

    fun openComments(post: HomePost) {
        if (token == null || uiState.sessionExpired) return
        closeComments()
        commentsState = CommentsState(post.key, post.id)
        loadComments()
    }

    fun closeComments() {
        commentReadJob?.cancel()
        commentReadJob = null
        commentWindow++
        commentsState = null
    }

    fun backComments() {
        val state = commentsState ?: return
        if (state.threads.size == 1) { closeComments(); return }
        commentReadJob?.cancel()
        commentsState = state.copy(threads = state.threads.dropLast(1), editing = null, draft = "", error = null)
        if (commentsState?.current?.initialized == false) loadComments()
    }

    fun openReplies(comment: PostComment) {
        val state = commentsState ?: return
        if (state.sending || state.busyIds.contains(comment.id)) return
        commentReadJob?.cancel()
        val previous = state.threads.dropLast(1) + state.current.copy(loading = false)
        commentsState = state.copy(threads = previous + CommentThread(parent = comment), editing = null, draft = "", error = null)
        loadComments()
    }

    fun retryComments() {
        val state = commentsState ?: return
        updateCurrentThread { it.copy(error = null) }
        commentsState = commentsState?.copy(error = null)
        if (state.current.initialized) refreshCurrentThread() else loadComments()
    }

    private fun refreshCurrentThread() {
        commentReadJob?.cancel()
        updateCurrentThread { CommentThread(parent = it.parent) }
        loadComments()
    }

    fun loadComments() {
        val auth = token ?: return
        val state = commentsState ?: return
        val thread = state.current
        if (thread.loading || !thread.hasMore || thread.error != null || uiState.sessionExpired) return
        val window = commentWindow
        val parentId = thread.parent?.id
        val depth = state.threads.size
        val readRevision = commentRevision
        val session = sessionVersion
        updateCurrentThread { it.copy(loading = true) }
        commentReadJob = scope.launch {
            try {
                val page = actions.comments(auth, state.postId, parentId, thread.cursor)
                if (session != sessionVersion) return@launch
                if (page.hasMore && (page.nextCursor == null || page.nextCursor in thread.seenCursors)) {
                    throw FeedApiException("Could not load more comments. Please refresh.", FeedApiFailure.INVALID_RESPONSE)
                }
                if (sameThread(window, parentId, depth)) updateCurrentThread { current ->
                    current.copy(
                        comments = (current.comments + page.comments.map { row ->
                            changedComments[row.id]?.takeIf { it.first > readRevision }?.second ?: row
                        }).filterNot { it.id in deletedComments }.distinctBy { it.id },
                        cursor = page.nextCursor, hasMore = page.hasMore, initialized = true,
                        seenCursors = current.seenCursors + listOfNotNull(page.nextCursor), loading = false, error = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session != sessionVersion) return@launch
                handleSessionFailure(error)
                if (sameThread(window, parentId, depth)) updateCurrentThread { it.copy(loading = false, error = message(error)) }
            }
        }
    }

    fun setCommentDraft(value: String) {
        val state = commentsState ?: return
        if (!state.sending && value.codePointCount(0, value.length) <= 2000) commentsState = state.copy(draft = value, error = null)
    }

    fun editComment(comment: PostComment) {
        val state = commentsState ?: return
        if (comment.canModify && !state.sending && comment.id !in state.busyIds) {
            commentsState = state.copy(editing = comment, draft = comment.text, error = null)
        }
    }

    fun cancelEdit() { commentsState = commentsState?.copy(editing = null, draft = "", error = null) }

    fun sendComment() {
        val auth = token ?: return
        val state = commentsState ?: return
        if (state.sending || state.draft.isBlank() || uiState.sessionExpired) return
        val window = commentWindow
        val parent = state.current.parent?.id
        val session = sessionVersion
        commentsState = state.copy(sending = true, error = null)
        scope.launch {
            try {
                if (state.editing != null) {
                    val edited = actions.editComment(auth, state.editing.id, state.draft)
                    if (session != sessionVersion) return@launch
                    rememberChangedComment(edited)
                    if (window == commentWindow) mapComments { if (it.id == edited.id) edited else it }
                } else {
                    val result = actions.createComment(auth, state.postId, state.draft, parent)
                    if (session != sessionVersion) return@launch
                    recordCommentCount(state.postKey, result.commentsCount)
                    rememberChangedComment(result.comment)
                    if (window == commentWindow) {
                        if (parent != null) mapComments { if (it.id == parent) it.copy(repliesCount = it.repliesCount + 1) else it }
                        commentsState = commentsState?.let { current ->
                            current.copy(threads = current.threads.map { thread ->
                                if (thread.parent?.id == parent) thread.copy(comments =
                                    listOf(result.comment) + thread.comments.filterNot { it.id == result.comment.id }) else thread
                            })
                        }
                    }
                }
                if (window == commentWindow) commentsState = commentsState?.copy(sending = false, editing = null, draft = "")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session != sessionVersion) return@launch
                handleSessionFailure(error)
                if (window == commentWindow) commentsState = commentsState?.copy(sending = false,
                    error = if (state.editing == null && error is FeedApiException &&
                        error.failure in setOf(FeedApiFailure.NETWORK, FeedApiFailure.TIMEOUT)) {
                        "Could not confirm your comment. Refresh comments to check before sending again."
                    } else message(error))
            }
        }
    }

    fun loveComment(comment: PostComment) {
        val auth = token ?: return
        val state = commentsState ?: return
        if (comment.id in state.busyIds || uiState.sessionExpired) return
        val window = commentWindow
        val session = sessionVersion
        commentsState = state.copy(busyIds = state.busyIds + comment.id, error = null)
        mapComments { if (it.id == comment.id) it.copy(
            isLovedByMe = !comment.isLovedByMe,
            lovesCount = (comment.lovesCount + if (comment.isLovedByMe) -1 else 1).coerceAtLeast(0)) else it }
        scope.launch {
            try {
                val result = actions.loveComment(auth, comment.id)
                if (session != sessionVersion) return@launch
                val confirmed = comment.copy(isLovedByMe = result.loved, lovesCount = result.count)
                rememberChangedComment(confirmed)
                if (window == commentWindow) mapComments { if (it.id == comment.id) it.copy(isLovedByMe = result.loved, lovesCount = result.count) else it }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session != sessionVersion) return@launch
                handleSessionFailure(error)
                if (window == commentWindow) {
                    mapComments { if (it.id == comment.id) comment else it }
                    commentsState = commentsState?.copy(error = message(error))
                    refreshCurrentThread()
                }
            } finally {
                if (window == commentWindow) commentsState = commentsState?.let { it.copy(busyIds = it.busyIds - comment.id) }
            }
        }
    }

    fun deleteComment(comment: PostComment) {
        val auth = token ?: return
        val state = commentsState ?: return
        if (!comment.canModify || comment.id in state.busyIds || uiState.sessionExpired) return
        val window = commentWindow
        val session = sessionVersion
        commentsState = state.copy(busyIds = state.busyIds + comment.id, error = null)
        scope.launch {
            try {
                val result = actions.deleteComment(auth, comment.id)
                if (session != sessionVersion) return@launch
                recordCommentCount(state.postKey, result.commentsCount)
                if (result.deleted) {
                    deletedComments.add(comment.id)
                    changedComments.remove(comment.id)
                }
                if (result.deleted && window == commentWindow) {
                    commentsState = commentsState?.let { current -> current.copy(threads = current.threads.map { thread ->
                        thread.copy(comments = thread.comments.filterNot { it.id == comment.id })
                    }) }
                    comment.parentId?.let { parent -> mapComments { if (it.id == parent) it.copy(repliesCount = (it.repliesCount - 1).coerceAtLeast(0)) else it } }
                    if (commentsState?.editing?.id == comment.id) cancelEdit()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session != sessionVersion) return@launch
                handleSessionFailure(error)
                if (window == commentWindow) commentsState = commentsState?.copy(error = message(error))
            } finally {
                if (window == commentWindow) commentsState = commentsState?.let { it.copy(busyIds = it.busyIds - comment.id) }
            }
        }
    }

    fun openReactionPeople(post: HomePost) {
        val auth = token ?: return
        if (uiState.sessionExpired) return
        closeReactionPeople()
        peopleState = ReactionPeopleState(post.id)
        val session = sessionVersion
        peopleJob = scope.launch {
            try {
                val people = actions.reactionPeople(auth, post.id)
                if (session != sessionVersion) return@launch
                if (peopleState?.postId == post.id) peopleState = ReactionPeopleState(post.id, loading = false, people = people)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session != sessionVersion) return@launch
                handleSessionFailure(error)
                if (peopleState?.postId == post.id) peopleState = ReactionPeopleState(post.id, loading = false, error = message(error))
            }
        }
    }

    fun closeReactionPeople() { peopleJob?.cancel(); peopleJob = null; peopleState = null }
    fun closeOverlays() { closeComments(); closeReactionPeople() }

    private fun sameThread(window: Int, parent: String?, depth: Int): Boolean = window == commentWindow &&
        commentsState?.threads?.size == depth && commentsState?.current?.parent?.id == parent

    private fun updateCurrentThread(block: (CommentThread) -> CommentThread) {
        commentsState = commentsState?.let { it.copy(threads = it.threads.dropLast(1) + block(it.current)) }
    }

    private fun mapComments(block: (PostComment) -> PostComment) {
        commentsState = commentsState?.let { state -> state.copy(threads = state.threads.map {
            it.copy(parent = it.parent?.let(block), comments = it.comments.map(block))
        }) }
    }

    private fun handleSessionFailure(error: Exception) {
        if (error is FeedApiException && error.failure == FeedApiFailure.SESSION_EXPIRED) {
            uiState = uiState.copy(sessionExpired = true, sessionError = "Your session has expired. Please sign in again.")
        }
    }

    private fun recordCommentCount(key: String, count: Long) {
        commentCounts[key] = count
        commentVersions[key] = commentVersions[key].orZero() + 1
    }

    private fun rememberChangedComment(comment: PostComment) {
        changedComments[comment.id] = (++commentRevision) to comment
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
