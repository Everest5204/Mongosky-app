package com.mongosky.app.home

import com.mongosky.app.mediapost.MediaFeedPage
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.FeedSource
import com.mongosky.app.post.HomePost
import com.mongosky.app.textpost.TextFeedPage
import com.mongosky.app.textpost.TextPost
import java.time.Instant
import java.util.ArrayDeque

/**
 * Merges descending text and media streams with separate cursors.
 * Confirmed photo uploads appear immediately without changing either cursor.
 */
internal class FeedPager(
    publishedPosts: List<MediaPost> = emptyList(),
    publishedTextPosts: List<TextPost> = emptyList()
) {
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
    private val unseenPublished = linkedMapOf<String, MediaPost>()
    private val unseenPublishedText = linkedMapOf<String, TextPost>()

    val initialized: Boolean
        get() = media.initialized && text.initialized

    val hasMore: Boolean
        get() = media.hasMore ||
                text.hasMore ||
                media.pending.isNotEmpty() ||
                text.pending.isNotEmpty()

    val size: Int
        get() = emitted.size

    /** Includes buffered posts; head polling must not call them new posts. */
    fun contains(post: HomePost): Boolean = post.id in stream(post.source).seen

    /** Update known posts without consuming a cursor or inserting a new head. */
    fun update(post: HomePost) {
        val index = emitted.indexOfFirst { it.key == post.key }
        if (index >= 0 && post.updatedAt >= emitted[index].updatedTime()) emitted[index] = post
        val stream = stream(post.source)
        val pending = stream.pending.map { old -> if (old.key == post.key && post.updatedAt >= old.updatedTime()) post else old }
        stream.pending.clear(); stream.pending.addAll(pending)
    }

    private fun HomePost.updatedTime() = when (this) {
        is HomePost.Text -> post.updatedAt
        is HomePost.Media -> post.updatedAt
    }

    fun remove(key: String) {
        emitted.removeAll { it.key == key }
        media.pending.removeAll { it.key == key }
        text.pending.removeAll { it.key == key }
        unseenPublished.entries.removeAll { "MEDIA:${it.key}" == key }
        unseenPublishedText.entries.removeAll { "TEXT:${it.key}" == key }
    }

    /** Carry these across a refresh until the media feed has returned them. */
    val unseenPublishedPosts: List<MediaPost>
        get() = unseenPublished.values.sortedWith(
            compareByDescending<MediaPost> {
                it.createdAt
            }.thenByDescending {
                it.id
            }
        )

    /** Keep a confirmed text post visible until the refreshed feed returns it. */
    val unseenPublishedTextPosts: List<TextPost>
        get() = unseenPublishedText.values.sortedWith(
            compareByDescending<TextPost> {
                it.createdAt
            }.thenByDescending {
                it.id
            }
        )

    init {
        publishedPosts.asReversed().forEach {
            insertPublishedPost(it)
        }
        publishedTextPosts.asReversed().forEach {
            insertPublishedTextPost(it)
        }
        emitted.sortWith(
            compareByDescending<HomePost> {
                it.createdAt
            }.thenByDescending {
                it.id
            }
        )
    }

    /** Only confirmed, owned photo posts belong in Home. */
    fun insertPublishedPost(post: MediaPost): Boolean {
        if (
            !post.isSelf ||
            post.id.isBlank() ||
            post.type != MediaPostType.IMAGE ||
            post.media.isEmpty() ||
            post.media.any { it.type != FeedMediaType.IMAGE }
        ) {
            return false
        }

        val index = emitted.indexOfFirst {
            it.source == FeedSource.MEDIA && it.id == post.id
        }

        val existing =
            (emitted.getOrNull(index) as? HomePost.Media)?.post
                ?: (
                        media.pending.firstOrNull {
                            it.id == post.id
                        } as? HomePost.Media
                        )?.post

        val newest = if (
            existing != null &&
            existing.updatedAt >= post.updatedAt
        ) {
            existing
        } else {
            post
        }

        val alreadyInFeed =
            post.id in media.seen &&
                    post.id !in unseenPublished

        media.pending.removeAll {
            it.id == post.id
        }

        media.seen.add(post.id)

        if (index >= 0) {
            emitted[index] = HomePost.Media(newest)
        } else {
            emitted.add(0, HomePost.Media(newest))
        }

        if (!alreadyInFeed) {
            unseenPublished[post.id] = newest
        }

        return true
    }

    /** A confirmed text creation changes neither feed cursor nor buffered media. */
    fun insertPublishedTextPost(post: TextPost): Boolean {
        if (!post.isSelf || post.id.isBlank() || post.displayText.isBlank()) {
            return false
        }

        val index = emitted.indexOfFirst {
            it.source == FeedSource.TEXT && it.id == post.id
        }
        val existing =
            (emitted.getOrNull(index) as? HomePost.Text)?.post
                ?: (text.pending.firstOrNull { it.id == post.id } as? HomePost.Text)?.post
        val newest = if (existing != null && existing.updatedAt >= post.updatedAt) {
            existing
        } else {
            post
        }
        val alreadyInFeed = post.id in text.seen && post.id !in unseenPublishedText

        text.pending.removeAll { it.id == post.id }
        text.seen.add(post.id)

        if (index >= 0) {
            emitted[index] = HomePost.Text(newest)
        } else {
            emitted.add(0, HomePost.Text(newest))
        }
        if (!alreadyInFeed) {
            unseenPublishedText[post.id] = newest
        }
        return true
    }

    fun cursor(source: FeedSource): String? =
        stream(source).cursor

    fun neededSources(): List<FeedSource> =
        FeedSource.entries.filter { source ->
            val stream = stream(source)

            !stream.initialized ||
                    (stream.pending.isEmpty() && stream.hasMore)
        }

    fun accept(page: MediaFeedPage) {
        accept(
            source = FeedSource.MEDIA,
            posts = page.posts.map(HomePost::Media),
            cursor = page.nextCursor,
            hasMore = page.hasMore
        )
    }

    fun accept(page: TextFeedPage) {
        accept(
            source = FeedSource.TEXT,
            posts = page.posts.map(HomePost::Text),
            cursor = page.nextCursor,
            hasMore = page.hasMore
        )
    }

    private fun accept(
        source: FeedSource,
        posts: List<HomePost>,
        cursor: String?,
        hasMore: Boolean
    ) {
        val stream = stream(source)

        require(
            !hasMore ||
                    (
                            !cursor.isNullOrBlank() &&
                                    !stream.cursors.contains(cursor)
                            )
        ) {
            "The feed returned a repeated or missing cursor."
        }

        if (hasMore && cursor != null) {
            stream.cursors.add(cursor)
        }

        posts.forEach { post ->
            if (post is HomePost.Text) {
                val published = unseenPublishedText[post.id]
                if (published != null && post.post.updatedAt >= published.updatedAt) {
                    val index = emitted.indexOfFirst { it.key == post.key }
                    if (index >= 0) {
                        emitted[index] = post
                    }
                    unseenPublishedText.remove(post.id)
                }
            }

            if (post is HomePost.Media) {
                val published = unseenPublished[post.id]

                if (
                    published != null &&
                    post.post.updatedAt >= published.updatedAt
                ) {
                    val index = emitted.indexOfFirst {
                        it.key == post.key
                    }

                    if (index >= 0) {
                        emitted[index] = post
                    }

                    unseenPublished.remove(post.id)
                }
            }

            if (stream.seen.add(post.id)) {
                stream.pending.addLast(post)
            }
        }

        stream.cursor = if (hasMore) cursor else null
        stream.hasMore = hasMore
        stream.initialized = true
    }

    fun drain(): List<HomePost> {
        if (!initialized) {
            return emitted.toList()
        }

        while (true) {
            val mediaPost = media.pending.peekFirst()
            val textPost = text.pending.peekFirst()

            val next = when {
                mediaPost != null && textPost != null -> {
                    if (newer(mediaPost, textPost)) media else text
                }

                mediaPost != null && !text.hasMore -> media
                textPost != null && !media.hasMore -> text
                else -> break
            }

            emitted.add(next.pending.removeFirst())
        }

        return emitted.toList()
    }

    private fun stream(source: FeedSource): Stream =
        if (source == FeedSource.MEDIA) media else text

    private fun newer(
        first: HomePost,
        second: HomePost
    ): Boolean =
        first.createdAt > second.createdAt ||
                (
                        first.createdAt == second.createdAt &&
                                first.id >= second.id
                        )
}
