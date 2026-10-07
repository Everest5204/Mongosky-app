package com.mongosky.app.passwordreset

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mongosky.app.passwordreset.PasswordResetApi
import com.mongosky.app.passwordreset.PasswordResetException
import com.mongosky.app.passwordreset.PasswordResetFailure
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

enum class PasswordResetStep {
    EMAIL,
    CODE,
    NEW_PASSWORD,
    SUCCESS
}

enum class PasswordResetAction {
    SEND_CODE,
    RESEND_CODE,
    VERIFY_CODE,
    RESET_PASSWORD
}

data class PasswordResetUiState(
    val step: PasswordResetStep = PasswordResetStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val pendingAction: PasswordResetAction? = null,
    val resendSeconds: Int = 0,
    val error: String? = null,
    val resetUnconfirmed: Boolean = false
) {
    val loading: Boolean
        get() = pendingAction != null

    val canResend: Boolean
        get() = step == PasswordResetStep.CODE && !loading && resendSeconds == 0
}

class PasswordResetViewModel : ViewModel() {
    private val api = PasswordResetApi()
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private var verifiedCode = ""
    private var cooldownJob: Job? = null

    // Keep cooldowns when returning to sign-in, but never persist reset credentials.
    private val cooldowns = mutableMapOf<String, Long>()

    var uiState by mutableStateOf(PasswordResetUiState())
        private set

    fun updateEmail(value: String) {
        if (uiState.loading || uiState.step != PasswordResetStep.EMAIL) return
        uiState = uiState.copy(email = value, error = null)
        refreshCooldown()
    }

    fun updateCode(value: String) {
        if (uiState.loading || uiState.step != PasswordResetStep.CODE) return
        val code = value.filter { it in '0'..'9' }.take(PasswordResetApi.CODE_LENGTH)
        uiState = uiState.copy(code = code, error = null)
    }

    fun clearError() {
        if (!uiState.loading && !uiState.resetUnconfirmed) {
            uiState = uiState.copy(error = null)
        }
    }

    fun requestCode() {
        if (uiState.loading || uiState.step != PasswordResetStep.EMAIL) return
        val email = normalizedEmail()

        if (!emailPattern.matches(email)) {
            uiState = uiState.copy(error = "Enter a valid email address.")
            return
        }

        if (remainingSeconds(email) > 0) {
            refreshCooldown()
            uiState = uiState.copy(error = "Please wait before requesting another code.")
            return
        }

        verifiedCode = ""
        uiState = uiState.copy(email = email, code = "", resetUnconfirmed = false)
        runRequest(
            action = PasswordResetAction.SEND_CODE,
            request = { api.requestCode(email) },
            onSuccess = { codeSent(email) }
        )
    }

    fun resendCode() {
        if (uiState.loading || uiState.step != PasswordResetStep.CODE) return
        val email = normalizedEmail()
        if (remainingSeconds(email) > 0) {
            refreshCooldown()
            return
        }

        // A resend may replace the previous code even if its response is lost.
        verifiedCode = ""
        uiState = uiState.copy(code = "", resetUnconfirmed = false)
        runRequest(
            action = PasswordResetAction.RESEND_CODE,
            request = { api.requestCode(email) },
            onSuccess = { codeSent(email) }
        )
    }

    fun verifyCode() {
        if (uiState.loading || uiState.step != PasswordResetStep.CODE) return
        val email = normalizedEmail()
        val code = uiState.code

        if (code.length != PasswordResetApi.CODE_LENGTH || code.any { it !in '0'..'9' }) {
            uiState = uiState.copy(error = "Enter the 6-digit verification code.")
            return
        }

        runRequest(
            action = PasswordResetAction.VERIFY_CODE,
            request = { api.verifyCode(email, code) },
            onSuccess = {
                verifiedCode = code
                uiState = uiState.copy(
                    step = PasswordResetStep.NEW_PASSWORD,
                    code = "",
                    pendingAction = null,
                    error = null
                )
                refreshCooldown()
            }
        )
    }

    fun resetPassword(newPassword: String, confirmation: String) {
        if (
            uiState.loading || uiState.step != PasswordResetStep.NEW_PASSWORD ||
            uiState.resetUnconfirmed
        ) return

        val code = verifiedCode
        if (code.length != PasswordResetApi.CODE_LENGTH) {
            uiState = uiState.copy(
                step = PasswordResetStep.CODE,
                code = "",
                error = "Enter your verification code again."
            )
            refreshCooldown()
            return
        }

        val message = when {
            newPassword.isBlank() -> "Enter a new password."
            newPassword.codePointCount(0, newPassword.length) < 6 ->
                "Use a password with at least 6 characters."
            newPassword.toByteArray(Charsets.UTF_8).size > 72 ->
                "This password is too long. Please use a shorter one."
            newPassword != confirmation -> "Your passwords do not match."
            else -> null
        }
        if (message != null) {
            uiState = uiState.copy(error = message)
            return
        }

        val email = normalizedEmail()
        runRequest(
            action = PasswordResetAction.RESET_PASSWORD,
            request = { api.resetPassword(email, code, newPassword) },
            onSuccess = {
                verifiedCode = ""
                cooldownJob?.cancel()
                cooldownJob = null
                uiState = PasswordResetUiState(step = PasswordResetStep.SUCCESS)
            }
        )
    }

