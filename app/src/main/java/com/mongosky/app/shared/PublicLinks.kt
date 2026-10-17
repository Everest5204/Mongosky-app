package com.mongosky.app.shared

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Web-compatible URLs; IDs are a single path segment, never a route or query. */
internal object PublicLinks {
    private const val ORIGIN = "https://mongosky.com"
    fun post(id: String): String = "$ORIGIN/post/${segment(id)}"
    fun profile(id: String): String = "$ORIGIN/profile/${segment(id)}"
    fun hashtag(tag: String): String = "$ORIGIN/hashtag/${segment(tag.removePrefix("#"))}"

    private fun segment(value: String): String {
        require(value.isNotBlank()) { "A public link needs a non-empty identifier" }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())
            .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
    }
}
