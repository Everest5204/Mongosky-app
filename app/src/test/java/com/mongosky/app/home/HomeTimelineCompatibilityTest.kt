package com.mongosky.app.home

import com.mongosky.app.network.PostHttp
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.HomePost
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeTimelineCompatibilityTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val paths = ConcurrentLinkedQueue<String>()
    private var now = 0L
    @Before fun before() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .readTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    }
    @After fun after() {
        client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        server.shutdown(); client.dispatcher.executorService.shutdownNow()
    }
    private fun api() = HomeTimelineApi(PostHttp(client, server.url("/").toString()), { now })
    private fun id(n: Int) = n.toString(16).padStart(24, '0')
    private fun row(n: Int, kind: String, time: Instant = Instant.ofEpochSecond(n.toLong())) = JSONObject()
        .put("id", id(n)).put("user_id", "a".repeat(24)).put("type", kind)
        .put("created_at", time.toString()).put("text", "Post $n")
        .apply { if (kind == "image") put("media", JSONArray().put(JSONObject().put("url", "https://example.test/$n.jpg"))) }
    private fun body(rows: List<JSONObject>, more: Boolean = false, next: String? = null) = JSONObject()
        .put("posts", JSONArray(rows)).put("has_more", more).put("next_cursor", next ?: JSONObject.NULL).toString()
    private fun respond(block: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths.add(request.requestUrl!!.encodedPath); return block(request)
            }
        }
    }
    private fun ok(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun missing(code: Int = 404) = MockResponse().setResponseCode(code).setBody("404 page not found\n")

    @Test fun currentBackendUsesOneRequestAndReconcilesVisibleIds() = runBlocking {
        val visible = id(1)
        respond { request ->
            assertEquals("/api/posts/home", request.requestUrl!!.encodedPath)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
            assertEquals(visible, request.requestUrl!!.queryParameter("visible_ids"))
            ok(JSONObject(body(listOf(row(4, "text")))).put("visible_posts", JSONArray().put(row(1, "image")))
                .put("missing_ids", JSONArray().put(id(2))).toString())
        }
        val page = api().load("test-token", null, listOf(visible))
        assertEquals(listOf(id(4)), page.posts.map { it.id })
        assertEquals(listOf(visible), page.visiblePosts.map { it.id }); assertEquals(setOf(id(2)), page.missingIds)
        assertEquals(listOf("/api/posts/home"), paths.toList())
    }

    @Test fun plain404FallsBackToBothSourcesConcurrentlyAndCachesTheirSnapshot() = runBlocking {
        val started = CountDownLatch(2)
        respond { request -> when (request.requestUrl!!.encodedPath) {
            "/api/posts/home" -> missing()
            else -> {
                started.countDown()
                if (!started.await(2, TimeUnit.SECONDS)) return@respond MockResponse().setResponseCode(500)
                val text = request.requestUrl!!.encodedPath == "/api/text-posts/feed"
                ok(body(listOf(row(if (text) 4 else 3, if (text) "text" else "image"))))
            }
        } }
        val page = api().load("test-token", null, listOf(id(99)))
        assertEquals(listOf(id(4), id(3)), page.posts.map { it.id })
        assertTrue(page.posts[0] is HomePost.Text); assertTrue(page.posts[1] is HomePost.Media)
        assertEquals(page.posts, HomeTimelineApi.decode(requireNotNull(page.snapshot)).posts)
        assertTrue(page.missingIds.isEmpty()); assertFalse(page.hasMore); assertEquals(3, paths.size)
    }

    @Test fun legacyPaginationHasNoGapsAcrossSkewedStreamsAndEqualTimestamps() = runBlocking {
        val stamp = Instant.parse("2026-10-16T10:00:00.123Z")
        val media = (1..30).map { row(it, "image", stamp) }
        val text = (31..45).map { row(it, "text", stamp) }
        respond { request ->
            val url = request.requestUrl!!
            if (url.encodedPath == "/api/posts/home") return@respond missing()
            assertNull(url.queryParameter("visible_ids"))
            val boundary = url.queryParameter("cursor")?.let {
                String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8).substringAfter('|') }
            val source = if (url.encodedPath == "/api/posts/feed") media else text
            val rows = source.filter { boundary == null || it.getString("id") < boundary }.sortedByDescending { it.getString("id") }
            val limit = url.queryParameter("limit")!!.toInt(); val page = rows.take(limit); val more = rows.size > limit
            val next = if (more) Base64.getUrlEncoder().withoutPadding().encodeToString(
                "$stamp|${page.last().getString("id")}".toByteArray(Charsets.UTF_8)) else null
            ok(body(page, more, next))
        }
        val api = api(); val timeline = HomeTimeline(); var pages = 0
        do { timeline.accept(api.load("test-token", timeline.cursor)); assertTrue(++pages <= 5) } while (timeline.hasMore)
        assertEquals((45 downTo 1).map(::id), timeline.snapshot().map { it.id })
        assertEquals(1, paths.count { it == "/api/posts/home" }); assertEquals(11, paths.size)
    }

    @Test fun emptyLegacyFeedsRenderAnEmptyFeed() = runBlocking {
        respond { request -> if (request.requestUrl!!.encodedPath == "/api/posts/home") missing() else ok(body(emptyList())) }
        val page = api().load("test-token", null); assertTrue(page.posts.isEmpty()); assertFalse(page.hasMore)
    }

    @Test fun unsupportedRouteStatusesFallbackButAuthAndServerFailuresDoNot() = runBlocking {
        for (status in listOf(404, 405, 501, 401, 403, 429, 500, 503)) {
            paths.clear()
            respond { request -> if (request.requestUrl!!.encodedPath == "/api/posts/home") missing(status) else ok(body(listOf(row(1, "text")))) }
            val result = runCatching { api().load("test-token", null) }
            if (status in listOf(404, 405, 501)) { assertTrue(result.isSuccess); assertEquals(3, paths.size) }
            else { assertEquals(status, (result.exceptionOrNull() as FeedApiException).statusCode); assertEquals(1, paths.size) }
        }
    }

    @Test fun expiredLegacySessionIsPropagated() = runBlocking {
        respond { request -> if (request.requestUrl!!.encodedPath == "/api/posts/home") missing() else missing(401) }
        val error = runCatching { api().load("test-token", null) }.exceptionOrNull() as FeedApiException
        assertEquals(FeedApiFailure.SESSION_EXPIRED, error.failure)
    }

    @Test fun missingLegacyRoutesShowAFeedErrorInsteadOfADeletedPostMessage() = runBlocking {
        respond { missing() }
        val error = runCatching { api().load("test-token", null) }.exceptionOrNull() as FeedApiException
        assertFalse(error.message.orEmpty().contains("no longer available")); assertTrue(error.message.orEmpty().contains("home feed"))
    }

    @Test fun malformedModernResponseDoesNotHideTheFailure() = runBlocking {
        respond { ok("{\"posts\":null}") }
        val error = runCatching { api().load("test-token", null) }.exceptionOrNull() as FeedApiException
        assertEquals(FeedApiFailure.INVALID_RESPONSE, error.failure); assertEquals(1, paths.size)
    }

    @Test fun compatibilityExpiresAndDetectsANewBackendWithoutRestarting() = runBlocking {
        var deployed = false
        respond { request -> if (request.requestUrl!!.encodedPath == "/api/posts/home" && !deployed) missing()
            else ok(body(listOf(row(if (deployed) 2 else 1, "text")))) }
        val api = api(); assertEquals(id(1), api.load("test-token", null).posts.single().id)
        deployed = true; now = 299_999; api.load("test-token", null)
        assertEquals(1, paths.count { it == "/api/posts/home" })
        now = 300_001; assertEquals(id(2), api.load("test-token", null).posts.single().id)
        assertEquals(2, paths.count { it == "/api/posts/home" })
    }

    @Test fun cancellingLegacyLoadCancelsBothHttpCalls() = runBlocking {
        val started = CountDownLatch(2); val legacyCalls = AtomicInteger()
        respond { request -> if (request.requestUrl!!.encodedPath == "/api/posts/home") missing()
            else { legacyCalls.incrementAndGet(); started.countDown(); MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) } }
        val request = async(Dispatchers.IO) { api().load("test-token", null) }
        assertTrue(started.await(2, TimeUnit.SECONDS)); withTimeout(2_000) { request.cancelAndJoin() }
        assertEquals(2, legacyCalls.get()); assertTrue(request.isCancelled)
    }
}
