package com.mongosky.app.reels

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.network.postCount
import com.mongosky.app.network.postObjectId
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.reactions.PostReaction
import java.time.Instant
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface ReelsDataSource {
    suspend fun page(token: String, cursor: String? = null): ReelsPage
    suspend fun preview(token: String): ReelsPage = page(token)
    suspend fun reel(token: String, id: String): Reel
    suspend fun verified(token: String, authorIds: List<String>): Set<String>
}

/** Uses the same MongoDB-backed endpoints as the web, with fresh metadata on every read. */
class ReelsApi internal constructor(private val http: PostTransport) : ReelsDataSource {
    constructor() : this(PostHttp())

    override suspend fun page(token: String, cursor: String?): ReelsPage {
        return loadPage(token, cursor, PAGE_SIZE)
    }

    override suspend fun preview(token: String): ReelsPage = loadPage(token, null, PREVIEW_SIZE)

    private suspend fun loadPage(token: String, cursor: String?, limit: Int): ReelsPage {
        val query = mutableMapOf("limit" to limit.toString(), "t" to System.currentTimeMillis().toString())
        cursor?.let { require(it.isNotBlank() && it.length <= 1024); query["cursor"] = it }
        val raw = http.request(token, "GET", "/api/reels", query)
        return withContext(Dispatchers.Default) { parse {
            val rows = raw.getJSONArray("reels")
            if (rows.length() > limit) throw invalidPostResponse()
            val next = raw.optString("next_cursor").trim().takeIf { it.isNotEmpty() }
            val more = raw.getBoolean("has_more")
            if (next != null && next.length > 1024 || more && (next == null || next == cursor)) throw invalidPostResponse()
            ReelsPage((0 until rows.length()).map { readReel(rows.getJSONObject(it)) }.distinctBy { it.id }, next, more)
        } }
    }

    override suspend fun reel(token: String, id: String): Reel {
        val safeId = postObjectId(id)
        val raw = http.request(token, "GET", "/api/reels/$safeId", mapOf("t" to System.currentTimeMillis().toString()))
        return withContext(Dispatchers.Default) { parse {
            readReel(raw.getJSONObject("reel")).also { if (it.id != safeId.lowercase()) throw invalidPostResponse() }
        } }
    }

    override suspend fun verified(token: String, authorIds: List<String>): Set<String> {
        val ids = authorIds.map { postObjectId(it).lowercase() }.distinct()
        if (ids.isEmpty()) return emptySet()
        require(ids.size <= 200)
        val raw = http.request(token, "POST", "/api/verified-badges/resolve",
            body = JSONObject().put("user_ids", JSONArray(ids)))
        return parse {
            val badges = raw.getJSONArray("badges")
            if (badges.length() > 200) throw invalidPostResponse()
            (0 until badges.length()).mapNotNull { index ->
                val row = badges.getJSONObject(index)
                val id = postObjectId(row.getString("user_id")).lowercase()
                if (id !in ids) throw invalidPostResponse()
                id.takeIf { row.optString("badge_type") == "verified" }
            }.toSet()
        }
    }

    private fun readReel(raw: JSONObject): Reel {
        val id = postObjectId(raw.getString("id")).lowercase()
        val ownerId = postObjectId(raw.getString("user_id")).lowercase()
        if (raw.getString("type") != "video") throw invalidPostResponse()
        val user = raw.getJSONObject("user")
        if (postObjectId(user.getString("id")).lowercase() != ownerId) throw invalidPostResponse()
        val rows = raw.optJSONArray("media")
        if (rows != null && rows.length() > 10) throw invalidPostResponse()
        val videos = if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optString("type") == "video" }.sortedBy { it.optInt("order") }
        val media = videos.firstOrNull()
        val url = (media?.optString("url") ?: raw.optString("media_url")).trim()
        if (safeUrl(url) == null) throw invalidPostResponse()
        val created = Instant.parse(raw.getString("created_at"))
        val reaction = PostReaction.fromWire(raw.optString("current_user_reaction"))
            ?: PostReaction.LOVE.takeIf { raw.optBoolean("is_loved_by_me", false) }
        return Reel(
            id, FeedAuthor(ownerId, user.optString("first_name"), user.optString("last_name"), safeUrl(user.optString("profile_image_url"))),
            url, created, raw.optString("updated_at").takeIf { it.isNotBlank() }?.let(Instant::parse) ?: created,
            raw.optString("caption"), safeUrl(media?.optString("poster_url") ?: raw.optString("poster_url")),
            user.optString("username").trim().trimStart('@'), postCount(raw, "likes_count"), postCount(raw, "comments_count"), reaction
        )
    }

    private inline fun <T> parse(block: () -> T): T = try { block() }
    catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) { throw invalidPostResponse(error) }

    companion object {
        const val PAGE_SIZE = 8
        const val PREVIEW_SIZE = 10
        internal fun safeUrl(value: String?): String? = value?.trim()?.takeIf { text ->
            runCatching { val uri = URI(text); uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null }.getOrDefault(false)
        }
    }
}
