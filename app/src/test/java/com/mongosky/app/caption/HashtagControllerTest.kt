@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mongosky.app.caption

import com.mongosky.app.network.PostTransport
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.HomePost
import com.mongosky.app.textpost.TextPost
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class HashtagControllerTest {
    private fun text(caption: String, id: String = "123456789012345678901234") = HomePost.Text(TextPost(
        id, "aaaaaaaaaaaaaaaaaaaaaaaa", FeedAuthor("aaaaaaaaaaaaaaaaaaaaaaaa", "A", "User"),
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), text = caption))
    private fun page(more: Boolean = false, cursor: String? = null) = JSONObject().put("posts", JSONArray())
        .put("has_more", more).put("next_cursor", cursor ?: JSONObject.NULL)

    @Test fun loadedFeedSeedsSuggestionsWithoutNetworkRequests() = runTest {
        var requests = 0
        val api = object : PostTransport {
            override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject {
                requests++; return page()
            }
        }
        val controller = HashtagController(this, { "test-session" }, api, StandardTestDispatcher(testScheduler))
        controller.seed(listOf(text("#travel #travel")))
        advanceUntilIdle()
        assertEquals(0, requests)
        assertEquals(1, controller.state.value.index.suggestions("tra").single().people)
        controller.open("travel")
        advanceUntilIdle()
        assertEquals("travel", controller.state.value.tag)
        assertEquals(2, requests)
        assertEquals(1, controller.state.value.posts.size)
        assertFalse(controller.state.value.hasMore)
        controller.dismiss()
        assertNull(controller.state.value.tag)
        controller.close()
    }
    @Test fun sourceCursorsStaySeparateAndExhaustedSourcesAreNotFetchedAgain() = runTest {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val api = object : PostTransport {
            override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject {
                assertEquals("GET", method)
                requests.add(path to query)
                return if (path == "/api/posts/feed" && query["cursor"] == null) page(true, "media-next") else page()
            }
        }
        val controller = HashtagController(this, { "test-session" }, api, StandardTestDispatcher(testScheduler))
        controller.loadMore(); advanceUntilIdle()
        controller.loadMore(); advanceUntilIdle()
        assertEquals(3, requests.size)
        assertEquals("media-next", requests.last().second["cursor"])
        assertEquals(1, requests.count { it.first == "/api/text-posts/feed" })
        controller.close()
    }
    @Test fun sourceFailureKeepsAlreadyVisibleMatchesAndOffersRetry() = runTest {
        val api = object : PostTransport {
            override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject {
                if (path == "/api/posts/feed") throw IOException("Offline")
                return page()
            }
        }
        val controller = HashtagController(this, { "test-session" }, api, StandardTestDispatcher(testScheduler))
        controller.seed(listOf(text("#বাংলা"))); advanceUntilIdle()
        controller.open("বাংলা"); advanceUntilIdle()
        assertEquals(1, controller.state.value.index.usage("বাংলা")?.people)
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.hasMore)
        controller.close()
    }
    @Test fun profilePostsAreIndexedAndHomeEditsReplaceOldTags() = runTest {
        val controller = HashtagController(this, { "test-session" }, workDispatcher = StandardTestDispatcher(testScheduler))
        val old = text("#old")
        controller.seed(listOf(old)); controller.observe(old); advanceUntilIdle()
        val edited = old.copy(post = old.post.copy(text = "#new", updatedAt = Instant.parse("2026-01-02T00:00:00Z")))
        controller.seed(listOf(edited)); advanceUntilIdle()
        assertNull(controller.state.value.index.usage("old"))
        assertEquals(1, controller.state.value.index.usage("new")?.posts)
        controller.seed(emptyList()); advanceUntilIdle()
        assertTrue(controller.state.value.posts.isEmpty())
        controller.observe(text("#profile")); advanceUntilIdle()
        assertEquals(1, controller.state.value.index.usage("profile")?.people)
        controller.close()
    }
    @Test fun closingSessionCancelsInflightLoadsAndClearsPrivateSnapshot() = runTest {
        var cancelled = 0
        val api = object : PostTransport {
            override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject {
                try { awaitCancellation() } finally { cancelled++ }
            }
        }
        val controller = HashtagController(this, { "test-session" }, api, StandardTestDispatcher(testScheduler))
        controller.seed(listOf(text("#private"))); advanceUntilIdle()
        controller.open("private"); runCurrent()
        controller.close(); advanceUntilIdle()
        assertEquals(2, cancelled)
        assertTrue(controller.state.value.posts.isEmpty())
        assertNull(controller.state.value.tag)
        assertNull(controller.state.value.index.usage("private"))
    }
}
