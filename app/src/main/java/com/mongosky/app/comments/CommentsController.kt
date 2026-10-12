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

/** One session-owned comment section shared by Home, both Profiles and Reels. */
internal class CommentsController(
    private val actions: CommentActions,
    private val currentToken: () -> String?,
    private val currentScope: () -> CoroutineScope,
    private val expired: () -> Boolean,
    private val currentSession: () -> Int,
    private val onSessionFailure: (Exception) -> Unit,
    private val badges: CommentBadges = CommentBadgeApi(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    val sessionExpired get() = expired()
    val commentCounts = mutableStateMapOf<String, Long>()
    var commentsState by mutableStateOf<CommentsState?>(null)
        private set
    var writing by mutableStateOf(false)
        private set
    private val countVersions = mutableMapOf<String, Int>()
    private var countsAtRefresh: Map<String, Int> = emptyMap()
    private var window = 0
    private var revision = 0
    private var requestSerial = 0
    private val readVersions = mutableMapOf<String, Int>()
    private val reads = mutableMapOf<String, Job>()
    private val reviewsAfterRead = mutableSetOf<String>()
    private var mutation: Job? = null
    private val loves = mutableMapOf<String, Job>()
    private val changed = mutableMapOf<String, Pair<Int, PostComment>>()
    private val deleted = mutableSetOf<String>()
    private var foreground = false
    private var nextSync = 0L
    private var syncFailures = 0
    private var replyRound = 0
    private var uncertainParent: String? = null
    private var badgeJob: Job? = null
    private var badgeRetryAt = 0L
    private var visibleAuthorIds: Set<String> = emptySet()
    private val badgeCache = LinkedHashMap<String, Pair<Long, Boolean>>()
    private val cache = LinkedHashMap<String, Pair<Long, CommentsState>>()

    fun onFeedRefresh() { countsAtRefresh = countVersions.toMap() }
    fun beginPostCountRead(): (List<FeedPost>) -> Unit {
        val session = currentSession(); val auth = currentToken(); val versions = countVersions.toMap()
        return { posts ->
            if (sameSession(session, auth)) posts.forEach { post ->
                val key = "${post.source.name}:${post.id}"
                if (countVersions[key] == versions[key]) commentCounts[key] = post.commentsCount.coerceAtLeast(0)
            }
        }
    }
    fun onFeedReady() {
        commentCounts.keys.toList().forEach { if (countVersions[it] == countsAtRefresh[it]) commentCounts.remove(it) }
    }
    fun endSession() {
        closeComments(); mutation?.cancel(); mutation = null; loves.values.toList().forEach { it.cancel() }; loves.clear()
        badgeJob?.cancel(); badgeJob = null; badgeCache.clear(); badgeRetryAt = 0; visibleAuthorIds = emptySet()
        writing = false; commentCounts.clear(); countVersions.clear(); countsAtRefresh = emptyMap()
        changed.clear(); deleted.clear(); cache.clear(); revision = 0
    }

    fun openComments(post: HomePost) {
        if (currentToken() == null || sessionExpired || !CommentPolicy.validId(post.id)) return
        closeComments()
        val cached = cache[post.key]?.takeIf { clock() - it.first < 120_000 }?.second
        commentsState = cached?.copy(draft = "", editing = null, replying = null, inlineDraft = "", menuId = null,
            busyIds = emptySet(), sending = false, sendingInline = false, error = null, inlineError = null,
            unconfirmedCreate = false, replies = emptyMap(), expandedIds = emptySet(),
            root = cached.root.copy(comments = cached.root.comments.take(10), initialized = false, cursor = null,
                seenCursors = emptySet(), headIds = emptyList(), hasMore = true)) ?: CommentsState(post.key, post.id)
        nextSync = 0; syncFailures = 0; replyRound = 0
        readThread(null, refresh = cached != null)
        resolveBadges()
    }
    fun closeComments() {
        commentsState?.takeIf { it.root.initialized }?.let { state ->
            cache[state.postKey] = clock() to state.copy(root = idle(state.root),
                replies = state.replies.mapValues { idle(it.value) })
            while (cache.size > 6) cache.remove(cache.keys.first())
        }
        window++; cancelReads(); badgeJob?.cancel(); badgeJob = null
        commentsState = null; foreground = false
        changed.clear(); deleted.clear(); reviewsAfterRead.clear(); uncertainParent = null; visibleAuthorIds = emptySet()
    }
    fun backComments() {
        val state = commentsState ?: return
        when {
            state.menuId != null -> closeMenu()
            state.inlineTargetId != null && !writing -> cancelEdit()
            else -> closeComments()
        }
    }
    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        if (!visible) { cancelReads(); badgeJob?.cancel(); badgeJob = null }
        else {
            nextSync = 0
            if (commentsState?.root?.initialized == false) loadComments() else checkForUpdates()
            resolveBadges()
        }
    }
    /** Only the open, resumed section polls. One root and one visible reply page per cycle. */
    fun checkForUpdates(visibleIds: Set<String> = emptySet()) {
        val state = commentsState ?: return
        if (!foreground || sessionExpired || writing || state.busyIds.isNotEmpty() || clock() < nextSync) return
        nextSync = clock() + CommentPolicy.UPDATE_INTERVAL_MILLIS
        if (state.root.initialized) readThread(null, refresh = true, quiet = true) else loadComments()
        val candidates = state.expandedIds.filter { id -> visibleIds.isEmpty() || id in visibleIds ||
            state.replies[id]?.comments?.any { it.id in visibleIds } == true }
        if (candidates.isNotEmpty()) {
            val id = candidates[replyRound % candidates.size]; replyRound++
            readThread(id, refresh = state.replies[id]?.initialized == true, quiet = true)
        }
        resolveBadges()
    }
    fun visibleComments(ids: Set<String>) {
        val state = commentsState ?: return
        val authors = ids.mapNotNull { state.find(it)?.userId }.toSet()
        if (authors != visibleAuthorIds) { visibleAuthorIds = authors; resolveBadges() }
    }
    fun openReplies(comment: PostComment) = startReply(comment)
    fun toggleReplies(comment: PostComment) {
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (row.isDeleted) return
        if (row.id in state.expandedIds) {
            commentsState = state.copy(expandedIds = state.expandedIds - row.id)
            stopRead(row.id)
        } else {
            commentsState = state.copy(expandedIds = state.expandedIds + row.id,
                replies = state.replies + (row.id to (state.replies[row.id] ?: CommentThread())))
            if (state.replies[row.id]?.initialized != true) readThread(row.id)
        }
    }
    fun startReply(comment: PostComment) {
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (writing || row.isDeleted || row.id in state.busyIds || sessionExpired) return
        if (state.replying?.id == row.id) { cancelEdit(); return }
        commentsState = state.copy(replying = row, editing = null, inlineDraft = "", inlineError = null, menuId = null)
    }
    fun editComment(comment: PostComment) {
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (!row.canModify || row.isDeleted || writing || row.id in state.busyIds || sessionExpired) return
        if (state.editing?.id == row.id) { closeMenu(); return }
        commentsState = state.copy(editing = row, replying = null, inlineDraft = row.text, inlineError = null, menuId = null)
    }
    fun cancelEdit() {
        if (!writing) commentsState = commentsState?.copy(editing = null, replying = null, inlineDraft = "", inlineError = null)
    }
    fun openMenu(comment: PostComment) {
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (row.canModify && !row.isDeleted && !writing && row.id !in state.busyIds && !sessionExpired)
            commentsState = state.copy(menuId = row.id)
    }
    fun closeMenu() { commentsState = commentsState?.copy(menuId = null) }
    fun setCommentDraft(value: String) {
        val state = commentsState ?: return
        if (!state.sending) commentsState = state.copy(draft = CommentPolicy.limitDraft(value), error = if (state.unconfirmedCreate) state.error else null)
    }
    fun setInlineDraft(value: String) {
        val state = commentsState ?: return
        if (!state.sendingInline) commentsState = state.copy(inlineDraft = CommentPolicy.limitDraft(value), inlineError = if (state.unconfirmedCreate) state.inlineError else null)
    }
    fun loadComments() = readThread(null)
    fun loadReplies(parentId: String) = readThread(parentId)
    fun retryThread(parentId: String?) {
        val state = commentsState ?: return
        readThread(parentId, refresh = thread(state, parentId).initialized || thread(state, parentId).comments.isNotEmpty(), resetPages = true,
            reviewCreate = state.unconfirmedCreate && parentId == uncertainParent)
    }
    fun retryComments() {
        val state = commentsState ?: return
        if (!state.unconfirmedCreate) commentsState = state.copy(error = null, inlineError = null)
        retryThread(null)
        val parent = if (state.unconfirmedCreate) uncertainParent else state.replying?.id ?: state.editing?.parentId
        parent?.takeIf { state.find(it) != null }?.let { retryThread(it) }
    }

    private fun readThread(parentId: String?, refresh: Boolean = false, quiet: Boolean = false, reviewCreate: Boolean = false, resetPages: Boolean = false) {
        val auth = currentToken() ?: return
        val state = commentsState ?: return
        val key = parentId ?: ""
        val before = thread(state, parentId)
        if (sessionExpired) return
        if (reads[key]?.isActive == true) {
            if (reviewCreate) reviewsAfterRead.add(key)
            return
        }
        if (!refresh && before.initialized && !before.hasMore) return
        if (parentId != null && (state.find(parentId) == null || parentId in deleted)) return
        val session = currentSession(); val view = window; val serial = ++requestSerial; val readRevision = revision
        readVersions[key] = serial
        updateThread(parentId) { it.copy(loading = !refresh, refreshing = refresh, error = if (quiet) it.error else null) }
        val job = currentScope().launch(start = CoroutineStart.LAZY) {
            try {
                val page = actions.comments(auth, state.postId, parentId, if (refresh) null else before.cursor)
                if (!validRead(session, auth, view, key, serial)) return@launch
                if (page.comments.any { it.postId != state.postId || it.parentId != parentId } ||
                    (page.hasMore && (page.nextCursor.isNullOrBlank() || (!refresh && page.nextCursor in before.seenCursors)))) {
                    throw FeedApiException("Could not load more comments. Please refresh.", FeedApiFailure.INVALID_RESPONSE)
                }
                val protected = changed.filterValues { it.first > readRevision }.keys + commentsState!!.busyIds +
                    draftPath(commentsState!!)
                val safePage = page.copy(comments = page.comments.filterNot { it.id in deleted || it.parentId in deleted }.map {
                    changed[it.id]?.takeIf { change -> change.first > readRevision }?.second ?: it
                })
                val old = thread(commentsState!!, parentId)
                val refreshed = if (resetPages) old.copy(comments = old.comments.filter { it.id in protected }, initialized = false) else old
                val next = if (refresh) CommentPolicy.refresh(refreshed, safePage, protected) else CommentPolicy.append(old, safePage)
                updateThread(parentId) { next }
                val removed = old.comments.map { it.id }.toSet() - next.comments.map { it.id }.toSet()
                removed.filterNot { it in protected }.forEach { prune(it, tombstone = false) }
                if (reviewCreate) commentsState = commentsState?.copy(unconfirmedCreate = false,
                    error = "Comments refreshed. Check whether it was posted before sending again.", inlineError = null)
                syncFailures = 0
                resolveBadges()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!validRead(session, auth, view, key, serial)) return@launch
                onSessionFailure(error)
                syncFailures = (syncFailures + 1).coerceAtMost(3)
                nextSync = clock() + (CommentPolicy.UPDATE_INTERVAL_MILLIS shl syncFailures)
                if (!quiet || !before.initialized || sessionExpired) updateThread(parentId) {
                    it.copy(error = message(error), loading = false, refreshing = false)
                }
            } finally {
                if (validRead(session, auth, view, key, serial)) {
                    reads.remove(key)
                    updateThread(parentId) { idle(it) }
                    if (reviewsAfterRead.remove(key) && commentsState?.unconfirmedCreate == true)
                        readThread(parentId, refresh = true, reviewCreate = true, resetPages = true)
                }
            }
        }
        reads[key] = job; job.start()
    }

    fun sendComment() = send(inline = false)
    fun sendInline() = send(inline = true)
    private fun send(inline: Boolean) {
        val auth = currentToken() ?: return
        val state = commentsState ?: return
        val edit = if (inline) state.editing else null
        val parent = if (inline && edit == null) state.replying?.id else null
        val text = if (inline) state.inlineDraft else state.draft
        if (writing || sessionExpired || !CommentPolicy.canSend(text) || (edit == null && state.unconfirmedCreate) ||
            (inline && state.inlineTargetId == null) || (edit != null && state.find(edit.id)?.canModify != true)) return
        val session = currentSession(); val view = window
        writing = true
        if (edit == null) bumpCount(state.postKey)
        commentsState = state.copy(sending = !inline, sendingInline = inline, error = null, inlineError = null)
        val job = currentScope().launch(start = CoroutineStart.LAZY) {
            try {
                if (edit != null) {
                    val row = actions.editComment(auth, edit.id, text)
                    if (!sameSession(session, auth)) return@launch
                    if (row.id != edit.id || row.postId != state.postId || row.parentId != edit.parentId || row.isDeleted) throw badResponse()
                    cache.remove(state.postKey)
                    if (view == window) { rememberChanged(row); mapRows { if (it.id == row.id) row else it } }
                } else {
                    val result = actions.createComment(auth, state.postId, text, parent)
                    if (!sameSession(session, auth)) return@launch
                    if (result.comment.postId != state.postId || result.comment.parentId != parent || result.comment.isDeleted) throw badResponse()
                    recordCount(state.postKey, result.commentsCount); cache.remove(state.postKey)
                    if (view == window) {
                        rememberChanged(result.comment)
                        updateThread(parent) { it.copy(comments = listOf(result.comment) + it.comments.filterNot { row -> row.id == result.comment.id }) }
                        if (parent != null) {
                            mapRows { if (it.id == parent) it.copy(repliesCount = it.repliesCount + 1).also(::rememberChanged) else it }
                            commentsState = commentsState?.let { it.copy(expandedIds = it.expandedIds + parent) }
                        }
                        resolveBadges()
                    }
                }
                if (view == window) commentsState = commentsState?.let {
                    if (inline) it.copy(editing = null, replying = null, inlineDraft = "", inlineError = null)
                    else it.copy(draft = "", error = null)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!sameSession(session, auth)) return@launch
                onSessionFailure(error)
                if (view == window) {
                    val unknown = edit == null && error is FeedApiException && error.failure in setOf(
                        FeedApiFailure.NETWORK, FeedApiFailure.TIMEOUT, FeedApiFailure.SERVER, FeedApiFailure.INVALID_RESPONSE)
                    val text = if (unknown) "Could not confirm your comment. Refresh to check before sending again." else message(error)
                    if (unknown) uncertainParent = parent
                    commentsState = commentsState?.let { it.copy(error = if (inline) it.error else text,
                        inlineError = if (inline) text else it.inlineError, unconfirmedCreate = it.unconfirmedCreate || unknown) }
                }
            } finally {
                if (sameSession(session, auth)) {
                    writing = false; mutation = null
                    if (view == window) {
                        commentsState = commentsState?.copy(sending = false, sendingInline = false)
                        if (parent != null && commentsState?.replies?.get(parent)?.initialized != true) readThread(parent)
                    }
                }
            }
        }
        mutation = job; job.start()
    }

    fun loveComment(comment: PostComment) {
        val auth = currentToken() ?: return
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (sessionExpired || row.isDeleted || row.id in state.busyIds || (writing && state.inlineTargetId == row.id)) return
        val session = currentSession(); val view = window
        commentsState = state.copy(busyIds = state.busyIds + row.id, error = null)
        val optimistic = row.copy(isLovedByMe = !row.isLovedByMe,
            lovesCount = (row.lovesCount + if (row.isLovedByMe) -1 else 1).coerceAtLeast(0))
        rememberChanged(optimistic); mapRows { if (it.id == row.id) optimistic else it }
        val job = currentScope().launch(start = CoroutineStart.LAZY) {
            try {
                val result = actions.loveComment(auth, row.id)
                if (!sameSession(session, auth) || view != window) return@launch
                mapRows { if (it.id == row.id) it.copy(isLovedByMe = result.loved, lovesCount = result.count.coerceAtLeast(0)).also(::rememberChanged) else it }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!sameSession(session, auth) || view != window) return@launch
                onSessionFailure(error)
                mapRows { if (it.id == row.id) it.copy(isLovedByMe = row.isLovedByMe, lovesCount = row.lovesCount).also(::rememberChanged) else it }
                commentsState = commentsState?.copy(error = message(error))
            } finally {
                if (sameSession(session, auth) && view == window) {
                    loves.remove(row.id)
                    commentsState = commentsState?.let { it.copy(busyIds = it.busyIds - row.id) }
                }
            }
        }
        loves[row.id] = job; job.start()
    }

    fun deleteComment(comment: PostComment) {
        val auth = currentToken() ?: return
        val state = commentsState ?: return
        val row = state.find(comment.id) ?: return
        if (!row.canModify || row.isDeleted || writing || row.id in state.busyIds || sessionExpired) return
        val session = currentSession(); val view = window
        writing = true; bumpCount(state.postKey)
        commentsState = state.copy(busyIds = state.busyIds + row.id, menuId = null, error = null)
        val job = currentScope().launch(start = CoroutineStart.LAZY) {
            try {
                val result = actions.deleteComment(auth, row.id)
                if (!sameSession(session, auth)) return@launch
                if (!result.deleted) throw FeedApiException("Could not delete this comment. Please refresh.", FeedApiFailure.REQUEST_REJECTED)
                recordCount(state.postKey, result.commentsCount); cache.remove(state.postKey)
                if (view == window) {
                    prune(row.id, tombstone = true)
                    row.parentId?.let { parent -> mapRows { if (it.id == parent)
                        it.copy(repliesCount = (it.repliesCount - 1).coerceAtLeast(0)).also(::rememberChanged) else it } }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!sameSession(session, auth)) return@launch
                onSessionFailure(error)
                if (view == window) commentsState = commentsState?.copy(error = message(error))
            } finally {
                if (sameSession(session, auth)) {
                    writing = false; mutation = null
                    if (view == window) commentsState = commentsState?.let { it.copy(busyIds = it.busyIds - row.id) }
                }
            }
        }
        mutation = job; job.start()
    }

    private fun resolveBadges() {
        val auth = currentToken() ?: return
        val state = commentsState ?: return
        if (sessionExpired || badgeJob?.isActive == true || clock() < badgeRetryAt) return
        val ids = (state.root.comments + state.replies.values.flatMap { it.comments }).map { it.userId }.distinct()
        val visible = if (visibleAuthorIds.isEmpty()) ids.take(20) else visibleAuthorIds.toList()
        val missing = visible.filter { id -> badgeCache[id]?.let { clock() - it.first < 300_000 } != true }.take(64)
        val verified = ids.filterTo(HashSet()) { badgeCache[it]?.second == true }
        if (state.verifiedAuthors != verified) commentsState = state.copy(verifiedAuthors = verified)
        if (missing.isEmpty()) return
        val session = currentSession(); val view = window
        val job = currentScope().launch(start = CoroutineStart.LAZY) {
            try {
                val resolved = badges.verified(auth, missing)
                if (!sameSession(session, auth) || view != window) return@launch
                missing.forEach { badgeCache[it] = clock() to (it in resolved) }
                while (badgeCache.size > 400) badgeCache.remove(badgeCache.keys.first())
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (sameSession(session, auth) && view == window) { badgeRetryAt = clock() + 60_000; onSessionFailure(error) }
            } finally {
                if (sameSession(session, auth) && view == window) { badgeJob = null; resolveBadges() }
            }
        }
        badgeJob = job; job.start()
    }

    private fun prune(id: String, tombstone: Boolean) {
        val state = commentsState ?: return
        val ids = mutableSetOf(id); val queue = ArrayDeque<String>(); queue.add(id)
        while (queue.isNotEmpty()) state.replies[queue.removeFirst()]?.comments?.forEach { if (ids.add(it.id)) queue.add(it.id) }
        ids.forEach { stopRead(it); changed.remove(it) }
        if (tombstone) deleted.addAll(ids)
        val closeInline = state.inlineTargetId in ids
        commentsState = state.copy(root = state.root.copy(comments = state.root.comments.filterNot { it.id in ids }),
            replies = state.replies.filterKeys { it !in ids }.mapValues { it.value.copy(comments = it.value.comments.filterNot { row -> row.id in ids }) },
            expandedIds = state.expandedIds - ids, menuId = state.menuId.takeUnless { it in ids },
            editing = if (closeInline) null else state.editing, replying = if (closeInline) null else state.replying,
            inlineDraft = if (closeInline) "" else state.inlineDraft, inlineError = if (closeInline) null else state.inlineError)
    }
    private fun thread(state: CommentsState, parent: String?) = if (parent == null) state.root else state.replies[parent] ?: CommentThread()
    private fun draftPath(state: CommentsState): Set<String> {
        val target = state.inlineTargetId ?: return emptySet()
        val parents = (state.root.comments + state.replies.values.flatMap { it.comments }).associate { it.id to it.parentId }
        val path = mutableSetOf<String>()
        var id: String? = target
        while (id != null && path.add(id)) id = parents[id]
        return path
    }
    private fun updateThread(parent: String?, block: (CommentThread) -> CommentThread) {
        commentsState = commentsState?.let { if (parent == null) it.copy(root = block(it.root))
            else it.copy(replies = it.replies + (parent to block(it.replies[parent] ?: CommentThread()))) }
    }
    private fun mapRows(block: (PostComment) -> PostComment) {
        commentsState = commentsState?.let { it.copy(root = it.root.copy(comments = it.root.comments.map(block)),
            replies = it.replies.mapValues { entry -> entry.value.copy(comments = entry.value.comments.map(block)) }) }
    }
    private fun stopRead(parent: String) {
        readVersions[parent] = ++requestSerial; reads.remove(parent)?.cancel()
        updateThread(parent) { idle(it) }
    }
    private fun cancelReads() {
        reads.values.toList().forEach { it.cancel() }; reads.clear(); readVersions.clear()
        commentsState = commentsState?.let { it.copy(root = idle(it.root), replies = it.replies.mapValues { row -> idle(row.value) }) }
    }
    private fun idle(thread: CommentThread) = thread.copy(loading = false, refreshing = false)
    private fun validRead(session: Int, auth: String, view: Int, key: String, serial: Int) =
        sameSession(session, auth) && view == window && readVersions[key] == serial
    private fun sameSession(session: Int, auth: String?) = session == currentSession() && auth == currentToken() && !sessionExpired
    private fun bumpCount(key: String) { countVersions[key] = (countVersions[key] ?: 0) + 1 }
    private fun recordCount(key: String, count: Long) { commentCounts[key] = count.coerceAtLeast(0); bumpCount(key) }
    private fun rememberChanged(row: PostComment) { changed[row.id] = ++revision to row }
    private fun badResponse() = FeedApiException("Could not read the server response. Please refresh.", FeedApiFailure.INVALID_RESPONSE)
    private fun message(error: Exception) = (error as? FeedApiException)?.message ?: "Could not complete the request. Please try again."
}
