package com.mongosky.app.pinpost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.post.*
import java.util.UUID
import kotlinx.coroutines.*

/** One screen-owned pin state. An uncertain mutation is frozen until its receipt is checked. */
internal class PinPostController(
    private val source: PinPostSource, private val token: () -> String?, private val scope: () -> CoroutineScope,
    private val session: () -> Int, private val expired: () -> Boolean, private val onSessionFailure: (Exception) -> Unit,
    private val notify: (String) -> Unit = {}, private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val requestId: () -> String = { UUID.randomUUID().toString() }
) {
    var snapshot by mutableStateOf<PinSnapshot?>(null); private set
    var dialog by mutableStateOf<PinDialogState?>(null); private set
    private var pending: PinDialogState? = null
    private var auth: String? = null
    private var owner = ""
    private var epoch = 0
    private var readAt = Long.MIN_VALUE
    private var readJob: Job? = null
    private var actionJob: Job? = null
    private var generation = 0
    private var readVersion = 0
    val busy get() = dialog?.busy == true

    private fun bind(id: String): Boolean {
        val saved = token()?.takeIf { it.isNotBlank() } ?: return false
        if (!PinPolicy.validId(id) || expired()) return false
        if (auth != saved || owner != id || epoch != session()) {
            endSession(); auth = saved; owner = id; epoch = session()
        }
        return true
    }
    private fun current(version: Int, saved: String) = generation == version && auth == saved && token() == saved && epoch == session() && !expired()
    private fun live(version: Int, saved: String): Boolean {
        val valid = current(version, saved)
        if (!valid && generation == version) endSession()
        return valid
    }
    fun refresh(id: String, force: Boolean = false) {
        if (!bind(id) || busy || pending?.attempt != null || dialog?.attempt != null || readJob?.isActive == true) return
        if (!force && snapshot != null && clock() - readAt < CACHE_MILLIS) return
        val saved = auth ?: return; val version = generation; val request = ++readVersion
        val job = scope().launch(start = CoroutineStart.LAZY) {
            try {
                if (!live(version, saved)) return@launch
                val result = source.read(saved, id); ensureActive()
                if (live(version, saved) && request == readVersion) {
                    require(result.ownerId == id)
                    if (snapshot?.let { result.revision < it.revision } == true) return@launch
                    snapshot = result; readAt = clock()
                    dialog?.takeIf { it.attempt == null }?.let { dialog = it.copy(baseline = result, loading = false, needsRefresh = false, error = null) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (live(version, saved) && request == readVersion) {
                if (sessionError(error)) { endSession(); onSessionFailure(error) }
                else dialog = dialog?.copy(loading = false, error = error.message ?: "Could not load your profile pin.", needsRefresh = true)
            } } finally { if (readJob === coroutineContext[Job]) readJob = null }
        }
        readJob = job; job.start()
    }
    fun open(target: PinTarget) {
        if (!PinPolicy.valid(target) || busy) return
        if (!bind(target.ownerId)) { notify("Please sign in again."); return }
        pending?.let { dialog = it; pending = null; return }
        val cached = snapshot?.takeIf { clock() - readAt < CACHE_MILLIS }
        dialog = PinDialogState(target, baseline = cached, loading = cached == null)
        if (cached == null) refresh(target.ownerId, force = true)
    }
    fun label(post: HomePost): String = PinTarget.from(post)?.let { if (snapshot?.contains(it) == true) "Unpin from profile" else "Pin to profile" } ?: "Pin to profile"
    fun confirm() {
        val state = dialog ?: return
        if (state.busy || state.loading || state.uncertain || state.needsRefresh || remainingCooldownMillis() > 0) return
        val baseline = state.baseline ?: return
        val attempt = state.attempt ?: PinAttempt(state.target, !state.unpin, baseline.revision, requestId())
        if (!PinPolicy.validRequest(attempt.requestId)) { dialog = state.copy(error = "Please reopen this action."); return }
        run(attempt, checking = false)
    }
    fun check() { dialog?.takeIf { !it.busy && it.attempt != null }?.attempt?.let { run(it, checking = true) } }
    fun retryRead() {
        val state = dialog ?: return
        if (state.busy || state.attempt != null || remainingCooldownMillis() > 0) return
        dialog = state.copy(loading = true, error = null)
        refresh(state.target.ownerId, force = true)
    }
    private fun run(attempt: PinAttempt, checking: Boolean) {
        val state = dialog ?: return; val saved = auth ?: return; val version = generation
        if (!current(version, saved)) { endSession(); return }
        readVersion++; readJob?.cancel(); readJob = null
        dialog = state.copy(busy = true, attempt = attempt, error = null)
        val job = scope().launch(start = CoroutineStart.LAZY) {
            try {
                if (!live(version, saved)) return@launch
                val result = if (checking) source.receipt(saved, attempt) else source.update(saved, attempt)
                ensureActive()
                if (!live(version, saved)) return@launch
                if (result.attempt != attempt || result.resultRevision != attempt.expectedRevision + 1 ||
                    result.current.ownerId != owner || result.current.revision < result.resultRevision) throw invalidReceipt()
                snapshot = result.current; readAt = clock(); dialog = null; pending = null
                val applied = if (attempt.pinned) result.current.contains(attempt.target) else !result.current.contains(attempt.target)
                notify(if (!applied) "Update received. Your profile pin changed in another session." else if (attempt.pinned) "Post pinned to profile" else "Post unpinned")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (live(version, saved)) {
                if (sessionError(error)) { endSession(); onSessionFailure(error) }
                else if (checking && (error as? FeedApiException)?.statusCode == 404) {
                    dialog = dialog?.copy(busy = false, uncertain = false, error = "No receipt yet. Retry the same update.")
                } else {
                    val definite = !checking && (error as? FeedApiException)?.statusCode in setOf(400, 403, 404, 409, 429)
                    val retryAt = if ((error as? FeedApiException)?.statusCode == 429) clock() + (error.retryAfterSeconds ?: 60).coerceIn(1, 3600) * 1000L else 0
                    dialog = dialog?.copy(busy = false, uncertain = !definite, attempt = if (definite) null else attempt,
                        needsRefresh = definite, retryAtMillis = retryAt, error = if (definite) error.message else "Check whether your profile pin was updated.")
                }
            } } finally { if (actionJob === coroutineContext[Job]) actionJob = null }
        }
        actionJob = job; job.start()
    }
    fun dismiss(force: Boolean = false): Boolean {
        val state = dialog ?: return true
        if (state.busy && !force) return false
        if (state.attempt != null) pending = state.copy(busy = false, uncertain = state.uncertain || state.busy)
        generation++; actionJob?.cancel(); actionJob = null; readJob?.cancel(); readJob = null; dialog = null
        return true
    }
    fun endSession() {
        generation++; readJob?.cancel(); actionJob?.cancel(); readJob = null; actionJob = null
        dialog = null; pending = null; snapshot = null; auth = null; owner = ""; epoch = 0; readAt = Long.MIN_VALUE
    }
    fun postDeleted(post: HomePost) {
        val target = PinTarget.from(post) ?: return
        if (snapshot?.contains(target) == true) {
            readVersion++; readJob?.cancel(); readJob = null
            snapshot = snapshot?.copy(post = null, pinnedAt = null)
        }
        if (dialog?.target == target) dismiss(force = true)
        if (pending?.target == target) pending = null
    }
    fun remainingCooldownMillis() = ((dialog?.retryAtMillis ?: 0) - clock()).coerceAtLeast(0)
    private fun sessionError(error: Exception) = (error as? FeedApiException)?.failure == FeedApiFailure.SESSION_EXPIRED
    private fun invalidReceipt() = FeedApiException("Could not confirm the update.", FeedApiFailure.INVALID_RESPONSE)
    companion object { const val CACHE_MILLIS = 20_000L }
}
