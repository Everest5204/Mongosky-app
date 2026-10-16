package com.mongosky.app.changename

import com.mongosky.app.language.appText

import java.time.Instant

internal data class NameStatus(val first: String, val last: String, val canChange: Boolean, val next: Instant?)
internal data class ChangeNameState(
    val status: NameStatus? = null, val first: String = "", val last: String = "",
    val loading: Boolean = true, val saving: Boolean = false, val error: String? = null,
    val success: Boolean = false, val expired: Boolean = false, val now: Instant = Instant.now(),
    val revision: Int = 0
) {
    val locked: Boolean get() = status?.let { it.next?.isAfter(now) ?: !it.canChange } ?: false
    val changed: Boolean get() = status?.let { normalizeName(first) != it.first || normalizeName(last) != it.last } ?: false
    val validation: String? get() = validateName(first, last)
    val canSave: Boolean get() = status != null && !loading && !saving && !expired && !locked && changed && validation == null
}
/** Match Unicode whitespace without Java-only regex flags (Android uses ICU). */
private fun nameWhitespace(value: Char): Boolean =
    value.isWhitespace() || value == '\u0085'

internal fun normalizeName(value: String): String = buildString {
    var pendingSpace = false
    for (character in value) {
        if (nameWhitespace(character)) {
            if (isNotEmpty()) pendingSpace = true
        } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
        }
    }
}
internal fun validateName(first: String, last: String): String? {
    if (normalizeName(first).isEmpty() || normalizeName(last).isEmpty()) return "First name and last name are required."
    val combined = (first + last).filterNot(::nameWhitespace)
    return if (combined.codePointCount(0, combined.length) > 30)
        "First name and last name together cannot exceed 30 characters." else null
}
internal fun remainingTime(next: Instant, now: Instant): String {
    val seconds = ((java.time.Duration.between(now, next).toMillis() + 999) / 1000).coerceAtLeast(0)
    val values = listOf(seconds / 86400, seconds % 86400 / 3600, seconds % 3600 / 60, seconds % 60)
    val labels = listOf("day", "hour", "minute", "second")
    val start = values.indexOfFirst { it > 0 }.let { if (it < 0) 3 else it }
    return (start..3).joinToString(", ") { appText("${values[it]} ${labels[it]}${if (values[it] == 1L) "" else "s"}") }
}
