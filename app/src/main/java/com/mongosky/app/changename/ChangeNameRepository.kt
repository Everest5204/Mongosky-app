package com.mongosky.app.changename

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class NameRequestException(message: String, val status: Int = 0, val next: Instant? = null) : IOException(message)
internal interface NameSource {
    suspend fun load(): NameStatus
    suspend fun save(first: String, last: String): NameStatus
}
internal class ChangeNameRepository(private val readToken: suspend () -> String?) : NameSource {
    override suspend fun load() = request(null)
    override suspend fun save(first: String, last: String) = request(JSONObject().put("first_name", first).put("last_name", last))
    private suspend fun request(payload: JSONObject?): NameStatus {
        val token = readToken()?.takeIf { it.isNotBlank() } ?: throw NameRequestException("Please sign in again.", 401)
        val request = Request.Builder().url("https://api.mongosky.com/api/user/name")
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .method(if (payload == null) "GET" else "PATCH", payload?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(NameRequestException(
                        if (payload == null) "Could not connect. Check your connection and retry."
                        else "Could not confirm the update. Tap Refresh status before trying again."))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val bytes = response.body?.byteStream()?.use { input ->
                                val out = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(out.size() + count <= 65536)
                                    out.write(buffer, 0, count)
                                }
                                out.toByteArray()
                             }
                                ?: throw IOException("Empty response")
                            require(bytes.size <= 65536)
                            val json = JSONObject(String(bytes, Charsets.UTF_8))
                            if (!response.isSuccessful) throw NameRequestException(
                                if (response.code == 401) "Your session has expired. Please sign in again."
                                else json.optString("error", "Unable to process name request (HTTP ${response.code})."),
                                response.code, parseInstant(json.optString("next_change_at")))
                            val name = json.getJSONObject("name")
                            val result = NameStatus(name.getString("first_name"), name.getString("last_name"),
                                name.getBoolean("can_change"), parseInstant(name.optString("next_change_at")))
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (e is NameRequestException) e else NameRequestException("Could not read the response. Tap Refresh status."))
                        }
                    }
                }
            })
        }
    }
    private companion object {
        val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
private fun parseInstant(value: String): Instant? = value.takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse)
