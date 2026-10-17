package com.mongosky.app.caption

import com.mongosky.app.home.toHomePost
import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.post.FeedApi
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedPage
import com.mongosky.app.post.FeedSource
import com.mongosky.app.post.HomePost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HashtagState(
    val tag: String? = null,
    val posts: List<HomePost> = emptyList(),
    val index: HashtagIndex = HashtagIndex.Empty,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

/** Session-owned snapshot; uses only the deployed feed API. No speculative hashtag endpoint. */
internal class HashtagController(
    parent: CoroutineScope,
    private val readToken: suspend () -> String?,
    private val transport: PostTransport = PostHttp(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val mutable = MutableStateFlow(HashtagState())
    val state = mutable.asStateFlow()
    private var seed = emptyList<HomePost>()
    private val fetched = linkedMapOf<String, HomePost>()
    private val observed = linkedMapOf<String, HomePost>()
    private val cursors = mutableMapOf<FeedSource, String?>()
    private val exhausted = mutableSetOf<FeedSource>()
    private var loadJob: Job? = null
    private var indexJob: Job? = null
    private var indexRevision = 0
    private val decoder = FeedApi()

    fun seed(posts: List<HomePost>) {
        val removed = seed.map { it.key }.toSet() - posts.map { it.key }.toSet()
        removed.forEach { observed.remove(it); fetched.remove(it) }
        seed = posts
        scheduleIndex()
    }
    fun observe(post: HomePost) {
        if (observed[post.key] == post) return
        observed[post.key] = post
        while (observed.size > MAX_POSTS) observed.remove(observed.keys.first())
        scheduleIndex()
    }
    fun open(tag: String) {
        if (tag.isBlank()) return
        mutable.value = mutable.value.copy(tag = tag)
        if (fetched.isEmpty()) loadMore()
    }
    fun dismiss() { mutable.value = mutable.value.copy(tag = null) }
    fun close() { scope.cancel(); mutable.value = HashtagState(); seed = emptyList(); fetched.clear(); observed.clear() }

    fun loadMore(refresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        if (!refresh && (!mutable.value.hasMore || fetched.size >= MAX_POSTS)) return
        if (refresh) { cursors.clear(); exhausted.clear() }
        loadJob = scope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val token = readToken()?.takeIf { it.isNotBlank() }
                    ?: throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED)
                val sources = FeedSource.entries.filter { it !in exhausted }
                val results = supervisorScope {
                    sources.map { source -> async {
                        try { source to Result.success(page(token, source)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { source to Result.failure<FeedPage>(error) }
                    } }.map { it.await() }
                }
                ensureActive()
                var error: String? = null
                for ((source, result) in results) {
                    result.fold(onSuccess = { page ->
                        if (refresh) fetched.entries.removeAll { it.value.source == source }
                        for (post in page.posts) fetched["${source.name}:${post.id}"] = post.toHomePost()
                        cursors[source] = page.nextCursor
                        if (!page.hasMore) exhausted.add(source)
                    }, onFailure = { failure -> error = failure.message ?: "Could not load posts. Try again." })
                }
                while (fetched.size > MAX_POSTS) fetched.remove(fetched.keys.first())
                mutable.value = mutable.value.copy(error = error, hasMore = exhausted.size < FeedSource.entries.size && fetched.size < MAX_POSTS)
                scheduleIndex()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = mutable.value.copy(error = error.message ?: "Could not load posts. Try again.") }
            finally { mutable.value = mutable.value.copy(loading = false) }
        }
    }

    private suspend fun page(token: String, source: FeedSource): FeedPage {
        val path = if (source == FeedSource.MEDIA) "/api/posts/feed" else "/api/text-posts/feed"
        val cursor = cursors[source]
        val query = buildMap { put("limit", FeedApi.DEFAULT_PAGE_SIZE.toString()); cursor?.let { put("cursor", it) } }
        val json = transport.request(token, "GET", path, query)
        return withContext(workDispatcher) { decoder.parsePage(json.toString(), source, cursor) }
    }

    private fun scheduleIndex() {
        indexJob?.cancel()
        val revision = ++indexRevision
        val snapshot = observed.values.toList() + fetched.values.toList() + seed
        indexJob = scope.launch {
            val (posts, index) = withContext(workDispatcher) {
                val posts = snapshot.groupBy { it.key }.values.map { versions ->
                    versions.maxBy { it.captionUpdatedAt() }
                }.sortedByDescending { it.createdAt }.take(MAX_POSTS)
                posts to HashtagIndex.build(posts.map { HashtagDocument(it.key, it.author.id, it.captionContent()) })
            }
            ensureActive()
            if (revision == indexRevision) mutable.value = mutable.value.copy(posts = posts, index = index)
        }
    }
    private companion object { const val MAX_POSTS = 1_000 }
}

internal fun HomePost.captionContent(): String = when (this) {
    is HomePost.Text -> post.displayText
    is HomePost.Media -> post.caption
}

private fun HomePost.captionUpdatedAt() = when (this) {
    is HomePost.Text -> post.updatedAt
    is HomePost.Media -> post.updatedAt
}
