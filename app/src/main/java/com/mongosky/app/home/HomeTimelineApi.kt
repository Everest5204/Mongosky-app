package com.mongosky.app.home

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedApi
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedSource
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun FeedApiException.isMissingHomeEndpoint() = statusCode in listOf(404, 405, 501)

internal class HomeTimelineApi(
    private val transport: PostTransport = PostHttp(),
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    @Volatile private var legacyUntil = Long.MIN_VALUE

    suspend fun load(token: String, cursor: String?, visibleIds: List<String> = emptyList()): HomeTimelinePage = withContext(Dispatchers.IO) {
        val limit = if (cursor == null) 8 else 12
        if (clockMillis() < legacyUntil) return@withContext loadLegacy(token, cursor, limit)
        val query = buildMap {
            put("limit", limit.toString())
            cursor?.let { put("cursor", it) }
            if (visibleIds.isNotEmpty()) put("visible_ids", visibleIds.distinct().take(8).joinToString(","))
        }
        try {
            decode(transport.request(token, "GET", "/api/posts/home", query).toString(), cursor)
        } catch (error: FeedApiException) {
            if (!error.isMissingHomeEndpoint()) throw error
            // Deployment can lag behind an app update. Avoid probing a missing
            // route on every page, but discover a newly deployed API later.
            legacyUntil = clockMillis() + LEGACY_RECHECK_MILLIS
            loadLegacy(token, cursor, limit)
        }
    }

    private data class LegacyRow(val post: FeedPost, val raw: JSONObject)

    private suspend fun loadLegacy(token: String, cursor: String?, limit: Int): HomeTimelinePage = coroutineScope {
        suspend fun source(path: String, kind: FeedSource): Pair<List<LegacyRow>, Boolean> {
            val query = buildMap { put("limit", limit.toString()); cursor?.let { put("cursor", it) } }
            val raw = transport.request(token, "GET", path, query)
            val page = FeedApi().parsePage(raw.toString(), kind, cursor)
            val posts = page.posts.associateBy { it.id }
            val rows = raw.getJSONArray("posts")
            return (0 until rows.length()).mapNotNull { index ->
                val row = rows.getJSONObject(index)
                val id = row.optString("id").ifBlank { row.optString("_id") }.lowercase()
                posts[id]?.let { post ->
                    if (kind == FeedSource.TEXT) row.put("type", "text")
                    LegacyRow(post, row)
                }
            } to page.hasMore
        }
        val media = async { source("/api/posts/feed", FeedSource.MEDIA) }
        val text = async { source("/api/text-posts/feed", FeedSource.TEXT) }
        val batches = listOf(media.await(), text.await())
        val ordered = batches.flatMap { it.first }.distinctBy { it.post.id }
            .sortedWith(compareByDescending<LegacyRow> { it.post.createdAt }.thenByDescending { it.post.id })
        val selected = ordered.take(limit)
        val hasMore = ordered.size > limit || batches.any { it.second }
        // Both legacy services use this same timestamp/ObjectID keyset format.
        // Only advance to the last emitted row, preserving unconsumed rows.
        val next = if (hasMore) {
            val last = selected.lastOrNull()?.post ?: throw invalidPostResponse()
            Base64.getUrlEncoder().withoutPadding().encodeToString("${last.createdAt}|${last.id}".toByteArray(Charsets.UTF_8))
        } else null
        decode(JSONObject().put("posts", JSONArray(selected.map { it.raw })).put("has_more", hasMore)
            .put("next_cursor", next ?: JSONObject.NULL).toString(), cursor)
    }

    companion object {
        private const val LEGACY_RECHECK_MILLIS = 5 * 60_000L
        val shared = HomeTimelineApi()
        suspend fun decode(body: String, cursor: String? = null): HomeTimelinePage {
            val decoder = FeedApi()
            val page = decoder.parsePage(body, null, cursor)
            val root = JSONObject(body)
            val visible = root.optJSONArray("visible_posts") ?: JSONArray()
            val visibleRoot = JSONObject().put("posts", visible).put("has_more", false)
            val visiblePage = decoder.parsePage(visibleRoot.toString(), null, null)
            val missing = root.optJSONArray("missing_ids") ?: JSONArray()
            return HomeTimelinePage(page.posts.map { it.toHomePost() }, page.nextCursor, page.hasMore,
                visiblePage.posts.map { it.toHomePost() },
                (0 until missing.length()).mapNotNull { missing.optString(it).takeIf(HomeFeedPolicy::validId) }.toSet(),
                snapshot = if (cursor == null) JSONObject().put("posts", root.getJSONArray("posts"))
                    .put("has_more", false).toString() else null)
        }
    }
}
