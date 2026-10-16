package com.mongosky.app.auth

import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

class AuthSession(
    val token: String,
    val userId: String,
    val firstName: String,
    val lastName: String,
    val profileImageUrl: String? = null
)

/** Optional public avatar; missing or malformed URLs keep the normal fallback. */
internal fun authProfileImageUrl(user: JSONObject): String? {
    val value = (user.opt("profile_image_url") as? String)?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 4096 } ?: return null
    return runCatching {
        val uri = URI(value)
        value.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }.getOrNull()
}

enum class LoginFailure { REJECTED, RATE_LIMITED, SERVER, INVALID_RESPONSE, CONNECTION, TIMEOUT, STORAGE }

class LoginException(
    message: String,
    val failure: LoginFailure = LoginFailure.REJECTED,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

/** No connectivity preflight or second /auth/me gate between a valid login response and Home. */
class AuthRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    client: OkHttpClient = AuthHttp.SHARED_CLIENT,
    private val endpoint: String = "https://api.mongosky.com/api/auth/login"
) {
    private val http = AuthHttp(client, ioDispatcher)

    suspend fun login(emailOrPhone: String, password: String): AuthSession = withContext(ioDispatcher) {
        val payload = JSONObject().put("email_or_phone", emailOrPhone.trim()).put("password", password)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        // Prevent HTTP status-driven POST replays. Only bounded connection recovery applies.
        val body = object : RequestBody() {
            override fun contentType() = payload.contentType()
            override fun contentLength() = payload.contentLength()
            override fun writeTo(sink: BufferedSink) = payload.writeTo(sink)
            override fun isOneShot() = true
        }
        val request = Request.Builder().url(endpoint).post(body)
            .header("Accept", "application/json").header("Cache-Control", "no-store").build()
        try {
            val result = http.execute(request)
            ensureActive()
            val json = runCatching { JSONObject(result.text) }.getOrNull()
            if (result.status !in 200..299) throw loginHttpError(result.status, json)
            val token = (json?.opt("token") as? String).orEmpty()
            val user = json?.optJSONObject("user")
            if (!validAuthToken(token) || user == null) throw invalidLoginResponse()
            authAccount(user, token) ?: throw invalidLoginResponse()
        } catch (error: LoginException) {
            throw error
        } catch (error: AuthResponseException) {
            throw invalidLoginResponse(error)
        } catch (error: InterruptedIOException) {
            ensureActive()
            throw LoginException("Sign in timed out. Please try again.", LoginFailure.TIMEOUT, cause = error)
        } catch (error: IOException) {
            ensureActive()
            throw LoginException("Could not connect. Check your internet and try again.", LoginFailure.CONNECTION, cause = error)
        }
    }
}

internal fun validAuthToken(token: String) = token.isNotBlank() && token.length <= 32_768 &&
    token.none { it <= ' ' || it >= '\u007f' }

internal fun authAccount(user: JSONObject, token: String): AuthSession? {
    val id = sequenceOf("id", "_id").mapNotNull { user.opt(it) as? String }
        .firstOrNull { it.isNotBlank() }?.trim() ?: return null
    if (id.length > 256 || id.any(Char::isISOControl)) return null
    return AuthSession(token, id, (user.opt("first_name") as? String).orEmpty(),
        (user.opt("last_name") as? String).orEmpty(), authProfileImageUrl(user))
}

private fun invalidLoginResponse(cause: Throwable? = null) = LoginException(
    "Could not read the sign in response. Please try again.", LoginFailure.INVALID_RESPONSE, cause = cause)

private fun loginHttpError(status: Int, json: JSONObject?): LoginException {
    val failure = when (status) {
        429 -> LoginFailure.RATE_LIMITED
        in 500..599 -> LoginFailure.SERVER
        else -> LoginFailure.REJECTED
    }
    val fallback = when (status) {
        401 -> "Incorrect email, phone or password."
        429 -> "Too many attempts. Please try again later."
        in 500..599 -> "Server unavailable. Please try again."
        else -> "Sign in failed (HTTP $status)."
    }
    val message = if (status in 400..499) sequenceOf("message", "error")
        .mapNotNull { json?.opt(it) as? String }.map(String::trim)
        .firstOrNull { it.isNotEmpty() && !it.startsWith("<") }?.take(240) ?: fallback else fallback
    return LoginException(message, failure, status)
}
