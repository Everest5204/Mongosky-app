package com.mongosky.app.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset
import com.mongosky.app.editpost.EditPostController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Per-author state prevents one Follow/badge response from redrawing every photo. */
internal class HomeFeedController(
    private val actions: HomeFeedActions,
    private val token: () -> String?,
    private val scope: () -> CoroutineScope,
    private val session: () -> Int,
    private val expired: () -> Boolean,
    private val onSessionFailure: (Exception) -> Unit,
    private val onEdited: (HomePost) -> Unit,
    private val onDeleted: (HomePost) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val authors = linkedMapOf<String, androidx.compose.runtime.MutableState<HomeAuthorState>>()
    private val badgeReadAt = linkedMapOf<String, Long>()
    private val pendingBadges = mutableSetOf<String>()
    private val relationReadAt = mutableMapOf<String, Long>()
    private val relationJobs = mutableMapOf<String, Job>()
    private val relationSlots = Semaphore(2)
    private var foreground = false
    private val followJobs = mutableMapOf<String, Job>()
    private val confirmedRevision = mutableMapOf<String, Long>()
    var relationVersion = 0L
        private set
    var authorGeneration by mutableStateOf(0)
        private set
    private var deleteJob: Job? = null
    private var overlayVersion = 0
    private val noticeChannel = Channel<String>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()
    var menu by mutableStateOf<HomePost?>(null)
        private set
    internal val postEditor = EditPostController(actions, token, scope, session, expired, onSessionFailure,
        onEdited, ::notify, verified = { author(it).value.verified })
    val editor get() = postEditor.state
    var deletion by mutableStateOf<HomeDeleteState?>(null)
        private set

    fun author(post: HomePost): State<HomeAuthorState> = authors.getOrPut(post.author.id) {
        mutableStateOf(HomeAuthorState(following = post.following))
    }

    fun ingest(posts: List<HomePost>, readRevision: Long) {
        posts.forEach { post ->
            val holder = authors.getOrPut(post.author.id) { mutableStateOf(HomeAuthorState(following = post.following)) }
            if (!holder.value.pending && (confirmedRevision[post.author.id] ?: 0L) <= readRevision) {
                holder.value = holder.value.copy(following = post.following,
                    confirmation = holder.value.confirmation && post.following)
                relationReadAt[post.author.id] = clock()
            }
        }
    }

    /** One bounded badge batch for settled visible rows, never one lookup per card. */
    fun visible(posts: List<HomePost>) {
        val auth = token() ?: return
        if (expired()) return
        refreshRelations(posts)
        val now = clock()
        val ids = posts.map { it.author.id }.distinct().filter {
            HomeFeedPolicy.validId(it) && it !in pendingBadges &&
                badgeReadAt[it]?.let { at -> now - at < HomeFeedPolicy.BADGE_TTL_MILLIS } != true
        }.take(200)
        if (ids.isEmpty()) return
        val generation = session()
        pendingBadges.addAll(ids)
        scope().launch {
            try {
                val verified = actions.verified(auth, ids)
                ensureActive()
                if (generation != session()) return@launch
                ids.forEach { id ->
                    badgeReadAt[id] = clock()
                    authors[id]?.let { it.value = it.value.copy(verified = id in verified) }
                }
                while (badgeReadAt.size > 500) badgeReadAt.remove(badgeReadAt.keys.first())
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == session()) {
                    onSessionFailure(error)
                    // A failed optional badge request cannot block the feed or invent verification.
                }
            } finally { if (generation == session()) pendingBadges.removeAll(ids.toSet()) }
        }
    }

    fun setForeground(active: Boolean) {
        if (active && !foreground) relationReadAt.clear()
        foreground = active
        if (!active) { relationJobs.values.forEach { it.cancel() }; relationJobs.clear() }
    }

    private fun refreshRelations(posts: List<HomePost>) {
        val auth = token() ?: return
        if (!foreground) return
        for (post in posts.distinctBy { it.author.id }) {
            val id = post.author.id
            val holder = authors[id] ?: continue
            if (post.isOwn || !HomeFeedPolicy.validId(id) || holder.value.pending || relationJobs[id]?.isActive == true ||
                relationReadAt[id]?.let { clock() - it < HomeFeedPolicy.BADGE_TTL_MILLIS } == true) continue
            val generation = session()
            val revision = relationVersion
            val job = scope().launch(start = CoroutineStart.LAZY) {
                try {
                    val following = relationSlots.withPermit { actions.relationship(auth, id) }
                    ensureActive()
                    if (generation == session() && foreground && !holder.value.pending &&
                        (confirmedRevision[id] ?: 0) <= revision && following != null) {
                        holder.value = holder.value.copy(following = following, confirmation = false)
                    }
                    if (generation == session()) relationReadAt[id] = clock()
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (generation == session()) {
                        onSessionFailure(error)
                        relationReadAt[id] = clock() - HomeFeedPolicy.BADGE_TTL_MILLIS + HomeFeedPolicy.POLL_MILLIS
                    }
                } finally { if (relationJobs[id] === coroutineContext[Job]) relationJobs.remove(id) }
            }
            relationJobs[id] = job; job.start()
        }
    }

    fun follow(post: HomePost) {
        val auth = token() ?: return
        val id = post.author.id
        if (expired() || post.isOwn || !HomeFeedPolicy.validId(id)) return
        val holder = authors.getOrPut(id) { mutableStateOf(HomeAuthorState(following = post.following)) }
        if (holder.value.following || holder.value.pending || followJobs[id]?.isActive == true) return
        val generation = session()
        holder.value = holder.value.copy(pending = true)
        val job = scope().launch(start = CoroutineStart.LAZY) {
            try {
                // PUT means "following = true"; retrying an uncertain response cannot unfollow.
                actions.follow(auth, id)
                ensureActive()
                if (generation != session()) return@launch
                confirmedRevision[id] = ++relationVersion
                holder.value = holder.value.copy(following = true, pending = false, confirmation = true)
                delay(HomeFeedPolicy.FOLLOW_CONFIRM_MILLIS)
                if (generation == session()) holder.value = holder.value.copy(confirmation = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == session()) {
                    onSessionFailure(error)
                    holder.value = holder.value.copy(pending = false)
                    notify(message(error))
                }
            } finally { if (generation == session()) followJobs.remove(id) }
        }
        followJobs[id] = job
        job.start()
    }

    fun openMenu(post: HomePost) { if (!expired()) { closeOverlays(); menu = post } }
    fun closeMenu() { menu = null }
    fun unavailable(action: HomeMenuAction) { closeMenu(); notify("${action.label} is coming soon.") }
    fun notify(message: String) { noticeChannel.trySend(message) }

    fun openEditor(post: HomePost) {
        if (!post.editable || expired() || token() == null || editor?.saving == true) return
        closeMenu(); closeDelete()
        postEditor.open(post)
    }
    fun reloadEditor() = postEditor.reload()
    fun editText(text: String) = postEditor.text(text)
    fun editPreset(preset: TextPostPreset) = postEditor.preset(preset)
    fun editStyle(style: FeedTextStyle) = postEditor.style(style)
    fun removeMedia(key: String) = postEditor.removeMedia(key)
    fun saveEditor(files: MediaPostFileStore? = null) = postEditor.save(files)

    fun openDelete(post: HomePost) {
        if (!post.isOwn || expired() || token() == null || deletion?.busy == true) return
        closeOverlays(); deletion = HomeDeleteState(post)
    }

    fun confirmDelete() {
        val auth = token() ?: return
        val state = deletion ?: return
        if (state.busy || expired()) return
        val generation = session()
        val request = overlayVersion
        deletion = state.copy(busy = true, error = null)
        deleteJob = scope().launch {
            try {
                actions.delete(auth, state.post.id)
                ensureActive()
                if (generation != session()) return@launch
                onDeleted(state.post)
                if (request == overlayVersion) deletion = null
                notify("Post deleted")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == session() && request == overlayVersion) {
                    onSessionFailure(error)
                    // A server 404 confirms the previously owned post is no longer available.
                    if (error is FeedApiException && error.statusCode == 404) {
                        onDeleted(state.post); deletion = null; notify("Post is no longer available")
                    } else {
                        val unknown = error is FeedApiException && error.failure in setOf(
                            FeedApiFailure.TIMEOUT, FeedApiFailure.NETWORK, FeedApiFailure.SERVER,
                            FeedApiFailure.INVALID_RESPONSE)
                        deletion = state.copy(error = message(error), uncertain = state.uncertain || unknown)
                    }
                }
            }
        }
    }

    fun closeEditor() = postEditor.requestClose()
    fun closeDelete() { if (deletion?.busy != true) deletion = null }
    fun closeOverlays() { closeMenu(); closeEditor(); closeDelete() }

    fun endSession() {
        overlayVersion++
        postEditor.reset()
        authorGeneration++
        followJobs.values.forEach { it.cancel() }; followJobs.clear()
        deleteJob?.cancel()
        relationJobs.values.forEach { it.cancel() }; relationJobs.clear(); relationReadAt.clear()
        authors.values.forEach { it.value = HomeAuthorState() }
        authors.clear(); badgeReadAt.clear(); pendingBadges.clear(); confirmedRevision.clear(); relationVersion = 0
        menu = null; deletion = null
        while (noticeChannel.tryReceive().isSuccess) { }
    }

    private fun message(error: Exception) = if (error is FeedApiException) error.message ?: "Please try again."
        else "Could not complete the request. Please try again."
}
