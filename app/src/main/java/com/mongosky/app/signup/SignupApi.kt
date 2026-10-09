package com.mongosky.app.signup

import com.mongosky.app.auth.AuthSession
import com.mongosky.app.auth.AuthValidation
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject

class SignupException(message: String, val outcomeUnknown: Boolean = false, cause: Throwable? = null) : IOException(message, cause)

fun interface SignupDataSource {
    suspend fun signup(request: SignupRequest): AuthSession
}

/** A pooled, asynchronous request. Registration is never automatically replayed. */
class SignupApi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    client: OkHttpClient = SHARED_CLIENT,
    private val endpoint: String = "https://api.mongosky.com/api/auth/signup"
) : SignupDataSource {
    private val client = client.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()

    override suspend fun signup(request: SignupRequest): AuthSession = withContext(ioDispatcher) {
        AuthValidation.signup(request)?.let { throw SignupException(it) }
        val jsonBody = JSONObject().put("first_name", request.firstName.trim()).put("last_name", request.lastName.trim())
            .put("email_or_phone", request.emailOrPhone.trim()).put("password", request.password).put("gender", request.gender)
            .put("birth_month", request.birthMonth).put("birth_day", request.birthDay).put("birth_year", request.birthYear)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val body = object : RequestBody() {
            override fun contentType() = jsonBody.contentType()
            override fun contentLength() = jsonBody.contentLength()
            override fun writeTo(sink: BufferedSink) = jsonBody.writeTo(sink)
            override fun isOneShot() = true
        }
        // No bearer token, cookie jar or TokenStore write: creating a second account cannot replace the current one.
        val httpRequest = Request.Builder().url(endpoint).post(body)
            .header("Accept", "application/json").header("Cache-Control", "no-store").build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(SignupException(UNCERTAIN, outcomeUnknown = true, cause = e))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val input = response.body?.byteStream()
                            val output = ByteArrayOutputStream(4096)
                            input?.use { stream ->
                                val buffer = ByteArray(4096)
                                while (continuation.isActive) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_RESPONSE_BYTES) throw SignupException(UNCERTAIN, outcomeUnknown = true)
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (!continuation.isActive) return
                            val json = runCatching { JSONObject(output.toString("UTF-8")) }.getOrNull()
                            if (!response.isSuccessful) {
                                val unknown = response.code !in setOf(400, 401, 403, 404, 405, 409, 413, 415, 422, 429)
                                val message = if (unknown) UNCERTAIN else
                                    json?.signupString("message")?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
                                        ?: json?.signupString("error")?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
                                        ?: if (response.code == 429) "Too many attempts. Please try again later."
                                        else "Could not create account (HTTP ${response.code})."
                                throw SignupException(message, unknown)
                            }
                            val token = json?.signupString("token").orEmpty()
                            val user = json?.optJSONObject("user")
                            val id = (user?.signupString("id")?.takeIf { it.isNotBlank() }
                                ?: user?.signupString("_id").orEmpty()).lowercase(Locale.ROOT)
                            if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' } ||
                                !id.matches(Regex("[a-f0-9]{24}")) || id.all { it == '0' })
                                throw SignupException(UNCERTAIN, outcomeUnknown = true)
                            continuation.resumeWith(Result.success(AuthSession(token, id,
                                user?.signupString("first_name").orEmpty(), user?.signupString("last_name").orEmpty())))
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (error is SignupException) error else SignupException(UNCERTAIN, outcomeUnknown = true, cause = error))
                    }
                }
            })
        }
    }
    private fun JSONObject.signupString(key: String): String = (opt(key) as? String).orEmpty()
    private companion object {
        const val MAX_RESPONSE_BYTES = 65_536
        const val UNCERTAIN = "Could not confirm account creation. Try signing in before creating it again."
        val SHARED_CLIENT = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).build()
    }
}
