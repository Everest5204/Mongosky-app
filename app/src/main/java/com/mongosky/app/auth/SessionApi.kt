package com.mongosky.app.auth

import com.mongosky.app.auth.AuthSession
import com.mongosky.app.auth.authProfileImageUrl
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SessionExpiredException : IOException("Session expired.")

class SessionApi {

    /** Authentication/account validity; this response does not contain profile media. */
    suspend fun getSession(token: String): AuthSession =
        getAccount(token, "auth/me")

    /** Matches the web composer: profile names and avatar come from /api/user/me. */
    suspend fun getProfile(token: String): AuthSession =
        getAccount(token, "user/me")

    private suspend fun getAccount(token: String, endpoint: String): AuthSession =
        withContext(Dispatchers.IO) {
            val connection = URL(
                "https://api.mongosky.com/api/$endpoint"
            ).openConnection() as HttpsURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val status = connection.responseCode

                if (status == 401) {
                    throw SessionExpiredException()
                }

                if (status !in 200..299) {
                    throw IOException(
                        "Session check failed (HTTP $status)."
                    )
                }

                val body = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val response = JSONObject(body)
                val user = response.optJSONObject("user") ?: response
                val userId = user.optString("id")

                if (userId.isBlank()) {
                    throw IOException("Invalid user response.")
                }

                AuthSession(
                    token = token,
                    userId = userId,
                    firstName = user.optString("first_name"),
                    lastName = user.optString("last_name"),
                    profileImageUrl = authProfileImageUrl(user)
                )
            } finally {
                connection.disconnect()
            }
        }
}