    // True means Back was handled here. False lets the screen return to sign-in.
    fun goBack(): Boolean {
        if (uiState.loading) return true
        if (uiState.resetUnconfirmed) return false

        val previousStep = when (uiState.step) {
            PasswordResetStep.CODE -> PasswordResetStep.EMAIL
            PasswordResetStep.NEW_PASSWORD -> PasswordResetStep.CODE
            PasswordResetStep.EMAIL, PasswordResetStep.SUCCESS -> return false
        }
        verifiedCode = ""
        uiState = uiState.copy(step = previousStep, code = "", error = null)
        refreshCooldown()
        return true
    }

    fun reset() {
        if (uiState.loading) return
        verifiedCode = ""
        cooldownJob?.cancel()
        cooldownJob = null
        uiState = PasswordResetUiState()
        removeExpiredCooldowns()
    }

    private fun runRequest(
        action: PasswordResetAction,
        request: suspend () -> Unit,
        onSuccess: () -> Unit
    ) {
        if (uiState.loading) return
        uiState = uiState.copy(pendingAction = action, error = null)

        viewModelScope.launch {
            try {
                request()
                ensureActive()
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: PasswordResetException) {
                ensureActive()
                handleFailure(action, error)
            } catch (_: Exception) {
                ensureActive()
                val message = when (action) {
                    PasswordResetAction.SEND_CODE, PasswordResetAction.RESEND_CODE ->
                        "Could not confirm whether the code was sent. Check your inbox " +
                            "and connection, then wait 60 seconds before requesting again."
                    PasswordResetAction.VERIFY_CODE ->
                        "Could not confirm verification. Check your connection and try again."
                    PasswordResetAction.RESET_PASSWORD ->
                        "We couldn't confirm whether your password changed. Try signing in " +
                            "with your new password before requesting another code."
                }
                handleFailure(
                    action,
                    PasswordResetException(
                        message = message,
                        failure = PasswordResetFailure.UNCONFIRMED
                    )
                )
            }
        }
    }

    private fun codeSent(email: String) {
        setCooldown(email, PasswordResetApi.RESEND_WAIT_SECONDS)
        uiState = uiState.copy(
            step = PasswordResetStep.CODE,
            code = "",
            pendingAction = null,
            error = null,
            resetUnconfirmed = false
        )
        refreshCooldown()
    }

    private fun handleFailure(action: PasswordResetAction, error: PasswordResetException) {
        val sendingCode = action == PasswordResetAction.SEND_CODE ||
            action == PasswordResetAction.RESEND_CODE
        val unconfirmed = error.failure == PasswordResetFailure.UNCONFIRMED
        val rateLimited = error.failure == PasswordResetFailure.RATE_LIMITED

        if (sendingCode && (unconfirmed || rateLimited || error.retryAfterSeconds != null)) {
            setCooldown(
                normalizedEmail(),
                error.retryAfterSeconds ?: PasswordResetApi.RESEND_WAIT_SECONDS
            )
        }

        var step = uiState.step
        var code = uiState.code
        if (action == PasswordResetAction.SEND_CODE && (unconfirmed || rateLimited)) {
            // Let the user enter a code if the earlier request reached the server.
            step = PasswordResetStep.CODE
            code = ""
        }
        if (action == PasswordResetAction.RESET_PASSWORD &&
            error.failure == PasswordResetFailure.CODE_INVALID
        ) {
            verifiedCode = ""
            step = PasswordResetStep.CODE
            code = ""
        }
        if (action == PasswordResetAction.RESET_PASSWORD && unconfirmed) {
            verifiedCode = ""
        }

        uiState = uiState.copy(
            step = step,
            code = code,
            pendingAction = null,
            error = error.message ?: "Could not complete this request. Please try again.",
            resetUnconfirmed = action == PasswordResetAction.RESET_PASSWORD && unconfirmed
        )
        refreshCooldown()
    }

    private fun normalizedEmail(): String = uiState.email.trim().lowercase(Locale.ROOT)

    private fun setCooldown(email: String, seconds: Int) {
        removeExpiredCooldowns()
        val deadline = SystemClock.elapsedRealtime() + seconds.coerceIn(1, 3_600) * 1_000L
        cooldowns[email] = maxOf(cooldowns[email] ?: 0L, deadline)
    }

    private fun remainingSeconds(email: String): Int {
        val remaining = ((cooldowns[email] ?: 0L) - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)
        return ((remaining + 999L) / 1_000L).toInt()
    }

    private fun refreshCooldown() {
        cooldownJob?.cancel()
        cooldownJob = null
        val seconds = remainingSeconds(normalizedEmail())
        if (uiState.resendSeconds != seconds) {
            uiState = uiState.copy(resendSeconds = seconds)
        }

        if (seconds == 0 || !showsCodeEntry()) return
        cooldownJob = viewModelScope.launch {
            while (showsCodeEntry()) {
                delay(1_000)
                val remaining = remainingSeconds(normalizedEmail())
                if (uiState.resendSeconds != remaining) {
                    uiState = uiState.copy(resendSeconds = remaining)
                }
                if (remaining == 0) break
            }
        }
    }

    private fun showsCodeEntry(): Boolean = uiState.step == PasswordResetStep.EMAIL ||
        uiState.step == PasswordResetStep.CODE

    private fun removeExpiredCooldowns() {
        val now = SystemClock.elapsedRealtime()
        cooldowns.entries.removeAll { it.value <= now }
    }

    override fun onCleared() {
        verifiedCode = ""
        cooldownJob?.cancel()
        cooldowns.clear()
        super.onCleared()
    }
}
