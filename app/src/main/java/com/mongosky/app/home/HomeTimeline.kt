package com.mongosky.app.home

import com.mongosky.app.post.HomePost

internal data class HomeTimelinePage(
    val posts: List<HomePost>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val visiblePosts: List<HomePost> = emptyList(),
    val missingIds: Set<String> = emptySet(),
    val snapshot: String? = null
)

/** One server cursor, deterministic order, no text/media merge barrier. */
internal class HomeTimeline {
    private val rows = linkedMapOf<String, HomePost>()
    private val cursors = mutableSetOf<String>()
    private val unpublished = linkedMapOf<String, HomePost>()
    var initialized = false
        private set
    var cursor: String? = null
        private set
    var hasMore = true
        private set
    val unseenPublished get() = unpublished.values.toList()

    fun contains(post: HomePost) = rows.containsKey(post.key)
    fun snapshot(): List<HomePost> = rows.values.toList()

    fun accept(page: HomeTimelinePage) {
        require(!page.hasMore || (!page.nextCursor.isNullOrBlank() && page.nextCursor !in cursors)) {
            "The feed returned a repeated or missing cursor."
        }
        if (page.hasMore) cursors.add(requireNotNull(page.nextCursor))
        page.posts.forEach { post ->
            val old = rows[post.key]
            if (old == null || post.updatedAt >= old.updatedAt) rows[post.key] = post
            unpublished[post.key]?.let { if (post.updatedAt >= it.updatedAt) unpublished.remove(post.key) }
        }
        if (!initialized && rows.isNotEmpty()) {
            val ordered = rows.values.sortedWith(compareByDescending<HomePost> { it.createdAt }.thenByDescending { it.id })
            rows.clear(); ordered.forEach { rows[it.key] = it }
        }
        cursor = if (page.hasMore) page.nextCursor else null
        hasMore = page.hasMore
        initialized = true
    }

    fun publish(post: HomePost) {
        unpublished[post.key] = post
        val all = (listOf(post) + rows.values).distinctBy { it.key }
            .sortedWith(compareByDescending<HomePost> { it.createdAt }.thenByDescending { it.id })
        rows.clear(); all.forEach { rows[it.key] = it }
    }

    fun update(post: HomePost) {
        val old = rows[post.key] ?: return
        if (post.updatedAt >= old.updatedAt) rows[post.key] = post
    }

    fun remove(key: String) { rows.remove(key); unpublished.remove(key) }
}
