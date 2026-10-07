package com.mongosky.app.profile

import com.mongosky.app.post.FeedPage
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedSource
import java.util.TreeSet

/** Independent cursors and indexed insertion; tab changes do not fetch pages again. */
internal class OwnProfilePager(private val ownerId: String) {
    private data class Cursor(var initialized: Boolean = false, var next: String? = null,
        var hasMore: Boolean = true, val seen: MutableSet<String> = mutableSetOf(), var serverHead: FeedPost? = null)
    private val cursors = FeedSource.entries.associateWith { Cursor() }
    private val byIdentity = mutableMapOf<String, FeedPost>()
    private val order = compareByDescending<FeedPost> { it.createdAt }.thenByDescending { it.id }.thenBy { it.source }
    private val sorted = TreeSet(order)
    val posts: List<FeedPost> get() = sorted.toList()
    val initializedSources: Set<FeedSource> get() = cursors.filterValues { it.initialized }.keys
    val hasMoreSources: Set<FeedSource> get() = cursors.filterValues { it.hasMore }.keys
    fun cursor(source: FeedSource): String? = cursors.getValue(source).next
    fun newest(source: FeedSource): FeedPost? = sorted.firstOrNull { it.source == source }
    fun newestFromServer(source: FeedSource): FeedPost? = cursors.getValue(source).serverHead
    fun existing(source: FeedSource, id: String): FeedPost? = byIdentity["${source.name}:$id"]

    /** Keep cached older rows until a refreshed cursor reaches their range. */
    fun keepWhileReloading(post: FeedPost): Boolean {
        val cursor = cursors.getValue(post.source)
        if (!cursor.initialized) return true
        if (!cursor.hasMore) return false
        val oldest = sorted.descendingSet().firstOrNull { it.source == post.source }
        return oldest == null || order.compare(post, oldest) > 0
    }

    fun accept(source: FeedSource, page: FeedPage) {
        validate(source, page.posts)
        val state = cursors.getValue(source)
        require(!page.hasMore || (!page.nextCursor.isNullOrBlank() && page.nextCursor != state.next && page.nextCursor !in state.seen)) {
            "The profile returned a repeated or missing cursor."
        }
        page.posts.forEach(::upsert)
        if (page.hasMore) state.seen.add(requireNotNull(page.nextCursor))
        state.next = if (page.hasMore) page.nextCursor else null
        state.hasMore = page.hasMore
        state.initialized = true
        trackServerHead(state, page.posts)
    }
    fun mergeLatest(source: FeedSource, posts: List<FeedPost>) {
        validate(source, posts)
        posts.forEach(::upsert)
    }
    /** Merge fresh head pages without moving an existing older-post cursor. */
    fun mergeLatestPage(source: FeedSource, page: FeedPage, firstPage: Boolean) {
        validate(source, page.posts)
        require(!page.hasMore || !page.nextCursor.isNullOrBlank()) { "The profile returned a missing cursor." }
        val state = cursors.getValue(source)
        val wasEmpty = newest(source) == null
        val hadServerRows = state.serverHead != null
        val needsNewCursor = !state.initialized || (!state.hasMore && (wasEmpty || !hadServerRows))
        if (firstPage) {
            val returnedIds = page.posts.mapTo(HashSet()) { it.id }
            val oldest = page.posts.maxWithOrNull(order)
            val removed = byIdentity.values.filter { cached -> cached.source == source && cached.id !in returnedIds &&
                (!page.hasMore || (oldest != null && order.compare(cached, oldest) <= 0)) }
            removed.forEach { cached -> sorted.remove(cached); byIdentity.remove("${cached.source.name}:${cached.id}") }
        }
        page.posts.forEach(::upsert)
        if (firstPage && (needsNewCursor || !page.hasMore)) {
            state.initialized = true
            state.next = if (page.hasMore) page.nextCursor else null
            state.hasMore = page.hasMore
            state.seen.clear()
            page.nextCursor?.takeIf { page.hasMore }?.let(state.seen::add)
        }
        if (firstPage && (page.posts.isNotEmpty() || !page.hasMore))
            state.serverHead = page.posts.minWithOrNull(order)
        else trackServerHead(state, page.posts)
    }
    private fun trackServerHead(state: Cursor, posts: List<FeedPost>) {
        val latest = posts.minWithOrNull(order) ?: return
        val before = state.serverHead
        if (before == null || order.compare(latest, before) < 0 ||
            (latest.id == before.id && latest.updatedAt >= before.updatedAt)) state.serverHead = latest
    }
    private fun validate(source: FeedSource, posts: List<FeedPost>) {
        require(posts.all { it.id.isNotBlank() && it.userId == ownerId && it.source == source }) { "Invalid own-profile posts." }
    }
    private fun upsert(post: FeedPost) {
        val key = "${post.source.name}:${post.id}"
        val previous = byIdentity[key]
        if (previous != null && previous.updatedAt > post.updatedAt) return
        if (previous != null) sorted.remove(previous)
        byIdentity[key] = post
        sorted.add(post)
    }
}
