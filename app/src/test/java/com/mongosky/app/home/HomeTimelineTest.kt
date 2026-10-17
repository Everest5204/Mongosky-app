package com.mongosky.app.home

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.HomePost
import com.mongosky.app.textpost.TextPost
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

internal fun homeTestPost(n: Int, text: String = "post $n"): HomePost.Text {
    val id = n.toString(16).padStart(24, '0')
    val author = "a".repeat(24)
    return HomePost.Text(TextPost(id, author, FeedAuthor(author, "Test"),
        Instant.ofEpochSecond(n.toLong()), Instant.ofEpochSecond(n.toLong()), text = text, isSelf = true))
}

class HomeTimelineTest {
    @Test fun mixedFeedHasOneCursorAndDeduplicatesOverlaps() {
        val timeline = HomeTimeline()
        timeline.accept(HomeTimelinePage(listOf(homeTestPost(4), homeTestPost(3)), "next", true))
        timeline.accept(HomeTimelinePage(listOf(homeTestPost(3), homeTestPost(2)), hasMore = false))
        assertEquals(listOf(4, 3, 2).map { homeTestPost(it).key }, timeline.snapshot().map { it.key })
        assertFalse(timeline.hasMore)
    }

    @Test fun repeatedCursorDoesNotMutateVisibleRows() {
        val timeline = HomeTimeline()
        timeline.accept(HomeTimelinePage(listOf(homeTestPost(4)), "same", true))
        try { timeline.accept(HomeTimelinePage(listOf(homeTestPost(3)), "same", true)); fail("accepted repeated cursor") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf(homeTestPost(4)), timeline.snapshot())
    }

    @Test fun refreshOrdersConfirmedPublicationAgainstNewerServerPosts() {
        val timeline = HomeTimeline()
        timeline.publish(homeTestPost(3))
        timeline.accept(HomeTimelinePage(listOf(homeTestPost(4), homeTestPost(3))))
        assertEquals(listOf(homeTestPost(4), homeTestPost(3)), timeline.snapshot())
        assertTrue(timeline.unseenPublished.isEmpty())
    }

    @Test fun unifiedDecoderPreservesSourcesBackgroundAndDimensions() = runTest {
        val author = "a".repeat(24)
        val body = """{"posts":[
          {"id":"000000000000000000000003","user_id":"$author","type":"text","text":"Hi","created_at":"2026-01-01T00:00:00Z","text_background":{"kind":"solid","value":"#123456"}},
          {"id":"000000000000000000000002","user_id":"$author","type":"image","created_at":"2025-01-01T00:00:00Z","media":[{"url":"https://example.test/p.jpg","width":800,"height":1000}]},
          {"id":"000000000000000000000001","user_id":"$author","type":"video","created_at":"2024-01-01T00:00:00Z"}
        ],"has_more":false}"""
        val page = HomeTimelineApi.decode(body)
        assertEquals(2, page.posts.size)
        assertEquals("#123456", (page.posts[0] as HomePost.Text).post.textBackground.value)
        assertEquals(1000, (page.posts[1] as HomePost.Media).post.media.single().height)
        assertFalse(HomeTimelineApi.decode(requireNotNull(page.snapshot)).hasMore)
    }
}
