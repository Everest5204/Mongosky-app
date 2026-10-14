package com.mongosky.app.reportpost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** One screen-owned report, with account-scoped memory drafts and durable receipts. */
internal class PostReportController(
    private val source: PostReportSource,
    private val token: () -> String?,
    private val scope: () -> CoroutineScope,
    private val session: () -> Int,
    private val expired: () -> Boolean,
    private val onSessionFailure: (Exception) -> Unit,
    private val notify: (String) -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val newRequestId: () -> String = { UUID.randomUUID().toString() }
) {
    var state by mutableStateOf<PostReportState?>(null)
        private set
    private val drafts = linkedMapOf<String, PostReportState>()
    private var cacheToken: String? = null
    private var activeToken: String? = null
    private var activeSession = -1
    private var generation = 0
    private var job: Job? = null

    fun open(target: ReportTarget): Boolean {
        if (state?.busy == true || !ReportPolicy.validTarget(target)) return false
        val auth = token()?.takeIf { it.isNotBlank() }
        if (auth == null || expired()) { notify("Please sign in again."); return false }
        if (cacheToken != auth) { endSession(); cacheToken = auth }
        dismiss(force = true)
        activeToken = auth; activeSession = session()
        val remembered = drafts[target.key] ?: PostReportState(target)
        state = remembered.copy(target = target, busy = false, checking = false, unavailable = false,
            error = if (remembered.unavailable) null else remembered.error)
        return true
    }

    fun choose(reason: ReportReason) {
        val old = editable() ?: return
        state = old.copy(reason = reason, stage = ReportStage.REVIEW, error = null)
    }

    fun updateDetails(value: String) {
        val old = editable() ?: return
        state = old.copy(details = ReportPolicy.limit(value), error = null)
    }

    fun back() {
        val old = state ?: return
        if (old.busy) return
        if (old.stage == ReportStage.REVIEW && !old.locked) state = old.copy(stage = ReportStage.REASONS, error = null)
        else dismiss()
    }

    fun dismiss(force: Boolean = false): Boolean {
        val old = state ?: return true
        if (old.busy && !force) return false
        remember(if (old.busy) old.copy(busy = false, checking = false, uncertain = old.attempt != null,
            error = "Check whether your report was received.") else old)
        generation++; job?.cancel(); job = null; state = null
        return true
    }

    fun submit() {
        val old = state ?: return
        val reason = old.reason ?: return
        if (old.busy || old.uncertain || old.unavailable || old.stage != ReportStage.REVIEW || clock() < old.retryAtMillis) return
        val auth = currentAuth() ?: return
        val details = old.details.trim()
        ReportPolicy.error(details)?.let { state = old.copy(error = it); return }
        val attempt = old.attempt ?: ReportSubmission(old.target, newRequestId(), reason, details)
        if (!ReportPolicy.validRequest(attempt.requestId)) { state = old.copy(error = "Could not prepare the report. Please reopen it."); return }
        val version = generation
        state = old.copy(busy = true, checking = false, attempt = attempt, details = attempt.details, error = null)
        run {
            try {
                if (!current(auth, version)) return@run
                val receipt = source.submit(auth, attempt)
                ensureActive()
                if (current(auth, version)) received(receipt, attempt)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(auth, version)) failed(error, checking = false) }
        }
    }

    fun checkReceipt() {
        val old = state ?: return
        val attempt = old.attempt ?: return
        if (old.busy || old.stage != ReportStage.REVIEW) return
        val auth = currentAuth() ?: return
        val version = generation
        state = old.copy(busy = true, checking = true, error = null)
        run {
            try {
                if (!current(auth, version)) return@run
                val receipt = source.receipt(auth, attempt)
                ensureActive()
                if (current(auth, version)) received(receipt, attempt)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(auth, version)) failed(error, checking = true) }
        }
    }

    fun rateLimitFinished() {
        state?.let { if (it.retryAtMillis > 0 && clock() >= it.retryAtMillis) state = it.copy(retryAtMillis = 0) }
    }

    fun remainingCooldownMillis() = ((state?.retryAtMillis ?: 0) - clock()).coerceAtLeast(0)

    fun endSession() {
        generation++; job?.cancel(); job = null
        state = null; drafts.clear(); activeToken = null; cacheToken = null; activeSession = -1
    }

    private fun editable(): PostReportState? = state?.takeIf { !it.busy && !it.locked && !it.unavailable && it.stage != ReportStage.RECEIVED && currentAuth() != null }

    private fun currentAuth(): String? {
        val auth = activeToken
        if (auth == null || token() != auth || session() != activeSession) {
            endSession(); notify("Your account changed. Reopen Report to continue."); return null
        }
        if (expired()) { endSession(); return null }
        return auth
    }

    private fun current(auth: String, version: Int): Boolean {
        if (version != generation || state == null) return false
        return currentAuth() == auth
    }

    private fun received(receipt: ReportReceipt, attempt: ReportSubmission) {
        if (receipt.postId != attempt.target.postId || receipt.source != attempt.target.source ||
            !ReportPolicy.validId(receipt.id) || !ReportPolicy.validRequest(receipt.requestId) ||
            receipt.status !in setOf("pending", "in_review", "reviewed", "closed") ||
            (!receipt.alreadyReported && (receipt.requestId != attempt.requestId || receipt.reason != attempt.reason))) {
            failed(FeedApiException("Could not confirm the report. Check its receipt.", FeedApiFailure.INVALID_RESPONSE), false)
            return
        }
        val old = state ?: return
        val done = old.copy(stage = ReportStage.RECEIVED, busy = false, checking = false,
            uncertain = false, details = "", attempt = null, receipt = receipt, error = null, retryAtMillis = 0)
        state = done; remember(done)
    }

    private fun failed(error: Exception, checking: Boolean) {
        val old = state ?: return
        val api = error as? FeedApiException
        if (api?.failure == FeedApiFailure.SESSION_EXPIRED) {
            onSessionFailure(error); endSession(); notify("Please sign in again."); return
        }
        if (checking && api?.statusCode == 404) {
            state = old.copy(busy = false, checking = false, uncertain = false,
                error = "No receipt was found yet. You can safely retry the same report.")
            return
        }
        val unknown = checking || api == null || api.failure in setOf(FeedApiFailure.NETWORK,
            FeedApiFailure.TIMEOUT, FeedApiFailure.SERVER, FeedApiFailure.INVALID_RESPONSE)
        state = old.copy(busy = false, checking = false, uncertain = unknown,
            attempt = if (unknown || checking) old.attempt else null,
            unavailable = !checking && api?.statusCode in setOf(403, 404, 409),
            retryAtMillis = if (api?.failure == FeedApiFailure.RATE_LIMITED) clock() + (api.retryAfterSeconds ?: 60) * 1000L else old.retryAtMillis,
            error = api?.message ?: "Could not confirm the report. Check its receipt.")
    }

    private fun remember(value: PostReportState) {
        if (cacheToken != activeToken || cacheToken == null) return
        drafts.remove(value.target.key); drafts[value.target.key] = value
        while (drafts.size > 24) drafts.remove(drafts.keys.first())
    }

    private fun run(block: suspend CoroutineScope.() -> Unit) {
        val next = scope().launch(start = CoroutineStart.LAZY) {
            try { block() } finally { if (job === coroutineContext[Job]) job = null }
        }
        job = next; next.start()
    }
}
