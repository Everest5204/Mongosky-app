package com.mongosky.app.reportpost

import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PostReportApi(
    private val baseUrl: String = "https://api.mongosky.com/",
    private val client: OkHttpClient = sharedClient
) : PostReportSource {
    override suspend fun submit(token: String, submission: ReportSubmission) = withContext(Dispatchers.IO) {
        validate(submission)
        val body = JSONObject().put("post_id", submission.target.postId).put("post_source", submission.target.source)
            .put("reason", submission.reason.code).put("details", submission.details).put("request_id", submission.requestId)
        parse(exchange(token, "api/reports/posts", body), submission, submitting = true)
    }

    override suspend fun receipt(token: String, submission: ReportSubmission) = withContext(Dispatchers.IO) {
        validate(submission)
        parse(exchange(token, "api/reports/posts/${submission.requestId}"), submission, submitting = false)
    }

    private fun validate(value: ReportSubmission) {
        require(ReportPolicy.validTarget(value.target) && ReportPolicy.validRequest(value.requestId) && ReportPolicy.error(value.details) == null)
    }

    private fun parse(root: JSONObject, value: ReportSubmission, submitting: Boolean): ReportReceipt {
        if (root.has("error")) invalid()
        val report = root.optJSONObject("report") ?: invalid()
        fun text(key: String) = report.opt(key) as? String ?: invalid()
        val duplicate = root.opt("already_reported") as? Boolean ?: invalid()
        val id = text("id"); val request = text("request_id")
        val reason = ReportReason.fromCode(text("reason")) ?: invalid()
        val status = text("status")
        val received = runCatching { Instant.parse(text("received_at")) }.getOrElse { invalid() }
        if (!ReportPolicy.validId(id) || !ReportPolicy.validRequest(request) ||
            text("post_id") != value.target.postId || text("post_source") != value.target.source ||
            status !in setOf("pending", "in_review", "reviewed", "closed") ||
            (!submitting && request != value.requestId) ||
            (!duplicate && (request != value.requestId || reason != value.reason))) invalid()
        return ReportReceipt(id, request, value.target.postId, value.target.source, reason, status, received, duplicate)
    }

    private suspend fun exchange(token: String, path: String, json: JSONObject? = null): JSONObject = suspendCancellableCoroutine { continuation ->
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            continuation.resumeWithException(FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED, 401))
            return@suspendCancellableCoroutine
        }
        val builder = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").header("Cache-Control", "no-store")
        if (json != null) {
            val encoded = json.toString()
            builder.post(object : RequestBody() {
                override fun contentType() = "application/json; charset=utf-8".toMediaType()
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.writeUtf8(encoded) }
            })
        }
        val call = client.newCall(builder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(FeedApiException(
                    "Could not confirm the report. Check your connection and try again.",
                    if (e is SocketTimeoutException) FeedApiFailure.TIMEOUT else FeedApiFailure.NETWORK, cause = e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = response.use { r ->
                        if (r.code != 200 && !(json != null && r.code == 201)) throw httpError(r)
                        val body = r.body ?: invalid()
                        if (body.contentLength() > MAX_BODY) invalid()
                        val source = body.source()
                        if (source.request(MAX_BODY + 1)) invalid()
                        runCatching { JSONObject(source.readUtf8()) }.getOrElse { invalid() }
                    }
                    if (continuation.isActive) continuation.resume(root)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun httpError(response: Response): FeedApiException {
        val code = response.code
        val retry = response.header("Retry-After")?.toIntOrNull()?.coerceIn(1, 3600)
        val (message, failure) = when (code) {
            401 -> "Please sign in again." to FeedApiFailure.SESSION_EXPIRED
            403 -> "This post cannot be reported from your account." to FeedApiFailure.ACCESS_DENIED
            404 -> "This post or report receipt is no longer available." to FeedApiFailure.REQUEST_REJECTED
            405 -> "Reports are not available yet. Try again after the backend update." to FeedApiFailure.REQUEST_REJECTED
            409 -> "This report request changed. Close and reopen Report." to FeedApiFailure.REQUEST_REJECTED
            429 -> "You have sent several reports. Please try again later." to FeedApiFailure.RATE_LIMITED
            400 -> "Check your report details and try again." to FeedApiFailure.REQUEST_REJECTED
            else -> "Could not confirm the report. Check its receipt before trying again." to FeedApiFailure.SERVER
        }
        return FeedApiException(message, failure, code, retry)
    }

    private fun invalid(): Nothing = throw FeedApiException("Could not confirm the response. Check your report receipt.", FeedApiFailure.INVALID_RESPONSE)

    companion object {
        private const val MAX_BODY = 16_384L
        private val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
