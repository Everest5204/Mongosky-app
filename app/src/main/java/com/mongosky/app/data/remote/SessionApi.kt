package com.mongosky.app.data.remote

import com.mongosky.app.data.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class SessionExpiredException : IOException("Session expired.")

class SessionApi {

    suspend fun getSession(token: String): AuthSession =
        withContext(Dispatchers.IO) {
            val connection = URL(
                "https://api.mongosky.com/api/auth/me"
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

                val user = JSONObject(body)
                val userId = user.optString("id")

                if (userId.isBlank()) {
                    throw IOException("Invalid user response.")
                }

                AuthSession(
                    token = token,
                    userId = userId,
                    firstName = user.optString("first_name"),
                    lastName = user.optString("last_name")
                )
            } finally {
                connection.disconnect()
            }
        }
}