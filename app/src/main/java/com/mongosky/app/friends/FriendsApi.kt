package com.mongosky.app.friends

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

/** One connection pool, bounded responses and cancellation of the actual HTTP call. */
class FriendsApi(private val client: OkHttpClient = sharedClient, baseUrl: String = BASE_URL) {
    private val base = baseUrl.toHttpUrl()

    suspend fun page(token: String, tab: FriendsTab, cursor: String? = null): FriendsPage = withContext(Dispatchers.IO) {
        if (cursor != null && FriendsPolicy.userId(cursor) != cursor) throw invalidResponse()
        val url = endpoint("api", "follow", tab.path).newBuilder()
            .addQueryParameter("limit", FriendsPolicy.PAGE_SIZE.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }.build()
        val root = json(request(token, url))
        val rows = root.optJSONArray("items") ?: throw invalidResponse()
        if (rows.length() > FriendsPolicy.PAGE_SIZE) throw invalidResponse()
        val users = LinkedHashMap<String, FriendsUser>()
        for (index in 0 until rows.length()) {
            currentCoroutineContext().ensureActive()
            val row = rows.optJSONObject(index) ?: throw invalidResponse()
            val id = FriendsPolicy.userId(row.string("id")) ?: continue
            val image = row.string("profile_image_url").takeIf { it.isNotBlank() && it.length <= 2_048 }
                ?.let(base::resolve)?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }
            users.putIfAbsent(id, FriendsUser(
                id = id, firstName = row.string("first_name").trim().take(256),
                lastName = row.string("last_name").trim().take(256),
                imageUrl = image?.toString(), title = row.string("profile_title").trim().take(512),
                isFollowing = row.boolean("is_following"), followsYou = row.boolean("follows_you"),
                isSelf = row.boolean("is_self")
            ))
        }
        val hasMore = root.boolean("has_more")
        val next = root.string("next_cursor").ifBlank { null }
        if (hasMore && (next == null || FriendsPolicy.userId(next) != next || next == cursor)) throw invalidResponse()
        FriendsPage(users.values.toList(), if (hasMore) next else null, hasMore,
            root.count("followers_count"), root.count("following_count"))
    }

    suspend fun setFollowing(token: String, userId: String, following: Boolean): FriendsFollowResult = withContext(Dispatchers.IO) {
        if (FriendsPolicy.userId(userId) != userId) throw FriendsException("This profile is unavailable.", FriendsFailure.REJECTED)
        val root = json(request(token, endpoint("api", "users", userId, "follow"),
            method = if (following) "PUT" else "DELETE", body = if (following) "{}" else null))
        val actual = root.boolean("is_following")
        if (actual != following) throw invalidResponse()
        FriendsFollowResult(actual)
    }

    suspend fun verifiedIds(token: String, ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val valid = ids.distinct().filter { FriendsPolicy.userId(it) == it }.take(FriendsPolicy.PAGE_SIZE)
        if (valid.isEmpty()) return@withContext emptySet()
        val root = json(request(token, endpoint("api", "verified-badges", "resolve"), "POST",
            JSONObject().put("user_ids", JSONArray(valid)).toString()))
        val badges = root.optJSONArray("badges") ?: throw invalidResponse()
        buildSet {
            for (index in 0 until badges.length()) {
                currentCoroutineContext().ensureActive()
                val row = badges.optJSONObject(index) ?: continue
                val id = row.string("user_id")
                if (id in valid && row.string("badge_type") == "verified") add(id)
            }
        }
    }

    private fun endpoint(vararg segments: String): HttpUrl = base.newBuilder()
        .apply { segments.forEach { addPathSegment(it) } }.build()

    private suspend fun request(token: String, url: HttpUrl, method: String = "GET", body: String? = null): String {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw FriendsException("Your session has expired. Please sign in again.", FriendsFailure.SESSION_EXPIRED)
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").header("Cache-Control", "no-cache, no-store")
            .method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
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
                        if (continuation.isActive) continuation.resumeWithException(
                            if (error is FriendsException) error else networkFailure(error))
                    }
                }
            })
        }
    }

    private fun json(text: String): JSONObject = try { JSONObject(text) }
        catch (error: JSONException) { throw invalidResponse(error) }
    private fun JSONObject.string(key: String): String = (opt(key) as? String).orEmpty()
    private fun JSONObject.boolean(key: String): Boolean = (opt(key) as? Boolean) ?: throw invalidResponse()
    private fun JSONObject.count(key: String): Long {
        val number = opt(key) as? Number ?: throw invalidResponse()
        val value = number.toLong()
        if (value < 0 || number.toDouble() != value.toDouble()) throw invalidResponse()
        return value
    }
    private fun httpFailure(response: Response): FriendsException {
        val retry = response.header("Retry-After")?.toIntOrNull()?.coerceIn(1, 3_600) ?: 30
        return when (response.code) {
            401 -> FriendsException("Your session has expired. Please sign in again.", FriendsFailure.SESSION_EXPIRED)
            403 -> FriendsException("Your account cannot access connections right now.", FriendsFailure.ACCESS_DENIED)
            429 -> FriendsException("Please wait $retry seconds before trying again.", FriendsFailure.RATE_LIMITED, retry)
            in 500..599 -> FriendsException("Connections are temporarily unavailable. Please try again.", FriendsFailure.SERVER)
            else -> FriendsException("Could not complete this request. Please try again.", FriendsFailure.REJECTED)
        }
    }
    private fun networkFailure(error: IOException): FriendsException = if (error is SocketTimeoutException)
        FriendsException("The request timed out. Please try again.", FriendsFailure.TIMEOUT, cause = error)
    else FriendsException("Please check your connection and try again.", FriendsFailure.NETWORK, cause = error)
    private fun invalidResponse(cause: Throwable? = null) = FriendsException(
        "Could not read the connections response. Please refresh and try again.", FriendsFailure.INVALID_RESPONSE, cause = cause)

    companion object {
        const val BASE_URL = "https://api.mongosky.com/"
        private const val MAX_BODY_BYTES = 512 * 1_024
        private val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
