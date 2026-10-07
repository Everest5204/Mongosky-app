package com.mongosky.app.profile

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject

class OwnProfileException(
    message: String,
    val requiresSignIn: Boolean = false,
    val outcomeUnknown: Boolean = false,
    cause: Throwable? = null
) : IOException(message, cause)

/** One pooled client; cancellable requests, bounded bodies and no replay of uploads. */
class OwnProfileHttp(
    client: OkHttpClient = SHARED_CLIENT,
    baseUrl: String = "https://api.mongosky.com/api/"
) {
    private val client = client.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    private val base: HttpUrl = baseUrl.toHttpUrl()

    suspend fun json(token: String, path: String, method: String = "GET", body: RequestBody? = null,
        query: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.Default) {
        try { JSONObject(text(token, path, method, body, query)) }
        catch (error: OwnProfileException) { throw error }
        catch (error: org.json.JSONException) {
            throw OwnProfileException("Could not read the profile response. Refresh and try again.",
                outcomeUnknown = method != "GET", cause = error)
        }
    }

    suspend fun text(token: String, path: String, method: String = "GET", body: RequestBody? = null,
        query: Map<String, String> = emptyMap()): String {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw OwnProfileException("Please sign in again.", requiresSignIn = true)
        }
        require(!path.startsWith('/') && !path.contains(".."))
        val url = requireNotNull(base.resolve(path)).newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).method(method, body)
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("Cache-Control", "no-store").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            if (method == "GET" || path == "verified-badges/resolve") call.timeout().timeout(30, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(OwnProfileException(
                        if (method == "POST") "Upload may have finished. Check your profile before choosing again."
                        else "Could not connect. Check your connection and try again.",
                        outcomeUnknown = method == "POST", cause = e))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (response.code != 200) throw httpError(response.code, method)
                            val input = response.body?.byteStream() ?: throw OwnProfileException("Empty profile response.", outcomeUnknown = method == "POST")
                            val output = ByteArrayOutputStream(8192)
                            val buffer = ByteArray(8192)
                            while (continuation.isActive) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_RESPONSE_BYTES) throw OwnProfileException(
                                    "The profile response is too large.", outcomeUnknown = method != "GET")
                                output.write(buffer, 0, count)
                            }
                            if (continuation.isActive) continuation.resumeWith(Result.success(output.toString("UTF-8")))
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (error is OwnProfileException) error else OwnProfileException(
                                "Could not read the profile response.", outcomeUnknown = method == "POST", cause = error))
                    }
                }
            })
        }
    }

    private fun httpError(code: Int, method: String) = OwnProfileException(
        when (code) {
            401 -> "Please sign in again."
            403 -> "You cannot change this profile."
            404 -> "Profile not found."
            413 -> "This image is too large. Choose a smaller image."
            429 -> "Too many requests. Wait a moment and try again."
            else -> if (method == "POST" && code >= 500)
                "Upload may have finished. Check your profile before choosing again."
            else "Could not ${if (method == "GET") "load" else "update"} profile (HTTP $code)."
        }, requiresSignIn = code == 401,
        outcomeUnknown = method == "POST" && code !in listOf(400, 401, 403, 404, 413, 415, 422, 429)
    )

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
        val SHARED_CLIENT = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(65, TimeUnit.SECONDS).build()
    }
}

internal fun JSONObject.profileString(key: String): String = (opt(key) as? String).orEmpty()
internal fun profileObjectId(value: String): String? = value.trim().lowercase(java.util.Locale.ROOT).takeIf {
    it.length == 24 && it.any { c -> c != '0' } && it.all { c -> c in '0'..'9' || c in 'a'..'f' }
}
internal fun profileImageUrl(value: String): String? = value.trim().takeIf { it.isNotEmpty() }?.let {
    runCatching { it.toHttpUrl() }.getOrNull()?.takeIf { url -> url.isHttps && url.username.isEmpty() && url.password.isEmpty() }?.toString()
}
internal fun requireProfileOwner(actual: String, expected: String) {
    if (profileObjectId(actual) != profileObjectId(expected) || profileObjectId(expected) == null)
        throw OwnProfileException("Profile account changed. Please sign in again.", requiresSignIn = true)
}
