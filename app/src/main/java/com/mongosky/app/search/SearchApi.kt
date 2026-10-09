package com.mongosky.app.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuses one connection pool. Cancellation cancels the actual HTTP call. No result disk cache. */
class SearchApi(
    private val client: OkHttpClient = sharedClient,
    baseUrl: String = BASE_URL
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    suspend fun search(token: String, query: String): SearchResult = withContext(Dispatchers.IO) {
        val normalized = SearchPolicy.normalize(query)
        if (normalized.isEmpty()) return@withContext SearchResult(emptyList(), "")
        val url = endpoint("api", "search", "users").newBuilder()
            .addQueryParameter("q", normalized)
            .addQueryParameter("limit", SearchPolicy.RESULT_LIMIT.toString()).build()
        val root = json(request(token, url))
        val rows = root.optJSONArray("users") ?: throw invalidResponse()
        if (rows.length() > SearchPolicy.RESULT_LIMIT) throw invalidResponse()
        val users = LinkedHashMap<String, SearchUser>()
        for (index in 0 until rows.length()) {
            currentCoroutineContext().ensureActive()
            val row = rows.optJSONObject(index) ?: throw invalidResponse()
            val user = parseUser(row) ?: continue
            users.putIfAbsent(user.id, user)
        }
        SearchResult(users.values.toList(), normalized)
    }

    suspend fun verifiedIds(token: String, userIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val ids = userIds.distinct().take(SearchPolicy.RESULT_LIMIT)
        if (ids.isEmpty()) return@withContext emptySet()
        val body = JSONObject().put("user_ids", JSONArray(ids)).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val root = json(request(token, endpoint("api", "verified-badges", "resolve"), body))
        val badges = root.optJSONArray("badges") ?: throw invalidResponse()
        val result = mutableSetOf<String>()
        for (index in 0 until badges.length()) {
            currentCoroutineContext().ensureActive()
            val badge = badges.optJSONObject(index) ?: continue
            val id = badge.string("user_id")
            if (badge.string("badge_type") == "verified" && id in ids) result.add(id)
        }
        result
    }

    private fun endpoint(vararg segments: String): HttpUrl = base.newBuilder()
        .apply { segments.forEach { addPathSegment(it) } }.build()

    private suspend fun request(
        token: String, url: HttpUrl, body: okhttp3.RequestBody? = null
    ): String {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw SearchException("Your session has expired. Please sign in again.", SearchFailure.SESSION_EXPIRED)
        }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store")
            .apply { if (body != null) post(body) }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(networkFailure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (it.code != 200) throw httpFailure(it)
                            val responseBody = it.body ?: throw invalidResponse()
                            if (responseBody.contentLength() > MAX_BODY_BYTES) throw invalidResponse()
                            responseBody.byteStream().use { input ->
                                val output = ByteArrayOutputStream(8_192)
                                val buffer = ByteArray(8_192)
                                while (true) {
                                    if (call.isCanceled()) throw IOException("Request canceled")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_BODY_BYTES) throw invalidResponse()
                                    output.write(buffer, 0, count)
                                }
                                output.toString("UTF-8")
                            }
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (error: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(if (error is SearchException) error else networkFailure(error))
                        }
                    }
                }
            })
        }
    }

    private fun json(text: String): JSONObject = try { JSONObject(text) }
        catch (error: JSONException) { throw invalidResponse(error) }

    private fun parseUser(row: JSONObject): SearchUser? {
        val id = (row.string("id").ifEmpty { row.string("_id") }).trim()
        if (id.isEmpty() || id.length > 128 || id.any { it.isWhitespace() || it.isISOControl() }) return null
        val image = row.string("profile_image_url").ifEmpty { row.string("profileImageURL") }
        return SearchUser(
            id = id,
            firstName = row.string("first_name").ifEmpty { row.string("firstName") }.trim(),
            lastName = row.string("last_name").ifEmpty { row.string("lastName") }.trim(),
            imageUrl = image.takeIf { it.isNotBlank() }?.let { base.resolve(it) }
                ?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }?.toString(),
            title = row.string("profile_title").ifEmpty { row.string("profileTitle") }.trim()
        )
    }

    private fun JSONObject.string(key: String): String = (opt(key) as? String).orEmpty()

    private fun httpFailure(response: Response): SearchException {
        val retry = response.header("Retry-After")?.toIntOrNull()?.coerceIn(1, 3_600) ?: 30
        return when (response.code) {
            401 -> SearchException("Your session has expired. Please sign in again.", SearchFailure.SESSION_EXPIRED)
            403 -> SearchException("Your account cannot access search right now.", SearchFailure.ACCESS_DENIED)
            429 -> SearchException("Too many requests. Please wait $retry seconds.", SearchFailure.RATE_LIMITED, retry)
            in 500..599 -> SearchException("Search is temporarily unavailable. Please try again.", SearchFailure.SERVER)
            else -> SearchException("Could not complete the search. Please try again.", SearchFailure.REJECTED)
        }
    }

    private fun networkFailure(error: IOException): SearchException = if (error is SocketTimeoutException) {
        SearchException("Search timed out. Please try again.", SearchFailure.TIMEOUT, cause = error)
    } else {
        SearchException("Please check your connection and try again.", SearchFailure.NETWORK, cause = error)
    }

    private fun invalidResponse(cause: Throwable? = null) = SearchException(
        "Could not read the search response. Please try again.", SearchFailure.INVALID_RESPONSE, cause = cause
    )

    companion object {
        const val BASE_URL = "https://api.mongosky.com/"
        private const val MAX_BODY_BYTES = 512 * 1_024
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
