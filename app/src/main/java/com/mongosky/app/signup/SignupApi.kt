package com.mongosky.app.signup

import com.mongosky.app.auth.AuthSession
import com.mongosky.app.signup.SignupRequest
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class SignupException(message: String) : IOException(message)

class SignupApi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun signup(
        request: SignupRequest
    ): AuthSession = withContext(ioDispatcher) {

        val connection = URL(
            "https://api.mongosky.com/api/auth/signup"
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

            val body = JSONObject()
                .put("first_name", request.firstName.trim())
                .put("last_name", request.lastName.trim())
                .put("email_or_phone", request.emailOrPhone.trim())
                .put("password", request.password)
                .put("gender", request.gender)
                .put("birth_month", request.birthMonth)
                .put("birth_day", request.birthDay)
                .put("birth_year", request.birthYear)

            val payload = body.toString()
                .toByteArray(Charsets.UTF_8)

            connection.setFixedLengthStreamingMode(payload.size)

            connection.outputStream.use { output ->
                output.write(payload)
            }

            val status = connection.responseCode
            val successful = status in 200..299

            val stream = if (successful) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(4096)

                    while (true) {
                        val count = reader.read(buffer)

                        if (count == -1) {
                            break
                        }

                        if (result.length + count > 65_536) {
                            throw IOException(
                                "Unexpected server response."
                            )
                        }

                        result.append(buffer, 0, count)
                    }

                    result.toString()
                }
                .orEmpty()

            val response = try {
                JSONObject(responseText)
            } catch (_: JSONException) {
                null
            }

            if (!successful) {
                val fallback = when (status) {
                    429 ->
                        "Too many attempts. Please try again later."

                    in 500..599 ->
                        "Server unavailable. Please try again."

                    else ->
                        "Could not create account (HTTP $status)."
                }

                val message = if (status in 400..499) {
                    response
                        ?.safeString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: response
                            ?.safeString("error")
                            ?.takeIf { it.isNotBlank() }
                        ?: fallback
                } else {
                    fallback
                }

                throw SignupException(message)
            }

            val token = response
                ?.safeString("token")
                .orEmpty()

            val user = response?.optJSONObject("user")

            val userId = user
                ?.safeString("id")
                .orEmpty()

            if (token.isBlank() || userId.isBlank()) {
                throw SignupException(
                    "Could not confirm account creation. " +
                            "Try signing in before creating it again."
                )
            }

            AuthSession(
                token = token,
                userId = userId,
                firstName = user
                    ?.safeString("first_name")
                    .orEmpty(),
                lastName = user
                    ?.safeString("last_name")
                    .orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.safeString(key: String): String {
        return if (isNull(key)) {
            ""
        } else {
            optString(key)
        }
    }
}
