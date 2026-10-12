package com.mongosky.app.changepassword

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

internal class ChangePasswordException(message: String, val status: Int = 0) : IOException(message)

internal interface ChangePasswordSource {
    suspend fun change(values: PasswordValues): String
}

/** One POST per submit, with cancellation and no automatic mutation replay. */
internal class ChangePasswordRepository(
    private val readToken: suspend () -> String?,
    private val client: OkHttpClient = sharedClient,
    private val endpoint: String = "https://api.mongosky.com/api/auth/change-password"
) : ChangePasswordSource {
    override suspend fun change(values: PasswordValues): String = withContext(Dispatchers.IO) {
        ensureActive()
        val token = readToken()?.takeIf { it.isNotBlank() && it.none { ch -> ch <= ' ' || ch >= '\u007f' } }
            ?: throw ChangePasswordException("Your session has expired. Please sign in again.", 401)
        val payload = JSONObject().put("current_password", values.current)
            .put("new_password", values.new).put("confirm_password", values.confirmation)
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val body = object : RequestBody() {
            override fun contentType() = "application/json; charset=utf-8".toMediaType()
            override fun contentLength() = bytes.size.toLong()
            // Also prevents OkHttp's HTTP 503/Retry-After follow-up from replaying this POST.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
        }
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .post(body).build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ChangePasswordException(PASSWORD_UNCONFIRMED))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!continuation.isActive) return
                            if (response.code == 401) throw ChangePasswordException("Your session has expired. Please sign in again.", 401)
                            val output = ByteArrayOutputStream()
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(4096)
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 65536) throw IOException("Response too large")
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (!continuation.isActive) return
                            val text = output.toString("UTF-8")
                            val json = runCatching { JSONObject(text) }.getOrNull()
                            val message = listOf("message", "error").firstNotNullOfOrNull { key ->
                                (json?.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(512)
                            }
                            if (!response.isSuccessful) {
                                val fallback = when (response.code) {
                                    403 -> "Current password is incorrect."
                                    409 -> "Your password has already changed. Please try again with your current password."
                                    429 -> "Too many attempts. Please wait and try again."
                                    in 500..599 -> "Unable to change your password. Please try again."
                                    else -> "Unable to change your password (HTTP ${response.code})."
                                }
                                throw ChangePasswordException(message ?: fallback, response.code)
                            }
                            if (json == null || (json.opt("error") as? String)?.isNotBlank() == true) {
                                throw ChangePasswordException(PASSWORD_UNCONFIRMED)
                            }
                            if (continuation.isActive) continuation.resume(message ?: PASSWORD_CHANGED)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (e is ChangePasswordException) e else ChangePasswordException(PASSWORD_UNCONFIRMED))
                        }
                    }
                }
            })
        }
    }

    private companion object {
        val sharedClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
