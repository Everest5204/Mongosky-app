package com.mongosky.app.deleteaccount

internal enum class DeletionPhase { IDLE, SUBMITTING, PROCESSING, DELETED }
internal enum class DeletionProgress { PROCESSING, DELETED }

/** A receipt only authorizes this deletion's status endpoint. It is never saved or logged. */
internal class DeletionReceipt(val token: String) {
    override fun toString() = "DeletionReceipt([redacted])"
}

internal data class DeleteAccountState(
    val password: String = "",
    val passwordError: String? = null,
    val dialogOpen: Boolean = false,
    val phase: DeletionPhase = DeletionPhase.IDLE,
    val error: String? = null,
    val closeRequested: Boolean = false,
    val sessionChanged: Boolean = false,
    val sessionExpired: Boolean = false,
    val exitRequested: Boolean = false,
    val exitRevision: Long = 0,
    val terminalProgressError: Boolean = false
) {
    val busy get() = phase != DeletionPhase.IDLE || sessionChanged || sessionExpired || exitRequested
    override fun toString() = "DeleteAccountState(password=[redacted], phase=$phase, dialogOpen=$dialogOpen)"
}

internal const val PASSWORD_REQUIRED = "Enter your current password to continue."
internal const val REQUEST_UNCONFIRMED = "We could not confirm your deletion request. Please try again."
internal const val ACCEPTANCE_UNCONFIRMED = "Your request may have been accepted, but deletion progress could not be verified."
internal const val PROGRESS_UNCONFIRMED = "Deletion progress could not be verified. No completion confirmation has been received. An accepted deletion request may still be running."
internal const val PROGRESS_RETRY = "We could not check deletion progress. We will try again shortly."
internal const val SESSION_CHANGED = "Your sign-in has changed. Close this message and reopen it for your current account."

