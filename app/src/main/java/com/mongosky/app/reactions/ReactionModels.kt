package com.mongosky.app.reactions

import com.mongosky.app.post.FeedAuthor

enum class PostReaction(val wireName: String, val label: String, val emoji: String) {
    LOVE("love", "Love", "❤️"), HAHA("haha", "Haha", "😆"),
    WOW("wow", "Wow", "😮"), SAD("sad", "Sad", "😢"), ANGRY("angry", "Angry", "😡");

    companion object {
        fun fromWire(value: String?): PostReaction? = entries.firstOrNull { it.wireName == value }
    }
}

data class ReactionSummary(
    val total: Long = 0,
    val counts: Map<PostReaction, Long> = emptyMap(),
    val topReactions: List<PostReaction> = emptyList(),
    val currentReaction: PostReaction? = null,
    val firstReactor: FeedAuthor? = null
) {
    fun withReaction(next: PostReaction?): ReactionSummary {
        val updated = counts.toMutableMap()
        currentReaction?.let { updated[it] = ((updated[it] ?: 0) - 1).coerceAtLeast(0) }
        next?.let { updated[it] = (updated[it] ?: 0) + 1 }
        val delta = (if (next == null) 0 else 1) - (if (currentReaction == null) 0 else 1)
        return copy(
            total = (total + delta).coerceAtLeast(0), counts = updated,
            currentReaction = next,
            topReactions = PostReaction.entries.filter { (updated[it] ?: 0) > 0 }
                .sortedByDescending { updated[it] ?: 0 }.take(2),
            firstReactor = null
        )
    }
}

data class ReactionPerson(val id: String, val reaction: PostReaction, val author: FeedAuthor)

data class PostReactionState(
    val summary: ReactionSummary = ReactionSummary(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

data class ReactionPeopleState(
    val postId: String, val loading: Boolean = true,
    val people: List<ReactionPerson> = emptyList(), val error: String? = null,
    val total: Long = 0,
    val counts: Map<PostReaction, Long> = emptyMap(),
    val filter: PostReaction? = null,
    val verifiedAuthors: Set<String> = emptySet()
)

data class ReactionPeopleResult(
    val people: List<ReactionPerson>,
    val total: Long,
    val counts: Map<PostReaction, Long>
)

/** Same unabridged text and two-icon ordering as web PostReactions. */
internal fun ReactionSummary.summaryText(): String {
    if (total <= 0) return ""
    val name = firstReactor?.displayName.orEmpty().trim()
    return when {
        name.isEmpty() -> total.toString()
        total == 1L -> name
        else -> "$name +${total - 1}"
    }
}

internal fun ReactionSummary.summaryIcons(): List<PostReaction> = topReactions.distinct().take(2).ifEmpty {
    PostReaction.entries.filter { (counts[it] ?: 0) > 0 }
        .sortedByDescending { counts[it] ?: 0 }.take(2)
}
