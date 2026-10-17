package com.mongosky.app.caption

import java.net.URI
import java.util.regex.Pattern

internal data class CaptionToken(val start: Int, val end: Int, val url: String, val hashtag: String? = null)

/** UTF-16 offsets match Compose. Parse locally; no network work during typing or scrolling. */
internal object CaptionTokens {
    private val pattern = Pattern.compile(
        "(?<![\\p{L}\\p{M}\\p{N}_@])(?:https?://[^\\s<>\\\"\\p{Z}]+|" +
            "(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}" +
            "(?::[0-9]{1,5})?(?:[/?#][^\\s<>\\\"\\p{Z}]*)?" +
            "|#[\\p{L}\\p{N}_][\\p{L}\\p{M}\\p{N}_]*)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    fun parse(text: String): List<CaptionToken> {
        val matcher = pattern.matcher(text)
        val result = ArrayList<CaptionToken>()
        while (matcher.find()) {
            val raw = matcher.group()
            val value = if (raw.startsWith('#')) raw else trimPunctuation(raw)
            if (value.isEmpty()) continue
            val tag = value.takeIf { it.startsWith('#') }?.drop(1)
            val url = if (tag != null) "" else {
                val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
                val uri = try { URI(candidate) } catch (_: Exception) { continue }
                if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port > 65535) continue
                candidate
            }
            result.add(CaptionToken(matcher.start(), matcher.start() + value.length, url, tag))
        }
        return result
    }

    private fun trimPunctuation(raw: String): String {
        var value = raw.trimEnd('.', ',', '!', '?', ';', ':', '\'', '’', '”', '।', '，', '。')
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) {
                value = value.dropLast(1)
            }
        }
        return value
    }
}
