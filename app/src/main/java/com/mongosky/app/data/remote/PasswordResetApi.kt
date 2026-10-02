package com.mongosky.app.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

enum class PasswordResetFailure {
    REJECTED,
    CODE_INVALID,
    RATE_LIMITED,
    UNCONFIRMED
}

class PasswordResetException(
    message: String,
    val failure: PasswordResetFailure = PasswordResetFailure.REJECTED,
    val statusCode: Int? = null,
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class PasswordResetApi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun requestCode(email: String) {
        post(
            action = Action.REQUEST_CODE,
            body = JSONObject().put("email_or_phone", normalizeEmail(email))
        )
    }

    suspend fun verifyCode(email: String, code: String) {
        post(
            action = Action.VERIFY_CODE,
            body = JSONObject()
                .put("email_or_phone", normalizeEmail(email))
                .put("code", code.trim())
        )
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        post(
            action = Action.RESET_PASSWORD,
            body = JSONObject()
                .put("email_or_phone", normalizeEmail(email))
                .put("code", code.trim())
                // Passwords must be sent exactly as entered, without trimming.
                .put("new_password", newPassword)
        )
    }

    private suspend fun post(action: Action, body: JSONObject): Unit =
        withContext(ioDispatcher) {
            ensureActive()
            var connection: HttpsURLConnection? = null

            try {
                val request = URL(BASE_URL + action.path)
                    .openConnection() as HttpsURLConnection
                connection = request
                request.requestMethod = "POST"
                request.connectTimeout = 15_000
                request.readTimeout = 30_000
                request.doOutput = true
                request.useCaches = false
                request.instanceFollowRedirects = false
                request.setRequestProperty("Accept", "application/json")
                request.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                request.setRequestProperty("Cache-Control", "no-store")

                val payload = body.toString().toByteArray(Charsets.UTF_8)
                request.setFixedLengthStreamingMode(payload.size)
                ensureActive()
                request.outputStream.use { it.write(payload) }

                ensureActive()
                val status = request.responseCode
                val stream = if (status == 200) {
                    request.inputStream
                } else {
                    request.errorStream
                }
                val responseText = readResponse(stream)
                ensureActive()

                if (status != 200) {
                    val retryAfter = request.getHeaderField("Retry-After")
                        ?.trim()
                        ?.toIntOrNull()
                        ?.coerceIn(1, 3_600)
                    throw responseError(action, status, responseText, retryAfter)
                }

                val response = parseJson(responseText)
                if (response?.opt("message") != action.successMessage) {
                    throw unconfirmedError(action)
                }
                ensureActive()
            } catch (error: PasswordResetException) {
                throw error
            } catch (error: IOException) {
                ensureActive()
                throw unconfirmedError(action, error)
            } finally {
                connection?.disconnect()
            }
        }

    private fun responseError(
        action: Action,
        status: Int,
        responseText: String,
        retryAfter: Int?
    ): PasswordResetException {
        val message = readableError(responseText)

        if (status == 429 || message?.contains(
                "wait 60 seconds",
                ignoreCase = true
            ) == true
        ) {
            return PasswordResetException(
                message = "Please wait before requesting another code or trying again.",
                failure = PasswordResetFailure.RATE_LIMITED,
                statusCode = status,
                retryAfterSeconds = retryAfter ?: RESEND_WAIT_SECONDS
            )
        }

        if (status == 400 && message?.startsWith(
                "verification code is invalid",
                ignoreCase = true
            ) == true
        ) {
            return PasswordResetException(
                message = "This code is incorrect, expired, or has reached its attempt limit. " +
                    "Check the code or request a new one.",
                failure = PasswordResetFailure.CODE_INVALID,
                statusCode = status
            )
        }

        if (status in 400..499) {
            return PasswordResetException(
                message = message ?: "Could not complete this request. Please try again.",
                statusCode = status,
                retryAfterSeconds = if (message?.contains(
                        "failed to send verification code",
                        ignoreCase = true
                    ) == true
                ) RESEND_WAIT_SECONDS else retryAfter
            )
        }

        // A missing confirmation does not prove that a POST had no effect.
        return unconfirmedError(action, statusCode = status)
    }

    private fun unconfirmedError(
        action: Action,
        cause: Throwable? = null,
        statusCode: Int? = null
    ): PasswordResetException {
        val message = when (action) {
            Action.REQUEST_CODE ->
                "We couldn't confirm whether the code was sent. Check your inbox and " +
                    "connection, then wait 60 seconds before requesting another code."
            Action.VERIFY_CODE ->
                "Could not confirm verification. Check your connection and try again."
            Action.RESET_PASSWORD ->
                "We couldn't confirm whether your password changed. Try signing in " +
                    "with your new password before requesting another code."
        }
        return PasswordResetException(
            message = message,
            failure = PasswordResetFailure.UNCONFIRMED,
            statusCode = statusCode,
            retryAfterSeconds = if (action == Action.REQUEST_CODE) {
                RESEND_WAIT_SECONDS
            } else null,
            cause = cause
        )
    }

    private fun readResponse(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count == -1) break
                if (result.length + count > 65_536) {
                    throw IOException("Unexpected server response.")
                }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
    }

    private fun readableError(responseText: String): String? {
        val text = responseText.trim()
        if (text.isEmpty() || text.startsWith("<")) return null

        val json = parseJson(text)
        if (json != null) {
            for (key in listOf("message", "error")) {
                val value = (json.opt(key) as? String)?.trim()
                if (!value.isNullOrEmpty() && !value.startsWith("<")) {
                    return value.take(300)
                }
            }
            return null
        }

        // The existing Go handlers return plain text for failed requests.
        if (text.startsWith("{") || text.startsWith("[")) return null
        return text.take(300)
    }

    private fun parseJson(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (_: JSONException) {
        null
    }

    private fun normalizeEmail(email: String): String =
        email.trim().lowercase(Locale.ROOT)

    private enum class Action(val path: String, val successMessage: String) {
        REQUEST_CODE("/api/auth/forgot-password", "Verification code sent"),
        VERIFY_CODE("/api/auth/verify-reset-code", "Verification successful"),
        RESET_PASSWORD("/api/auth/reset-password", "Your password reset successfully")
    }

    companion object {
        const val CODE_LENGTH = 6
        const val RESEND_WAIT_SECONDS = 60
        private const val BASE_URL = "https://api.mongosky.com"
    }
}
