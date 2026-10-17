package com.mongosky.app.home

import androidx.lifecycle.ViewModelStore
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedStartupTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun keep(model: HomeFeedViewModel) = model.also { models.put("feed", it) }
    private class MemoryCache(var page: HomeTimelinePage? = null) : HomeFeedCache {
        var clears = 0; var writes = 0
        override suspend fun read(token: String) = page
        override suspend fun clear(token: String) { clears++; page = null }
        override suspend fun write(token: String, snapshot: String) { writes++ }
    }

    @Test fun savedPostsAppearWhileFirstNetworkRequestIsPending() = runTest(dispatcher) {
        val pending = CompletableDeferred<HomeTimelinePage>()
        var requests = 0
        val cache = MemoryCache(HomeTimelinePage(listOf(homeTestPost(1))))
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> requests++; pending.await() }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent()
        assertEquals(listOf(homeTestPost(1)), model.uiState.posts)
        assertEquals(1, requests)
        pending.complete(HomeTimelinePage(listOf(homeTestPost(2))))
        runCurrent()
        assertEquals(listOf(homeTestPost(2)), model.uiState.posts)
        assertFalse(model.uiState.loading)
    }

    @Test fun lateCacheCannotReplaceFreshNetworkPosts() = runTest(dispatcher) {
        val pendingCache = CompletableDeferred<HomeTimelinePage>()
        val cache = object : HomeFeedCache { override suspend fun read(token: String) = pendingCache.await() }
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> HomeTimelinePage(listOf(homeTestPost(2))) }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent()
        pendingCache.complete(HomeTimelinePage(listOf(homeTestPost(1)))); runCurrent()
        assertEquals(listOf(homeTestPost(2)), model.uiState.posts)
    }

    @Test fun repeatedPaginationSignalsHaveOnlyOneActiveRequest() = runTest(dispatcher) {
        val pending = CompletableDeferred<HomeTimelinePage>(); var requests = 0
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> requests++; pending.await() }, updates = { emptyFlow() }))
        model.startSession(); runCurrent(); repeat(30) { model.loadMore() }; runCurrent()
        assertEquals(1, requests)
        pending.complete(HomeTimelinePage(emptyList())); runCurrent()
    }

    @Test fun logoutRejectsEvenANonCancellableOldResponse() = runTest(dispatcher) {
        val pending = CompletableDeferred<HomeTimelinePage>()
        val cache = MemoryCache()
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> withContext(NonCancellable) { pending.await() } }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent(); model.endSession()
        pending.complete(HomeTimelinePage(listOf(homeTestPost(1)), snapshot = "old")); runCurrent()
        assertTrue(model.uiState.posts.isEmpty()); assertEquals(0, cache.writes); assertEquals(1, cache.clears)
    }

    @Test fun activityTeardownRetainsCacheButLogoutClearsIt() = runTest(dispatcher) {
        val cache = MemoryCache()
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> HomeTimelinePage(emptyList()) }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent(); model.detach(); runCurrent(); assertEquals(0, cache.clears)
        model.startSession(); runCurrent(); model.endSession(); runCurrent(); assertEquals(1, cache.clears)
    }

    @Test fun offlineRefreshKeepsReadableCachedPosts() = runTest(dispatcher) {
        val cache = MemoryCache(HomeTimelinePage(listOf(homeTestPost(1))))
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> throw IOException("offline") }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent()
        assertEquals(1, model.uiState.posts.size)
        assertTrue(model.uiState.errors.isNotEmpty())
        assertFalse(model.uiState.sessionExpired)
    }

    @Test fun revokedSessionClearsFeedAndCache() = runTest(dispatcher) {
        val cache = MemoryCache(HomeTimelinePage(listOf(homeTestPost(1))))
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ -> throw FeedApiException("expired", FeedApiFailure.SESSION_EXPIRED) }, cache = cache, updates = { emptyFlow() }))
        model.startSession(); runCurrent()
        assertTrue(model.uiState.sessionExpired); assertTrue(model.uiState.posts.isEmpty()); assertEquals(1, cache.clears)
    }

    @Test fun realtimeBurstsWaitForScrollAndDoNotPrependRows() = runTest(dispatcher) {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 20)
        var requests = 0
        val model = keep(HomeFeedViewModel({ "token" }, loadHome = { _, _, _ ->
            requests++; HomeTimelinePage(if (requests == 1) listOf(homeTestPost(1)) else listOf(homeTestPost(2), homeTestPost(1)))
        }, updates = { events }, clockMillis = { testScheduler.currentTime }))
        model.setForeground(true); model.startSession(); runCurrent(); model.setScrolling(true)
        repeat(20) { events.tryEmit(Unit) }; runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, requests)
        model.setScrolling(false); advanceTimeBy(2_000); runCurrent()
        assertEquals(2, requests)
        assertEquals(listOf(homeTestPost(1)), model.uiState.posts)
        assertEquals(1, model.uiState.newPostsCount)
    }

    @Test fun missingRealtimeEndpointKeepsFeedAndUsesSlowReprobe() = runTest(dispatcher) {
        var streams = 0
        val model = keep(HomeFeedViewModel({ "token" },
            loadHome = { _, _, _ -> HomeTimelinePage(listOf(homeTestPost(1))) },
            updates = { flow { streams++; throw FeedApiException("missing route", FeedApiFailure.REQUEST_REJECTED, 404) } },
            clockMillis = { testScheduler.currentTime }))
        try {
            model.setForeground(true); model.startSession(); runCurrent()
            advanceTimeBy(60_000); runCurrent()
            assertEquals(1, streams); assertEquals(listOf(homeTestPost(1)), model.uiState.posts)
            assertFalse(model.uiState.sessionExpired)
            advanceTimeBy(243_000); runCurrent(); assertEquals(2, streams)
        } finally { model.detach(); runCurrent() }
    }
}
