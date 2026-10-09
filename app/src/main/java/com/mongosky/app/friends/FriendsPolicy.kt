package com.mongosky.app.friends

import java.util.Locale

object FriendsPolicy {
    const val PAGE_SIZE = 20
    const val REFRESH_INTERVAL_MS = 20_000L
    const val BADGE_TTL_MS = 60_000L
    const val BADGE_CACHE_LIMIT = 400
    private val idPattern = Regex("[a-f0-9]{24}")
    fun userId(value: String?): String? = value?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { idPattern.matches(it) && it.any { c -> c != '0' } }
}
