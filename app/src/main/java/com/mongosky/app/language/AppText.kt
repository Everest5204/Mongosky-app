package com.mongosky.app.language

/**
 * Presentation-only bridge for the existing sections' labels and app-owned messages.
 * Never pass names, biographies, captions, posts, comments, chat titles, or AI replies.
 * English is an allocation-free fast path; Bangla uses native, packaged Android resources.
 * Reading the locale snapshot also updates existing Compose text when the choice changes.
 */
internal fun appText(text: String): String {
    val locale = AppLanguage.current
    if (locale == AppLocale.ENGLISH || text.isEmpty()) return text
    val resources = AppLanguage.resources(locale) ?: return text
    LanguageCatalog.exact[text]?.let { return resources.getString(it) }
    // Some existing annotated spans carry whitespace around their label.
    val trimmed = text.trim()
    if (trimmed != text) {
        LanguageCatalog.exact[trimmed]?.let {
            val first = text.indexOf(trimmed)
            return text.substring(0, first) + resources.getString(it) + text.substring(first + trimmed.length)
        }
    }
    // Exact labels never run pattern matching. Templates are only for app-owned sentences.
    LanguageCatalog.templates.forEach { template ->
        template.arguments(text)?.let { return resources.getString(template.resource, *it) }
    }
    return text
}

internal fun appDescription(text: String?): String? = text?.let(::appText)

/** A remote push contains an opaque actor name followed by one of these app-owned actions. */
internal fun appNotificationText(body: String): String {
    if (AppLanguage.current == AppLocale.ENGLISH) return body
    val sentence = body.removeSuffix(".")
    val action = notificationActions.firstOrNull { sentence.endsWith(" $it") } ?: return body
    val actor = sentence.removeSuffix(" $action")
    return actor + " " + appText(action) + if (body.endsWith('.')) "." else ""
}

private val notificationActions = listOf(
    "started following you", "followed you back", "loved your post", "reacted to your post",
    "commented on your post", "replied to your comment", "loved your comment", "loved your reply"
)
