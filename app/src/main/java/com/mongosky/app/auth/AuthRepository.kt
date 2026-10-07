package com.mongosky.app.auth

import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
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

class LoginException(message: String) : IOException(message)

class AuthRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun login(
        emailOrPhone: String,
        password: String
    ): AuthSession = withContext(ioDispatcher) {
        val connection = URL(
            "https://api.mongosky.com/api/auth/login"
        ).openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.useCaches = false
            connection.instanceFollowRedirects = false

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val request = JSONObject()
                .put("email_or_phone", emailOrPhone.trim())
                .put("password", password)
                .toString()
                .toByteArray(Charsets.UTF_8)

            connection.setFixedLengthStreamingMode(request.size)

            connection.outputStream.use {
                it.write(request)
            }

            val statusCode = connection.responseCode
            val successful = statusCode in 200..299
            val stream = if (successful) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val response = try {
                JSONObject(responseText)
            } catch (_: JSONException) {
                null
            }

            if (!successful) {
                val fallback = when (statusCode) {
                    401 -> "Incorrect email, phone or password."
                    429 -> "Too many attempts. Please try again later."
                    in 500..599 -> "Server unavailable. Please try again."
                    else -> "Login failed (HTTP $statusCode)."
                }

                val message = if (statusCode in 400..499) {
                    response?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: response?.optString("error")
                            ?.takeIf { it.isNotBlank() }
                        ?: fallback
                } else {
                    fallback
                }

                throw LoginException(message)
            }

            val token = response?.optString("token").orEmpty()
            val user = response?.optJSONObject("user")
            val userId = user?.optString("id").orEmpty()

            if (token.isBlank() || userId.isBlank()) {
                throw LoginException("Invalid login response from server.")
            }

            AuthSession(
                token = token,
                userId = userId,
                firstName = user?.optString("first_name").orEmpty(),
                lastName = user?.optString("last_name").orEmpty(),
                profileImageUrl = user?.let(::authProfileImageUrl)
            )
        } finally {
            connection.disconnect()
        }
    }
}
