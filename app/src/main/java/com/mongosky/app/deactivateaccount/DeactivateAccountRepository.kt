package com.mongosky.app.deactivateaccount

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject

/** One POST per explicit confirmation; retries reuse the same operation ID. */
internal class DeactivateAccountRepository(
    private val client: OkHttpClient = sharedClient,
    private val endpoint: String = "https://api.mongosky.com/api/auth/deactivate-account"
) : DeactivationSource {
    override suspend fun deactivate(token: String, password: String, operationId: String): DeactivationReceipt {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw DeactivationException("Please sign in again.", 401)
        }
        DeactivateAccountPolicy.passwordError(password)?.let { throw DeactivationException(it, 400) }
        val bytes = JSONObject().put("confirm", true).put("current_password", password)
            .put("operation_id", operationId).toString().toByteArray(Charsets.UTF_8)
        val body = object : RequestBody() {
            override fun contentType() = "application/json; charset=utf-8".toMediaType()
            override fun contentLength() = bytes.size.toLong()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
        }
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").header("Cache-Control", "no-store").post(body).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(DeactivationException(UNCONFIRMED))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!continuation.isActive) return
                            val output = ByteArrayOutputStream()
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(2048)
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 16384) throw IOException("Response too large")
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (!continuation.isActive) return
                            val json = runCatching { JSONObject(output.toString("UTF-8")) }.getOrNull()
                            if (response.code != 200) {
                                val message = when (response.code) {
                                    400 -> "Check your password and try again."
                                    401 -> "Your session changed. Please sign in again."
                                    403 -> if (json?.optString("error")?.contains("set a password", true) == true)
                                        "Use Forgot password to set a password before deactivating." else "Your current password is incorrect."
                                    409 -> "Account deletion is already in progress."
                                    429 -> "Too many attempts. Wait one minute and try again."
                                    404, 405 -> "Deactivation is not available yet. Please try later."
                                    else -> UNCONFIRMED
                                }
                                throw DeactivationException(message, response.code)
                            }
                            val date = json?.opt("deactivated_at") as? String
                            if (json?.opt("status") != "deactivated" || json.opt("operation_id") != operationId ||
                                (json.opt("error") as? String)?.isNotBlank() == true || date == null ||
                                runCatching { Instant.parse(date) }.isFailure) throw DeactivationException(UNCONFIRMED, 502)
                            continuation.resume(DeactivationReceipt(operationId, date))
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                error as? DeactivationException ?: DeactivationException(UNCONFIRMED))
                        }
                    }
                }
            })
        }
    }

    private companion object {
        const val UNCONFIRMED = "We could not confirm deactivation. Retry the same request to check its result."
        val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
