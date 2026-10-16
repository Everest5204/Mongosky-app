package com.mongosky.app.language

/** Translate the editor's app-owned error labels at display time; keep its saved state intact. */
internal fun appProfileErrorText(message: String): String {
    if (AppLanguage.current == AppLocale.ENGLISH || message.isEmpty()) return message
    return message.lineSequence().joinToString("\n") { line ->
        val separator = line.indexOf(": ")
        val label = if (separator > 0) line.substring(0, separator) else null
        if (label == "Profile photo" || label == "Cover photo") {
            appText(label) + ": " + appText(line.substring(separator + 2))
        } else {
            appText(line)
        }
    }
}
