package com.mongosky.app.pinpost

import com.mongosky.app.home.isOwn
import com.mongosky.app.post.*
import java.time.Instant

internal data class PinTarget(val ownerId: String, val postId: String, val source: String) {
    companion object {
        fun from(post: HomePost): PinTarget? = if (!post.isOwn) null else
            PinTarget(post.author.id, post.id, if (post.source == FeedSource.TEXT) "text" else "media")
    }
}
internal data class PinSnapshot(val ownerId: String, val revision: Long, val post: FeedPost?, val pinnedAt: Instant?) {
    fun contains(target: PinTarget) = ownerId == target.ownerId && post?.id == target.postId &&
        post.source == if (target.source == "text") FeedSource.TEXT else FeedSource.MEDIA
}
internal data class PinAttempt(val target: PinTarget, val pinned: Boolean, val expectedRevision: Long, val requestId: String)
internal data class PinUpdate(val attempt: PinAttempt, val resultRevision: Long, val current: PinSnapshot)
internal data class PinDialogState(val target: PinTarget, val baseline: PinSnapshot? = null,
    val loading: Boolean = true, val busy: Boolean = false, val uncertain: Boolean = false,
    val attempt: PinAttempt? = null, val error: String? = null, val needsRefresh: Boolean = false, val retryAtMillis: Long = 0) {
    val unpin get() = attempt?.let { !it.pinned } ?: baseline?.contains(target) == true
}
internal object PinPolicy {
    private val ids = Regex("[a-f0-9]{24}")
    private val requests = Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    fun validId(value: String) = ids.matches(value) && value.any { it != '0' }
    fun valid(target: PinTarget) = validId(target.ownerId) && validId(target.postId) && target.source in setOf("text", "media")
    fun validRequest(value: String) = requests.matches(value)
}

/** One pinned row is merged separately; feed cursors and photo/video gallery order stay independent. */
internal fun profilePinPosts(posts: List<FeedPost>, pin: PinSnapshot?, owner: String): List<FeedPost> {
    val pinned = pin?.takeIf { it.ownerId == owner }?.post?.takeIf { it.userId == owner && it.author.id == owner } ?: return posts
    val existing = posts.firstOrNull { it.id == pinned.id && it.source == pinned.source }
    val first = existing?.takeIf { it.updatedAt >= pinned.updatedAt } ?: pinned
    return listOf(first) + posts.filterNot { it.id == first.id && it.source == first.source }
}
