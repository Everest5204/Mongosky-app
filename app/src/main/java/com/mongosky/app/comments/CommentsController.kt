package com.mongosky.app.comments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.HomePost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared comment state for Home and Profile, owned by the signed-in session. */
internal class CommentsController(
    private val actions: CommentActions,
    private val currentToken: () -> String?,
    private val currentScope: () -> CoroutineScope,
    private val expired: () -> Boolean,
    private val currentSession: () -> Int,
    private val onSessionFailure: (Exception) -> Unit
) {
    private val token get() = currentToken()
    private val scope get() = currentScope()
    val sessionExpired get() = expired()
    private val sessionVersion get() = currentSession()
    private fun handleSessionFailure(error: Exception) = onSessionFailure(error)
    val commentCounts = mutableStateMapOf<String, Long>()
    var commentsState by mutableStateOf<CommentsState?>(null)
        private set
    private val commentVersions = mutableMapOf<String, Int>()
    private var commentsAtRefresh: Map<String, Int> = emptyMap()
    private var commentReadJob: Job? = null
    private var commentWindow = 0
    private var commentRevision = 0
    private val changedComments = mutableMapOf<String, Pair<Int, PostComment>>()
    private val deletedComments = mutableSetOf<String>()

    fun onFeedRefresh() { commentsAtRefresh = commentVersions.toMap() }
    /** Accept fresh metadata only when no newer local comment mutation or session change occurred. */
    fun beginPostCountRead(): (List<FeedPost>) -> Unit {
        val session = sessionVersion
        val revisions = commentVersions.toMap()
        return { posts ->
            if (session == sessionVersion && !sessionExpired) posts.forEach { post ->
                val key = "${post.source.name}:${post.id}"
                if (commentVersions[key] == revisions[key]) commentCounts[key] = post.commentsCount
            }
        }
    }
    fun onFeedReady() {
        commentCounts.keys.toList().forEach { key ->
            if (commentVersions[key] == commentsAtRefresh[key]) commentCounts.remove(key)
        }
    }
    fun endSession() {
        closeComments()
        commentCounts.clear(); commentVersions.clear(); commentsAtRefresh = emptyMap()
        commentRevision = 0; changedComments.clear(); deletedComments.clear()
    }

    fun openComments(post: HomePost) {
        if (token == null || sessionExpired) return
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
        if (thread.loading || !thread.hasMore || thread.error != null || sessionExpired) return
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
        if (state.sending || state.draft.isBlank() || sessionExpired) return
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
        if (comment.id in state.busyIds || sessionExpired) return
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
        if (!comment.canModify || comment.id in state.busyIds || sessionExpired) return
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

}
