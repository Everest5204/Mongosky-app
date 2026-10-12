package com.mongosky.app.changepassword

internal enum class PasswordField { CURRENT, NEW, CONFIRM }

/** Values live only in memory. Passwords are not trimmed or normalized. */
internal data class PasswordValues(
    val current: String = "", val new: String = "", val confirmation: String = ""
) {
    override fun toString() = "PasswordValues(redacted)"
}

internal object ChangePasswordPolicy {
    fun validate(values: PasswordValues): String? = when {
        values.current.isEmpty() -> "Please enter your current password."
        values.new.isEmpty() -> "Please enter your new password."
        values.new.codePointCount(0, values.new.length) < 6 -> "New password must be at least 6 characters."
        values.new.toByteArray(Charsets.UTF_8).size > 72 -> "New password is too long."
        values.confirmation.isEmpty() -> "Please re-type your new password."
        values.new != values.confirmation -> "New passwords do not match."
        values.new == values.current -> "New password must be different from your current password."
        else -> null
    }
}

internal data class ChangePasswordState(
    val values: PasswordValues = PasswordValues(),
    val submitting: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val sessionExpired: Boolean = false,
    val successRevision: Int = 0,
    val awaitingAutofill: Boolean = false
) {
    val busy: Boolean get() = submitting || awaitingAutofill || sessionExpired
}

internal const val PASSWORD_CHANGED = "Your password has been changed successfully."
internal const val PASSWORD_UNCONFIRMED = "Unable to confirm the password change. Check your connection. If it changed, use your new password to sign in before retrying."
