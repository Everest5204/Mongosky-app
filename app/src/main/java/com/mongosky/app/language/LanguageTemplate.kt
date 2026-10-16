package com.mongosky.app.language

/** Compiled only when a matching UI sentence is first needed. Names are opaque arguments. */
internal class LanguageTemplate(val source: String, val resource: Int) {
    private companion object {
        val placeholder = Regex("%([1-9][0-9]*)\\\$s")
    }
    val prefix: String = source.substringBefore('%')
    private val slots = placeholder.findAll(source).toList()
    private val pattern by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var end = 0
        buildString {
            append('^')
            slots.forEach { slot ->
                append(Regex.escape(source.substring(end, slot.range.first)))
                append("([\\s\\S]*?)")
                end = slot.range.last + 1
            }
            append(Regex.escape(source.substring(end)))
            append('$')
        }.toRegex()
    }

    fun arguments(text: String): Array<Any>? {
        if (!text.startsWith(prefix)) return null
        val match = pattern.matchEntire(text) ?: return null
        val arguments = Array<Any>(slots.maxOfOrNull { it.groupValues[1].toInt() } ?: 0) { "" }
        slots.forEachIndexed { index, slot ->
            arguments[slot.groupValues[1].toInt() - 1] = match.groupValues[index + 1]
        }
        return arguments
    }
}
