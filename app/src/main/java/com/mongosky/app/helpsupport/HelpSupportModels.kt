package com.mongosky.app.helpsupport

internal enum class HelpTopic(val label: String) {
    ACCOUNT("Account"), POSTS("Posts & media"), SAFETY("Privacy & safety"), APP("App & notifications")
}

internal data class HelpArticle(
    val id: String,
    val topic: HelpTopic,
    val question: String,
    val answer: String
)

internal enum class SupportReason(val label: String) {
    GENERAL("General question"), TECHNICAL("Report a problem"), ACCOUNT("Account help"),
    SAFETY("Privacy & safety"), FEEDBACK("Send feedback")
}

internal data class SupportDraft(
    val reason: SupportReason = SupportReason.GENERAL,
    val summary: String = "",
    val description: String = "",
    val includeAppInfo: Boolean = false
)

internal data class SupportDraftErrors(val summary: String? = null, val description: String? = null) {
    val isValid: Boolean get() = summary == null && description == null
}

internal data class SupportAppInfo(val appVersion: String, val androidVersion: String)

internal data class SupportEmail(val recipient: String, val subject: String, val body: String, val mailto: String)
