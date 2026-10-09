package com.mongosky.app.search

/** The deployed Go API counts Unicode code points, not UTF-16 code units. */
object SearchPolicy {
    const val MAX_QUERY_CODE_POINTS = 80
    const val RESULT_LIMIT = 20
    const val DEBOUNCE_MS = 180L
    const val REFRESH_INTERVAL_MS = 20_000L
    private val whitespace = Regex("[\\s\\p{Z}\\u0085]+")

    fun limitInput(value: String): String {
        if (value.codePointCount(0, value.length) <= MAX_QUERY_CODE_POINTS) return value
        return value.substring(0, value.offsetByCodePoints(0, MAX_QUERY_CODE_POINTS))
    }

    fun normalize(value: String): String = whitespace.replace(limitInput(value), " ").trim()
}
