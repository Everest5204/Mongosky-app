package com.mongosky.app.presentation.home

import com.mongosky.app.domain.model.FeedAuthor
import com.mongosky.app.domain.model.FeedSource
import com.mongosky.app.domain.model.mediapost.MediaFeedPage
import com.mongosky.app.domain.model.mediapost.MediaPost
import com.mongosky.app.domain.model.textpost.TextFeedPage
import com.mongosky.app.domain.model.textpost.TextPost
import java.time.Instant
import java.util.ArrayDeque

sealed class HomePost {
    abstract val id: String
    abstract val author: FeedAuthor
    abstract val createdAt: Instant
    abstract val likesCount: Long
    abstract val commentsCount: Long
    abstract val source: FeedSource
    val key: String get() = "${source.name}:$id"

    data class Text(val post: TextPost) : HomePost() {
        override val id get() = post.id
        override val author get() = post.author
        override val createdAt get() = post.createdAt
        override val likesCount get() = post.likesCount
        override val commentsCount get() = post.commentsCount
        override val source get() = FeedSource.TEXT
    }

    data class Media(val post: MediaPost) : HomePost() {
        override val id get() = post.id
        override val author get() = post.author
        override val createdAt get() = post.createdAt
        override val likesCount get() = post.likesCount
        override val commentsCount get() = post.commentsCount
        override val source get() = FeedSource.MEDIA
    }
}

/**
 * A merge of two descending streams. An empty, unfinished stream is a barrier:
 * its next page may be newer than the other stream's buffered posts.
 * Text and media never share a cursor. Already displayed posts do not move.
 */
internal class FeedPager {
    private data class Stream(
        val pending: ArrayDeque<HomePost> = ArrayDeque(),
        val seen: MutableSet<String> = mutableSetOf(),
        val cursors: MutableSet<String> = mutableSetOf(),
        var initialized: Boolean = false,
        var cursor: String? = null,
        var hasMore: Boolean = true
    )

    private val media = Stream()
    private val text = Stream()
    private val emitted = ArrayList<HomePost>()
    val initialized: Boolean get() = media.initialized && text.initialized
    val hasMore: Boolean get() = media.hasMore || text.hasMore || media.pending.isNotEmpty() || text.pending.isNotEmpty()
    val size: Int get() = emitted.size

    fun cursor(source: FeedSource): String? = stream(source).cursor

    fun neededSources(): List<FeedSource> = FeedSource.entries.filter { source ->
        val stream = stream(source)
        !stream.initialized || (stream.pending.isEmpty() && stream.hasMore)
    }

    fun accept(page: MediaFeedPage) = accept(FeedSource.MEDIA, page.posts.map(HomePost::Media), page.nextCursor, page.hasMore)
    fun accept(page: TextFeedPage) = accept(FeedSource.TEXT, page.posts.map(HomePost::Text), page.nextCursor, page.hasMore)

    private fun accept(source: FeedSource, posts: List<HomePost>, cursor: String?, hasMore: Boolean) {
        val stream = stream(source)
        require(!hasMore || (!cursor.isNullOrBlank() && !stream.cursors.contains(cursor))) {
            "The feed returned a repeated or missing cursor."
        }
        if (hasMore && cursor != null) stream.cursors.add(cursor)
        posts.forEach { if (stream.seen.add(it.id)) stream.pending.addLast(it) }
        stream.cursor = if (hasMore) cursor else null
        stream.hasMore = hasMore
        stream.initialized = true
    }

    fun drain(): List<HomePost> {
        if (!initialized) return emitted.toList()
        while (true) {
            val m = media.pending.peekFirst()
            val t = text.pending.peekFirst()
            val next = when {
                m != null && t != null -> if (newer(m, t)) media else text
                m != null && !text.hasMore -> media
                t != null && !media.hasMore -> text
                else -> break
            }
            emitted.add(next.pending.removeFirst())
        }
        return emitted.toList()
    }

    private fun stream(source: FeedSource) = if (source == FeedSource.MEDIA) media else text
    private fun newer(first: HomePost, second: HomePost): Boolean =
        first.createdAt > second.createdAt || (first.createdAt == second.createdAt && first.id >= second.id)
}
