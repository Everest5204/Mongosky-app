package com.mongosky.app.editpost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.home.*
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import kotlinx.coroutines.*

internal data class EditPostEnvironment(
    val actions: HomeFeedActions,
    val token: () -> String?,
    val scope: () -> CoroutineScope,
    val session: () -> Int,
    val expired: () -> Boolean,
    val onSessionFailure: (Exception) -> Unit,
    val onEdited: (HomePost) -> Unit,
    val notify: (String) -> Unit,
    val verified: (HomePost) -> Boolean = { false }
)

/** Shared request/session lifecycle only. Text and media drafts/controllers are separate. */
internal abstract class EditPostSessionController(environment: EditPostEnvironment) {
    protected val actions = environment.actions
    protected val token = environment.token
    protected val scope = environment.scope
    protected val session = environment.session
    protected val expired = environment.expired
    private val onSessionFailure = environment.onSessionFailure
    private val onEdited = environment.onEdited
    private val notify = environment.notify
    private val verified = environment.verified
    protected abstract fun accepts(post: HomePost): Boolean
    protected open fun onBeginRead() = Unit
    protected open fun onReset() = Unit
    protected var currentState by mutableStateOf<EditPostState?>(null)
    open val state: EditPostState? get() = currentState
    protected var version = 0L
    protected var revision = 0L
    private var readJob: Job? = null
    private var saveJob: Job? = null
    private var badgeJob: Job? = null
    protected fun active(request: Long, generation: Int) = request == version && generation == session() && !expired()

    fun open(post: HomePost) {
        if (!accepts(post) || !post.editable || expired() || token().isNullOrBlank() || state?.saving == true) return
        reset()
        currentState = EditPostState(post, verified = verified(post))
        readLatest()
        val auth = token() ?: return
        val request = version; val generation = session()
        badgeJob = scope().launch {
            try {
                val badges = actions.verified(auth, listOf(post.author.id))
                ensureActive()
                if (active(request, generation)) currentState = state?.copy(verified = post.author.id in badges)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* An optional badge read never blocks editing. */ }
        }
    }

    fun reload() {
        val current = state ?: return
        if (current.saving || current.selecting || expired()) return
        if (current.dirty && !current.discardRequested) {
            currentState = current.copy(discardRequested = true, reloadRequested = true); return
        }
        readLatest(discard = true)
    }

    private fun readLatest(discard: Boolean = false) {
        val auth = token() ?: return
        val current = state ?: return
        readJob?.cancel()
        val request = version; val generation = session(); val readRevision = revision
        onBeginRead()
        currentState = if (discard) EditPostState(current.post, verified = current.verified)
            else current.copy(loading = true, error = null, discardRequested = false)
        readJob = scope().launch {
            try {
                val fresh = actions.editable(auth, current.post)
                ensureActive()
                if (!active(request, generation)) return@launch
                require(accepts(fresh) && fresh.editable && fresh.id == current.post.id && fresh.author.id == current.post.author.id)
                val latest = state ?: return@launch
                if (revision == readRevision || !latest.dirty) {
                    currentState = EditPostState(fresh, loading = false, verified = latest.verified)
                } else if (fresh.updatedAt == current.post.updatedAt) {
                    currentState = latest.copy(post = fresh, loading = false, needsReload = false)
                } else {
                    currentState = latest.copy(loading = false, needsReload = true,
                        error = "This post changed in another session. Reload the latest post before saving.")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (active(request, generation)) {
                    onSessionFailure(error)
                    if (active(request, generation)) currentState = state?.copy(loading = false, error = message(error), needsReload = true)
                }
            }
        }
    }

    protected fun change(transform: (EditPostState) -> EditPostState) {
        val current = state ?: return
        if (!current.editable || expired()) return
        revision++
        currentState = transform(current).copy(error = null)
    }

    // Suspend boundaries are injectable for concurrency/file-cleanup tests; production uses the native file store.
    internal fun submit(prepare: suspend (EditPostState) -> PreparedMediaPost? = { null },
                        cleanup: suspend (PreparedMediaPost) -> Unit = {}) {
        val auth = token() ?: return
        val draft = state ?: return
        if (!draft.valid || expired() || saveJob?.isActive == true) return
        val request = version; val generation = session()
        var submitted = false
        currentState = draft.copy(saving = true, error = null)
        saveJob = scope().launch {
            var prepared: PreparedMediaPost? = null
            try {
                prepared = prepare(draft)
                ensureActive()
                if (!active(request, generation)) return@launch
                submitted = true
                val result = actions.edit(auth, EditPostRequest(draft, prepared?.media.orEmpty()))
                ensureActive()
                if (!active(request, generation)) return@launch
                require(accepts(result) && result.editable && result.id == draft.post.id && result.author.id == draft.post.author.id)
                onEdited(result)
                if (active(request, generation)) { currentState = null; version++; onBeginRead(); notify("Post updated") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (active(request, generation)) {
                    onSessionFailure(error)
                    if (active(request, generation)) {
                        val reload = submitted && (error !is FeedApiException || error.statusCode == 409 ||
                            error.failure in setOf(FeedApiFailure.NETWORK, FeedApiFailure.TIMEOUT,
                                FeedApiFailure.SERVER, FeedApiFailure.INVALID_RESPONSE))
                        currentState = draft.copy(error = message(error), needsReload = reload)
                    }
                }
            } finally {
                prepared?.let { upload -> withContext(NonCancellable) { try { cleanup(upload) } catch (_: Exception) { } } }
            }
        }
    }

    fun requestClose() {
        val current = state ?: return
        if (current.saving) return
        if (current.dirty) currentState = current.copy(discardRequested = true, reloadRequested = false) else reset()
    }
    fun keepEditing() { currentState = state?.copy(discardRequested = false, reloadRequested = false) }
    fun discard(reload: Boolean) { if (state?.saving != true) { if (reload) readLatest(discard = true) else reset() } }
    /** Host navigation closes clean overlays; a dirty editor asks before discarding. */
    fun closeForHost() = requestClose()
    fun reset() {
        version++; revision = 0; onReset()
        readJob?.cancel(); saveJob?.cancel(); badgeJob?.cancel()
        readJob = null; saveJob = null; badgeJob = null
        currentState = null
    }
    private fun message(error: Exception) = when (error) {
        is FeedApiException, is MediaPostFileException -> error.message ?: "Please try again."
        else -> "Could not update this post. Please try again."
    }
}
