package com.mongosky.app.caption

import java.text.Normalizer
import java.util.Locale

internal data class ActiveHashtag(val start: Int, val end: Int, val query: String)
internal data class HashtagInsertion(val text: String, val cursor: Int)

internal object HashtagPolicy {
    fun key(tag: String): String = Normalizer.normalize(tag.removePrefix("#"), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun active(text: String, cursor: Int, selectionEnd: Int = cursor): ActiveHashtag? {
        if (cursor != selectionEnd || cursor !in 1..text.length) return null
        val tokens = CaptionTokens.parse(text)
        tokens.firstOrNull { it.hashtag != null && cursor in (it.start + 1)..it.end }?.let {
            return ActiveHashtag(it.start, it.end, text.substring(it.start + 1, cursor))
        }
        // A lone # also offers existing tags. A URL fragment must never activate suggestions.
        val start = cursor - 1
        if (text[start] != '#' || tokens.any { it.start <= start && it.end > start }) return null
        if (start > 0 && (text[start - 1].isLetterOrDigit() || Character.getType(text[start - 1]) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt()) || text[start - 1] == '_' || text[start - 1] == '@')) return null
        return ActiveHashtag(start, cursor, "")
    }

    fun insert(text: String, active: ActiveHashtag, tag: String): HashtagInsertion {
        val clean = tag.removePrefix("#")
        val parsed = CaptionTokens.parse("#$clean").singleOrNull()
        require(parsed?.hashtag == clean && parsed.end == clean.length + 1)
        require(active.start in text.indices && active.end in (active.start + 1)..text.length)
        val suffix = text.substring(active.end)
        val space = if (suffix.isEmpty() || !suffix.first().isWhitespace()) " " else ""
        val inserted = "#$clean$space"
        return HashtagInsertion(text.substring(0, active.start) + inserted + suffix, active.start + inserted.length + if (space.isEmpty() && suffix.isNotEmpty()) 1 else 0)
    }
}

internal data class HashtagDocument(val key: String, val authorId: String, val content: String)
internal data class HashtagSuggestion(val tag: String, val people: Int, val posts: Int)

/** Exact counts for this snapshot of accessible loaded posts, never a claimed global total. */
internal class HashtagIndex private constructor(
    private val rows: Map<String, HashtagSuggestion>, private val postKeys: Map<String, Set<String>>
) {
    fun suggestions(prefix: String): List<HashtagSuggestion> {
        val query = HashtagPolicy.key(prefix)
        return rows.entries.asSequence().filter { it.key.startsWith(query) }.map { it.value }
            .sortedWith(compareByDescending<HashtagSuggestion> { it.people }.thenByDescending { it.posts }.thenBy { it.tag })
            .take(6).toList()
    }
    fun matchingKeys(tag: String): Set<String> = postKeys[HashtagPolicy.key(tag)].orEmpty()
    fun usage(tag: String): HashtagSuggestion? = rows[HashtagPolicy.key(tag)]

    companion object {
        val Empty = build(emptyList())
        fun build(documents: List<HashtagDocument>): HashtagIndex {
            val tags = linkedMapOf<String, String>()
            val users = mutableMapOf<String, MutableSet<String>>()
            val posts = mutableMapOf<String, MutableSet<String>>()
            // Last version of a post wins: editing/deleting tags never adds duplicate usage.
            for (doc in documents.associateBy { it.key }.values) {
                val found = CaptionTokens.parse(doc.content).mapNotNull { it.hashtag }.associateBy(HashtagPolicy::key)
                for ((key, tag) in found) {
                    tags.putIfAbsent(key, tag)
                    posts.getOrPut(key) { linkedSetOf() }.add(doc.key)
                    if (doc.authorId.isNotBlank()) users.getOrPut(key) { linkedSetOf() }.add(doc.authorId)
                }
            }
            return HashtagIndex(tags.mapValues { (key, tag) -> HashtagSuggestion(tag, users[key]?.size ?: 0, posts[key]?.size ?: 0) },
                posts.mapValues { it.value.toSet() })
        }
    }
}
