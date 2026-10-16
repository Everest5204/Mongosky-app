package com.mongosky.app.auth

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class AuthHttpResponse(val status: Int, val text: String)
internal class AuthResponseException : IOException("Invalid authentication response.")

/** Shared connections; cancellation stops socket work instead of leaving a blocking request behind. */
internal class AuthHttp(
    client: OkHttpClient = SHARED_CLIENT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 25_000
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun execute(request: Request): AuthHttpResponse = withContext(dispatcher) {
        // One budget includes both attempts. Login's one-shot POST never replays a returned rejection/5xx.
        withTimeoutOrNull(timeoutMillis) {
            try {
                exchange(request)
            } catch (error: IOException) {
                ensureActive()
                if (error is AuthResponseException ||
                    error is SSLException || error is InterruptedIOException) throw error
                // Login may recover a lost connection once. Signup never uses this retry path.
                exchange(request)
            }
        } ?: throw SocketTimeoutException("Authentication request timed out.")
    }

    private suspend fun exchange(request: Request): AuthHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!continuation.isActive) return
                            val output = ByteArrayOutputStream(4096)
                            response.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(4096)
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_RESPONSE_BYTES) throw AuthResponseException()
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (continuation.isActive) continuation.resume(
                                AuthHttpResponse(response.code, output.toString("UTF-8")))
                        } catch (error: Exception) {
                            if (continuation.isActive) {
                                // Once rejection headers arrived, never repeat credentials because its body was cut off.
                                if (error is IOException && !response.isSuccessful)
                                    continuation.resume(AuthHttpResponse(response.code, ""))
                                else continuation.resumeWithException(error)
                            }
                        }
                    }
                }
            })
        }

    companion object {
        private const val MAX_RESPONSE_BYTES = 65_536
        val SHARED_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(false).followSslRedirects(false)
            .build()
    }
}
