package com.mongosky.app.auth

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SessionExpiredException : IOException("Session expired.")

class SessionApi(
    client: OkHttpClient = AuthHttp.SHARED_CLIENT,
    private val baseUrl: String = "https://api.mongosky.com/api/",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val http = AuthHttp(client, ioDispatcher)

    /** Validated silently after the saved account has already routed to Home. */
    suspend fun getSession(token: String): AuthSession = getAccount(token, "auth/me")
    suspend fun getProfile(token: String): AuthSession = getAccount(token, "user/me")

    private suspend fun getAccount(token: String, endpoint: String): AuthSession = withContext(ioDispatcher) {
        if (!validAuthToken(token)) throw SessionExpiredException()
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/" + endpoint)
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("Cache-Control", "no-store").build()
        val response = http.execute(request)
        if (response.status == 401) throw SessionExpiredException()
        // Timeout, 403, 429, malformed response and 5xx must not erase a saved login.
        if (response.status !in 200..299) throw IOException("Session check failed (HTTP " + response.status + ").")
        val json = runCatching { JSONObject(response.text) }.getOrNull()
            ?: throw AuthResponseException()
        authAccount(json.optJSONObject("user") ?: json, token) ?: throw AuthResponseException()
    }
}
