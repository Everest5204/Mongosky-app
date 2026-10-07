package com.mongosky.app.textpost

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedTextBackground
import com.mongosky.app.post.FeedTextBackgroundKind
import com.mongosky.app.post.FeedTextStyle
import java.time.Instant

/** These values are the web composer's presets, including the CSS sent to the API. */
enum class TextPostPreset(
    val id: String,
    val label: String,
    val background: FeedTextBackground,
    val textColor: String = "#ffffff"
) {
    NONE("none", "No background", FeedTextBackground(), "#111827"),
    PINK_PURPLE("pink-purple", "Pink purple", gradient("linear-gradient(135deg, #ec008c 0%, #7b2ff7 100%)")),
    BLUE_VIOLET("blue-violet", "Blue violet", gradient("linear-gradient(135deg, #2563eb 0%, #7c3aed 100%)")),
    SUNSET("sunset", "Sunset", gradient("linear-gradient(135deg, #ff512f 0%, #dd2476 100%)")),
    GREEN("green", "Green", FeedTextBackground(FeedTextBackgroundKind.SOLID, "#059669")),
    RED("red", "Red", FeedTextBackground(FeedTextBackgroundKind.SOLID, "#e11d48")),
    BLACK("black", "Black", FeedTextBackground(FeedTextBackgroundKind.SOLID, "#020617")),
    NEON("neon", "Neon", gradient("radial-gradient(circle at 20% 20%, #22d3ee 0%, transparent 28%), radial-gradient(circle at 80% 10%, #f472b6 0%, transparent 28%), linear-gradient(135deg, #111827 0%, #4c1d95 100%)")),
    SKY("sky", "Sky", gradient("linear-gradient(135deg, #38bdf8 0%, #0f172a 100%)")),
    SOFT("soft", "Soft", gradient("linear-gradient(135deg, #fef3c7 0%, #fecaca 50%, #ddd6fe 100%)"), "#111827");

    companion object {
        fun fromId(id: String?): TextPostPreset = entries.firstOrNull { it.id == id } ?: PINK_PURPLE
    }
}

private fun gradient(value: String) = FeedTextBackground(FeedTextBackgroundKind.GRADIENT, value)

object TextPostLimits {
    const val MAX_CHARACTERS = 650

    // The Go backend counts Unicode code points. Do not split an emoji surrogate pair.
    fun length(text: String): Int = text.codePointCount(0, text.length)
    fun limit(text: String): String = if (length(text) <= MAX_CHARACTERS) text else
        text.substring(0, text.offsetByCodePoints(0, MAX_CHARACTERS))
}

data class TextPostDraft(
    val ownerUserId: String,
    val text: String,
    val preset: TextPostPreset
)

sealed interface TextPostPublication {
    data object Idle : TextPostPublication
    data object Publishing : TextPostPublication
    data class Published(val post: TextPost) : TextPostPublication
    data class Failed(
        val message: String,
        val canRetry: Boolean = false,
        val outcomeUnknown: Boolean = false,
        val requiresSignIn: Boolean = false
    ) : TextPostPublication
}

data class TextPostComposerState(
    val text: String = "",
    val preset: TextPostPreset = TextPostPreset.PINK_PURPLE,
    val sessionReady: Boolean = false,
    val publication: TextPostPublication = TextPostPublication.Idle
) {
    val busy: Boolean get() = publication is TextPostPublication.Publishing || publication is TextPostPublication.Published
    val uncertain: Boolean get() = (publication as? TextPostPublication.Failed)?.outcomeUnknown == true
    val editable: Boolean get() = !busy && !uncertain &&
        (publication as? TextPostPublication.Failed)?.requiresSignIn != true
    val remaining: Int get() = TextPostLimits.MAX_CHARACTERS - TextPostLimits.length(text)
    val canPost: Boolean get() = sessionReady && editable && text.isNotBlank() && remaining >= 0 &&
        ((publication as? TextPostPublication.Failed)?.canRetry ?: true)
}

/**
 * A text post from /api/text-posts/feed.
 * Text content and styling stay separate from media post content.
 * User details reuse the shared author model.
 */
data class TextPost(
    val id: String,
    val userId: String,
    val author: FeedAuthor,
    val createdAt: Instant,
    val updatedAt: Instant,
    val text: String = "",
    val caption: String = "",
    val textBackground: FeedTextBackground = FeedTextBackground(),
    val textColor: String = "#111827",
    val textStyle: FeedTextStyle = FeedTextStyle.BOLD_CENTER,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val isSelf: Boolean = false,
    val isFollowing: Boolean = false
) {
    val displayText: String
        get() = if (text.isNotBlank()) text else caption
}

/**
 * One page of text posts. Its cursor belongs only to the text feed.
 * An absent or empty server cursor is represented as null.
 */
data class TextFeedPage(
    val posts: List<TextPost>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)
