package com.mongosky.app.reportpost

import com.mongosky.app.language.appText

import androidx.compose.runtime.Immutable
import com.mongosky.app.post.FeedSource
import com.mongosky.app.post.HomePost
import java.time.Instant

internal enum class ReportReason(val code: String, val title: String, val description: String) {
    SPAM("spam", "Spam", "Repeated, unwanted or misleading promotion"),
    HARASSMENT("harassment", "Bullying or harassment", "Targeting or humiliating someone"),
    HATE("hate_speech", "Hate speech", "Attacks against a protected group"),
    VIOLENCE("violence", "Violence or threats", "Threats or content encouraging violence"),
    NUDITY("nudity", "Nudity or sexual content", "Inappropriate sexual content"),
    SCAM("scam", "Scam or fraud", "Deceptive offers or attempts to steal information"),
    FALSE_INFORMATION("false_information", "False information", "Misleading claims presented as facts"),
    SELF_HARM("self_harm", "Self-injury", "Content encouraging self-injury"),
    OTHER("other", "Something else", "Another concern about this post");

    companion object { fun fromCode(value: String) = entries.firstOrNull { it.code == value } }
}

internal enum class ReportStage { REASONS, REVIEW, RECEIVED }

@Immutable
internal data class ReportTarget(val postId: String, val source: String, val authorName: String) {
    val key get() = "$source:$postId"
    companion object {
        fun from(post: HomePost) = ReportTarget(post.id,
            if (post.source == FeedSource.TEXT) "text" else "media",
            post.author.displayName.ifBlank { appText("Mongosky user") })
    }
}

@Immutable
internal data class ReportSubmission(val target: ReportTarget, val requestId: String,
    val reason: ReportReason, val details: String)

@Immutable
internal data class ReportReceipt(val id: String, val requestId: String, val postId: String,
    val source: String, val reason: ReportReason, val status: String,
    val receivedAt: Instant, val alreadyReported: Boolean)

@Immutable
internal data class PostReportState(
    val target: ReportTarget,
    val stage: ReportStage = ReportStage.REASONS,
    val reason: ReportReason? = null,
    val details: String = "",
    val busy: Boolean = false,
    val checking: Boolean = false,
    val uncertain: Boolean = false,
    val attempt: ReportSubmission? = null,
    val receipt: ReportReceipt? = null,
    val error: String? = null,
    val unavailable: Boolean = false,
    val retryAtMillis: Long = 0
) { val locked get() = attempt != null }

internal interface PostReportSource {
    suspend fun submit(token: String, submission: ReportSubmission): ReportReceipt
    suspend fun receipt(token: String, submission: ReportSubmission): ReportReceipt
}

internal object ReportPolicy {
    const val MAX_DETAILS = 1000
    private val objectId = Regex("[a-f0-9]{24}")
    private val operation = Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    fun validId(value: String) = objectId.matches(value) && value != "0".repeat(24)
    fun validRequest(value: String) = operation.matches(value)
    fun validTarget(target: ReportTarget) = validId(target.postId) && target.source in setOf("text", "media")
    fun count(value: String) = value.codePointCount(0, value.length)
    fun limit(value: String) = if (count(value) <= MAX_DETAILS) value else value.substring(0, value.offsetByCodePoints(0, MAX_DETAILS))
    fun error(value: String): String? = when {
        count(value) > MAX_DETAILS -> "Keep additional details within $MAX_DETAILS characters."
        value.any { Character.isISOControl(it) && it !in "\n\r\t" } -> "Remove unsupported characters from your details."
        else -> null
    }
}
