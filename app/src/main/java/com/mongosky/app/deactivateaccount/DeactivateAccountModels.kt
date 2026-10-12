package com.mongosky.app.deactivateaccount

import java.io.IOException

internal data class DeactivateAccountState(
    val password: String = "",
    val passwordVisible: Boolean = false,
    val passwordError: String? = null,
    val dialogOpen: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val uncertain: Boolean = false,
    val deactivated: Boolean = false,
    val exitRequested: Boolean = false
)

internal data class DeactivationReceipt(val operationId: String, val deactivatedAt: String)

internal class DeactivationException(message: String, val status: Int = 0) : IOException(message)

internal interface DeactivationSource {
    suspend fun deactivate(token: String, password: String, operationId: String): DeactivationReceipt
}

internal object DeactivateAccountPolicy {
    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter your current password."
        password.toByteArray(Charsets.UTF_8).size > 72 -> "Your password is too long."
        else -> null
    }
}
