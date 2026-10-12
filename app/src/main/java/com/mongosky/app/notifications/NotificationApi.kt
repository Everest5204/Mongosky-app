package com.mongosky.app.notifications

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal interface NotificationActions {
    suspend fun list(token: String, cursor: String? = null): NotificationPage
    suspend fun markRead(token: String, ids: List<String>): NotificationReadResult
    suspend fun verified(token: String, ids: List<String>): Set<String>
}
internal class NotificationApi(private val http: PostTransport = PostHttp()) : NotificationActions {
    override suspend fun list(token: String, cursor: String?) = withContext(Dispatchers.IO) {
        require(cursor == null || cursor.length in 1..128)
        NotificationJson.page(http.request(token, "GET", "/api/notifications",
            query = mapOf("limit" to "20") + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap())))
    }
    override suspend fun markRead(token: String, ids: List<String>) = withContext(Dispatchers.IO) {
        require(ids.size in 1..100 && ids.all(NotificationPolicy::validId))
        val unique = ids.distinct()
        val raw = http.request(token, "PATCH", "/api/notifications/read", body = JSONObject().put("notification_ids", JSONArray(unique)))
        val modified = NotificationJson.count(raw, "modified_count")
        if (modified > unique.size) throw invalidPostResponse()
        NotificationReadResult(modified, NotificationJson.count(raw, "unread_count"))
    }
    override suspend fun verified(token: String, ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        require(ids.size <= 200 && ids.all(NotificationPolicy::validId))
        if (ids.isEmpty()) return@withContext emptySet()
        val raw = http.request(token, "POST", "/api/verified-badges/resolve", body = JSONObject().put("user_ids", JSONArray(ids.distinct())))
        val rows = raw.optJSONArray("badges") ?: throw invalidPostResponse()
        if (rows.length() > ids.size) throw invalidPostResponse()
        buildSet {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i); val id = row.getString("user_id")
                if (id !in ids) throw invalidPostResponse()
                if (row.optString("badge_type") == "verified") add(id)
            }
        }
    }
}

internal object NotificationJson {
    fun page(raw: JSONObject): NotificationPage = checked {
        val rows = raw.getJSONArray("items")
        require(rows.length() <= 50)
        val items = (0 until rows.length()).map { item(rows.getJSONObject(it)) }
        require(items.map { it.id }.distinct().size == items.size)
        val more = raw.get("has_more") as? Boolean ?: error("has_more")
        val cursor = if (more) raw.getString("next_cursor").also { require(it.length in 1..128 && items.isNotEmpty()) }
            else null.also { require(!raw.has("next_cursor")) }
        NotificationPage(items, cursor, more, count(raw, "unread_count"))
    }
    fun count(raw: JSONObject, key: String): Long = checked {
        val number = raw.get(key) as? Number ?: error("count")
        val value = number.toLong()
        require(value >= 0 && number.toDouble() == value.toDouble() && value <= 9_007_199_254_740_991L)
        value
    }
    private fun id(raw: JSONObject, key: String) = raw.getString(key).also { require(NotificationPolicy.validId(it)) }
    private fun time(raw: JSONObject, key: String) = Instant.parse(raw.getString(key))
    private fun item(raw: JSONObject): NotificationItem {
        val kind = NotificationKind.entries.firstOrNull { it.wire == raw.getString("type") } ?: error("type")
        val actorId = id(raw, "actor_id")
        require(raw.has("actor"))
        val actor = if (raw.isNull("actor")) null else raw.getJSONObject("actor").let {
            require(id(it, "id") == actorId)
            FeedAuthor(actorId, it.getString("first_name"), it.getString("last_name"),
                if (it.has("profile_image_url")) it.getString("profile_image_url").takeIf { url -> url.startsWith("https://") } else null)
        }
        val follow = kind == NotificationKind.FOLLOW || kind == NotificationKind.FOLLOW_BACK
        val comment = kind in setOf(NotificationKind.COMMENT, NotificationKind.REPLY, NotificationKind.LOVE)
        val postId = if (follow) null.also { require(!raw.has("post_id")) } else id(raw, "post_id")
        val commentId = if (comment) id(raw, "comment_id") else null.also { require(!raw.has("comment_id")) }
        val parent = if (raw.has("parent_id")) id(raw, "parent_id").also {
            require(kind == NotificationKind.REPLY || kind == NotificationKind.LOVE); require(it != commentId)
        } else null
        require(kind != NotificationKind.REPLY || parent != null)
        val reaction = if (raw.has("reaction_type")) raw.getString("reaction_type").also {
            require((kind == NotificationKind.REACTION && it.isNotBlank() && it.length <= 64) || (kind == NotificationKind.LOVE && it == "love"))
        } else null
        require(kind !in setOf(NotificationKind.REACTION, NotificationKind.LOVE) || reaction != null)
        return NotificationItem(id(raw, "id"), kind, actorId, actor,
            raw.get("is_read") as? Boolean ?: error("is_read"), time(raw, "created_at"), time(raw, "updated_at"),
            if (raw.has("read_at")) time(raw, "read_at") else null, postId, commentId, parent, reaction)
    }
    private inline fun <T> checked(block: () -> T): T = try { block() } catch (error: Exception) { throw invalidPostResponse(error) }
}
