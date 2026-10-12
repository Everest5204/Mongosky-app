package com.mongosky.app.deleteaccount

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

internal class DeleteAccountException(message: String, val status: Int = 0) : IOException(message)

internal interface DeleteAccountSource {
    suspend fun request(password: String, originalToken: String): DeletionReceipt
    suspend fun progress(receipt: DeletionReceipt): DeletionProgress
}

/** Captured credentials only. One destructive request per explicit confirmation. */
internal class DeleteAccountRepository(
    private val client: OkHttpClient = sharedClient,
    private val endpoint: String = "https://api.mongosky.com/api/auth/delete-account"
) : DeleteAccountSource {
    override suspend fun request(password: String, originalToken: String): DeletionReceipt = withContext(Dispatchers.IO) {
        ensureActive()
        if (password.isEmpty()) throw DeleteAccountException(PASSWORD_REQUIRED, 400)
        checkToken(originalToken)
        val bytes = JSONObject().put("confirm", true).put("current_password", password)
            .toString().toByteArray(Charsets.UTF_8)
        val body = object : RequestBody() {
            override fun contentType() = "application/json; charset=utf-8".toMediaType()
            override fun contentLength() = bytes.size.toLong()
            // Prevent transport retries and HTTP 503/Retry-After replay of the DELETE.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
        }
        val response = send(buildRequest(endpoint, originalToken).delete(body).build(), REQUEST_UNCONFIRMED)
        val token = response.json.opt("deletion_token") as? String
        if (response.status != 202 || response.json.opt("status") != "accepted" ||
            token == null || token.length > 4096 || token.trim().isEmpty() ||
            token.trim().any { it <= ' ' || it >= '\u007f' } ||
            (response.json.opt("error") as? String)?.isNotBlank() == true) {
            throw DeleteAccountException(ACCEPTANCE_UNCONFIRMED, 502)
        }
        DeletionReceipt(token.trim())
    }

    override suspend fun progress(receipt: DeletionReceipt): DeletionProgress = withContext(Dispatchers.IO) {
        ensureActive()
        checkToken(receipt.token)
        val response = send(buildRequest("$endpoint/status", receipt.token).get().build(), PROGRESS_UNCONFIRMED, statusCheck = true)
        if (response.status != 200 || (response.json.opt("error") as? String)?.isNotBlank() == true) {
            throw DeleteAccountException(PROGRESS_UNCONFIRMED, 502)
        }
        when (response.json.opt("status")) {
            "processing" -> DeletionProgress.PROCESSING
            "deleted" -> DeletionProgress.DELETED
            else -> throw DeleteAccountException(PROGRESS_UNCONFIRMED, 502)
        }
    }

    private fun checkToken(token: String) {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw DeleteAccountException("Authorization is required.", 401)
        }
    }

    private fun buildRequest(url: String, token: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token").header("Accept", "application/json")
        .header("Cache-Control", "no-store")

    private class Result(val status: Int, val json: JSONObject)
    private suspend fun send(request: Request, fallback: String, statusCheck: Boolean = false): Result =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.timeout().timeout(if (statusCheck) 15 else 45, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(DeleteAccountException(fallback))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!continuation.isActive) return
                            val bytes = ByteArrayOutputStream()
                            var malformed = false
                            try {
                                response.body?.byteStream()?.use { input ->
                                    val buffer = ByteArray(4096)
                                    while (continuation.isActive) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (bytes.size() + count > 65536) throw IOException("Response limit")
                                        bytes.write(buffer, 0, count)
                                    }
                                }
                            } catch (_: IOException) { malformed = true }
                            if (!continuation.isActive) return
                            val json = if (malformed) null else runCatching { JSONObject(bytes.toString("UTF-8")) }.getOrNull()
                            if (!response.isSuccessful) {
                                val message = listOf("message", "error").firstNotNullOfOrNull { key ->
                                    (json?.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(300)
                                }
                                val default = when {
                                    response.code == 401 && !statusCheck -> "Your session has expired. Please sign in again."
                                    response.code == 403 && !statusCheck -> "Your current password is incorrect. Please try again."
                                    response.code == 429 -> "Too many attempts. Please wait and try again."
                                    else -> fallback
                                }
                                throw DeleteAccountException(message ?: default, response.code)
                            }
                            if (json == null) throw DeleteAccountException(
                                if (statusCheck) PROGRESS_UNCONFIRMED else ACCEPTANCE_UNCONFIRMED, 502)
                            if (continuation.isActive) continuation.resume(Result(response.code, json))
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                if (error is DeleteAccountException) error else DeleteAccountException(fallback))
                        }
                    }
                }
            })
        }

    private companion object {
        val sharedClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build()
    }
}

