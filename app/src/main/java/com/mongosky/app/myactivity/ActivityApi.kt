package com.mongosky.app.myactivity

import com.mongosky.app.comments.CommentApi
import com.mongosky.app.home.HomeFeedApi
import com.mongosky.app.home.toHomePost
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.network.postAuthor
import com.mongosky.app.post.*
import com.mongosky.app.reactions.ReactionApi
import com.mongosky.app.reels.Reel
import java.time.Instant
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal interface ActivityRepository {
    suspend fun page(token: String, owner: String, filter: ActivityFilter, cursor: String?): ActivityPage
    suspend fun target(token: String, owner: String, item: ActivityItem): ActivityTarget
    suspend fun remove(token: String, item: ActivityItem)
    fun changes(token: String, owner: String): Flow<Unit>
}
internal class ActivityApi(private val transport: PostTransport = ActivityHttp(), private val live: ActivityHttp = ActivityHttp()) : ActivityRepository {
    override suspend fun page(token: String, owner: String, filter: ActivityFilter, cursor: String?): ActivityPage {
        val query = buildMap {
            put("category", filter.category.wire); put("limit", "20")
            filter.from?.let { put("from", it.toString()) }; filter.before?.let { put("before", it.toString()) }
            cursor?.let { put("cursor", it) }
        }
        val raw = transport.request(token, "GET", "/api/my-activity", query)
        return withContext(Dispatchers.Default) { parse { decodePage(raw, owner, filter, cursor) } }
    }
    internal fun decodePage(raw: JSONObject, owner: String, filter: ActivityFilter, cursor: String?): ActivityPage {
        require(ActivityPolicy.validId(owner) && raw.getString("owner_id") == owner)
        val rows = raw.getJSONArray("items"); require(rows.length() <= 20)
        val items = (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i); val kind = r.getString("kind")
            val category = ActivityPolicy.kinds[kind] ?: throw invalidPostResponse()
            require(r.getString("category") == category.wire && (filter.category == ActivityCategory.ALL || filter.category == category))
            val id = r.getString("id"); require(ActivityPolicy.validId(id))
            val at = Instant.parse(r.getString("created_at")); require(filter.from == null || at >= filter.from)
            require(filter.before == null || at < filter.before)
            val available = r.getBoolean("available")
            val action = ActivityAction.entries.firstOrNull { it.wire == r.getString("action") } ?: throw invalidPostResponse()
            require(action == ActivityAction.NONE || action == ActivityPolicy.allowedAction(kind))
            require(available || action == ActivityAction.NONE || kind == "comment")
            val postId = r.optString("post_id").takeIf { it.isNotBlank() }; require(postId == null || ActivityPolicy.validId(postId))
            val author = r.optJSONObject("author")?.let { postAuthor(it) }
            require(!available || if (kind == "follow") author != null else postId != null && author != null)
            ActivityItem(id, kind, category, at, r.getString("title").take(140), r.getString("text").take(240), author,
                secureUrl(r.optString("thumbnail")), postId, available, action)
        }
        require(items.map { it.key }.distinct().size == items.size)
        require(items.zipWithNext().all { (a, b) -> ActivityPolicy.ordered(a, b) })
        val counts = raw.optJSONObject("counts")
        require(cursor != null || counts != null)
        val mapped = counts?.let { values -> ActivityCategory.entries.filter { it != ActivityCategory.ALL }.associateWith { category ->
            values.getLong(category.wire).also { require(it >= 0) }
        } }
        val next = raw.getString("next_cursor").takeIf { it.isNotEmpty() }; val more = raw.getBoolean("has_more")
        require(next == null || next.length <= 2048); require(if (more) next != null && next != cursor && items.isNotEmpty() else next == null)
        return ActivityPage(items, mapped, next, more)
    }
    override suspend fun target(token: String, owner: String, item: ActivityItem): ActivityTarget {
        val raw = transport.request(token, "GET", "/api/my-activity/target", mapOf("kind" to item.kind, "id" to item.id))
        return withContext(Dispatchers.Default) {
            try {
                require(raw.getString("owner_id") == owner && raw.getString("kind") == item.kind && raw.getString("id") == item.id)
                if (item.kind == "follow") return@withContext ActivityTarget(item, profile = postAuthor(raw.getJSONObject("profile")))
                val p = raw.getJSONObject("post"); require(p.getString("id") == item.postId)
                val source = if (p.getString("type") == "text") FeedSource.TEXT else FeedSource.MEDIA
                val page = JSONObject().put("posts", JSONArray().put(p)).put("has_more", false).put("next_cursor", "")
                val post = FeedApi().parsePage(page.toString(), source, null).posts.single().also { require(it.isSelf == (it.userId == owner)) }
                if (post.type == FeedPostType.VIDEO) {
                    val video = post.media.firstOrNull { it.type == FeedMediaType.VIDEO } ?: throw invalidPostResponse()
                    require(secureUrl(video.url) != null)
                    ActivityTarget(item, reel = Reel(post.id, post.author, video.url, post.createdAt, post.updatedAt,
                        post.caption, likesCount = post.likesCount, commentsCount = post.commentsCount))
                } else ActivityTarget(item, post = post.toHomePost())
            } catch (e: CancellationException) { throw e } catch (e: FeedApiException) { throw e } catch (e: Exception) { throw invalidPostResponse(e) }
        }
    }
    override suspend fun remove(token: String, item: ActivityItem) {
        require(ActivityPolicy.allowedAction(item.kind) == item.action && item.action != ActivityAction.NONE)
        when (item.action) {
            ActivityAction.DELETE_POST -> HomeFeedApi().delete(token, item.id)
            ActivityAction.DELETE_COMMENT -> { val result = CommentApi().deleteComment(token, item.id); require(result.deleted) }
            ActivityAction.REMOVE_REACTION -> { val result = ReactionApi().react(token, requireNotNull(item.postId), null); require(result.currentReaction == null) }
            else -> throw invalidPostResponse()
        }
    }
    override fun changes(token: String, owner: String) = live.changes(token, owner)
    private fun secureUrl(value: String): String? = runCatching { URI(value).takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }?.toString() }.getOrNull()
    private inline fun <T> parse(block: () -> T): T = try { block() } catch (e: FeedApiException) { throw e } catch (e: Exception) { throw invalidPostResponse(e) }
}
