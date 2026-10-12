package com.mongosky.app.helpsupport

import java.net.URLEncoder
import java.util.Locale

/** Pure policies keep search, validation and email preparation independently testable. */
internal object HelpSupportPolicy {
    const val MAX_QUERY = 120
    const val MAX_SUMMARY = 100
    const val MAX_DESCRIPTION = 4000
    const val MIN_DESCRIPTION = 10
    private val whitespace = Regex("\\s+")
    private val indexed = HelpSupportContent.articles.map { article ->
        article to "${article.question} ${article.answer} ${article.topic.label}".lowercase(Locale.ROOT)
    }

    fun search(query: String, topic: HelpTopic?): List<HelpArticle> {
        val words = query.take(MAX_QUERY).trim().lowercase(Locale.ROOT)
            .split(whitespace).filter(String::isNotBlank)
        return indexed.filter { (article, searchable) ->
            (topic == null || topic == article.topic) && words.all(searchable::contains)
        }.map { it.first }
    }

    fun summary(value: String): String = value.replace('\r', ' ').replace('\n', ' ').take(MAX_SUMMARY)

    fun validate(draft: SupportDraft): SupportDraftErrors = SupportDraftErrors(
        summary = when {
            draft.summary.trim().isEmpty() -> "Add a short summary."
            draft.summary.length > MAX_SUMMARY -> "Use $MAX_SUMMARY characters or fewer."
            else -> null
        },
        description = when {
            draft.description.trim().length < MIN_DESCRIPTION -> "Add at least $MIN_DESCRIPTION characters so we can understand the issue."
            draft.description.length > MAX_DESCRIPTION -> "Use $MAX_DESCRIPTION characters or fewer."
            else -> null
        }
    )

    fun prepareEmail(draft: SupportDraft, info: SupportAppInfo?): SupportEmail {
        require(validate(draft).isValid) { "Complete the support form before preparing an email." }
        val subject = "Mongosky | ${draft.reason.label} | ${summary(draft.summary).trim()}"
        val body = buildString {
            append("Hello Mongosky support,\n\n")
            append("Topic: ${draft.reason.label}\n")
            append("Summary: ${summary(draft.summary).trim()}\n\n")
            append(draft.description.trim())
            if (draft.includeAppInfo && info != null) {
                append("\n\nApp version: ${info.appVersion}\nAndroid version: ${info.androidVersion}")
            }
        }
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val mailto = "mailto:${HelpSupportContent.CONTACT_EMAIL}?subject=${encode(subject)}&body=${encode(body)}"
        return SupportEmail(HelpSupportContent.CONTACT_EMAIL, subject, body, mailto)
    }
}
