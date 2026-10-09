package com.mongosky.app.home

import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset
import java.util.UUID
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
    private var editJob: Job? = null
    private var selectionJob: Job? = null
    private var deleteJob: Job? = null
    private var overlayVersion = 0
    private var selectionVersion = 0
    private val noticeChannel = Channel<String>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()
    var menu by mutableStateOf<HomePost?>(null)
        private set
    var editor by mutableStateOf<HomeEditState?>(null)
        private set
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
        closeOverlays()
        editor = HomeEditState(post)
        reloadEditor()
    }

    fun reloadEditor() {
        val auth = token() ?: return
        val old = editor ?: return
        if (old.saving || expired()) return
        editJob?.cancel(); selectionJob?.cancel(); selectionVersion++
        val request = ++overlayVersion
        val generation = session()
        editor = old.copy(loading = true, selecting = false, error = null)
        editJob = scope().launch {
            try {
                val fresh = actions.editable(auth, old.post)
                ensureActive()
                if (request == overlayVersion && generation == session()) editor = HomeEditState(fresh, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (request == overlayVersion && generation == session()) {
                    onSessionFailure(error)
                    editor = editor?.copy(loading = false, error = message(error), needsReload = true)
                }
            }
        }
    }

    fun editText(text: String) { changeEditor { it.copy(text = text, error = null) } }
    fun editPreset(preset: TextPostPreset) { changeEditor { it.copy(background = preset.background, textColor = preset.textColor) } }
    fun editStyle(style: FeedTextStyle) { changeEditor { it.copy(textStyle = style) } }
    fun removeMedia(key: String) {
        changeEditor { it.copy(keptMedia = it.keptMedia.filterNot { media -> (media.id ?: media.url) == key },
            selectedMedia = it.selectedMedia.filterNot { media -> media.id == key }, error = null) }
    }
    private inline fun changeEditor(change: (HomeEditState) -> HomeEditState) {
        val value = editor ?: return
        if (!value.busy && !value.needsReload && !expired()) editor = change(value)
    }

    fun addPhotos(uris: List<Uri>, files: MediaPostFileStore) {
        val state = editor ?: return
        if (state.busy || state.needsReload || state.post !is HomePost.Media || expired()) return
        val selection = uris.distinct().filter { uri -> state.selectedMedia.none { it.uri == uri.toString() } }
        if (selection.isEmpty()) return
        if (state.keptMedia.size + state.selectedMedia.size + selection.size > 10) {
            editor = state.copy(error = "You can keep up to 10 photos."); return
        }
        val version = ++selectionVersion
        val request = overlayVersion
        val generation = session()
        editor = state.copy(selecting = true, error = null)
        selectionJob = scope().launch {
            try {
                val chosen = files.inspect(selection)
                ensureActive()
                require(chosen.all { it.kind == PostMediaKind.IMAGE }) { "Please select photos for this post." }
                if (version == selectionVersion && request == overlayVersion && generation == session()) {
                    editor = editor?.copy(selecting = false, selectedMedia = state.selectedMedia + chosen)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == selectionVersion && request == overlayVersion && generation == session()) {
                    editor = editor?.copy(selecting = false, error = error.message ?: "Could not read these photos.")
                }
            }
        }
    }

    fun saveEditor(files: MediaPostFileStore? = null) {
        val auth = token() ?: return
        val draft = editor ?: return
        if (!draft.valid || expired()) return
        val request = overlayVersion
        val generation = session()
        editor = draft.copy(saving = true, error = null)
        editJob = scope().launch {
            var prepared: PreparedMediaPost? = null
            try {
                if (draft.selectedMedia.isNotEmpty()) {
                    val store = requireNotNull(files)
                    prepared = store.prepare(MediaPostDraft("home-edit-${UUID.randomUUID()}", draft.text,
                        draft.selectedMedia), draft.post.author.id)
                }
                val result = actions.edit(auth, HomeEditRequest(draft, prepared?.media.orEmpty()))
                ensureActive()
                if (generation != session()) return@launch
                onEdited(result)
                if (request == overlayVersion) { editor = null; overlayVersion++ }
                notify("Post updated")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (request == overlayVersion && generation == session()) {
                    onSessionFailure(error)
                    val reload = error is FeedApiException && (error.statusCode == 409 ||
                        error.failure in setOf(FeedApiFailure.NETWORK, FeedApiFailure.TIMEOUT,
                            FeedApiFailure.SERVER, FeedApiFailure.INVALID_RESPONSE))
                    editor = draft.copy(error = message(error), needsReload = reload)
                }
            } finally {
                prepared?.let { upload -> withContext(NonCancellable) {
                    try { files?.delete(upload) } catch (_: Exception) { }
                } }
            }
        }
    }

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

    fun closeEditor() { if (editor?.saving != true) { overlayVersion++; selectionVersion++; editJob?.cancel(); selectionJob?.cancel(); editor = null } }
    fun closeDelete() { if (deletion?.busy != true) deletion = null }
    fun closeOverlays() { closeMenu(); closeEditor(); closeDelete() }

    fun endSession() {
        overlayVersion++; selectionVersion++
        authorGeneration++
        followJobs.values.forEach { it.cancel() }; followJobs.clear()
        editJob?.cancel(); selectionJob?.cancel(); deleteJob?.cancel()
        relationJobs.values.forEach { it.cancel() }; relationJobs.clear(); relationReadAt.clear()
        authors.values.forEach { it.value = HomeAuthorState() }
        authors.clear(); badgeReadAt.clear(); pendingBadges.clear(); confirmedRevision.clear(); relationVersion = 0
        menu = null; editor = null; deletion = null
        while (noticeChannel.tryReceive().isSuccess) { }
    }

    private fun message(error: Exception) = if (error is FeedApiException) error.message ?: "Please try again."
        else "Could not complete the request. Please try again."
}
