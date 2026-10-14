package com.mongosky.app.saveditems

import com.mongosky.app.home.toHomePost
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.network.postAuthor
import com.mongosky.app.post.*
import com.mongosky.app.reels.Reel
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal interface SavedRepository {
    suspend fun state(token: String,owner: String,postId: String): SavedStatus
    suspend fun update(token: String,owner: String,attempt: SaveAttempt): SavedStatus
    suspend fun page(token: String,owner: String,filter: SavedFilter,cursor: String?): SavedPage
    suspend fun resolve(token: String,owner: String,postIds: Set<String>): List<SavedItem>
    suspend fun post(token: String,owner: String,postId: String): SavedTarget
    fun changes(token: String,owner: String): Flow<Long>
}
internal class SavedApi(private val transport: PostTransport = SavedHttp(),private val live: SavedHttp = SavedHttp()) : SavedRepository {
    override suspend fun state(token: String,owner: String,postId: String): SavedStatus {
        require(SavedPolicy.validId(postId))
        val raw = transport.request(token,"GET","/api/saved-items/status",mapOf("post_id" to postId))
        return withContext(Dispatchers.Default) { parse { decodeState(raw,owner,postId) } }
    }
    override suspend fun update(token: String,owner: String,attempt: SaveAttempt): SavedStatus {
        require(SavedPolicy.validId(attempt.postId) && attempt.expectedRevision >= 0)
        val raw = transport.request(token,"PUT","/api/saved-items/${attempt.postId}",body = JSONObject().put("saved",attempt.saved)
            .put("expected_revision",attempt.expectedRevision).put("request_id",attempt.requestId))
        return withContext(Dispatchers.Default) { parse {
            decodeState(raw,owner,attempt.postId).also { require(it.saved == attempt.saved && it.revision in attempt.expectedRevision..(attempt.expectedRevision + 1)) }
        } }
    }
    internal fun decodeState(raw: JSONObject,owner: String,postId: String): SavedStatus {
        require(SavedPolicy.validId(owner) && raw.getString("owner_id") == owner && raw.getString("post_id") == postId)
        val saved = raw.getBoolean("saved");val revision = raw.getLong("revision");require(revision >= 0)
        val at = if (raw.isNull("saved_at")) null else Instant.parse(raw.getString("saved_at"))
        require(if (saved) revision > 0 && at != null else at == null)
        return SavedStatus(postId,saved,revision,at)
    }
    override suspend fun page(token: String,owner: String,filter: SavedFilter,cursor: String?): SavedPage {
        val query = buildMap { put("kind",filter.kind.wire);put("q",filter.search);put("limit","20");cursor?.let { put("cursor",it) } }
        val raw = transport.request(token,"GET","/api/saved-items",query)
        return withContext(Dispatchers.Default) { parse { decodePage(raw,owner,filter,cursor) } }
    }
    internal fun decodePage(raw: JSONObject,owner: String,filter: SavedFilter,cursor: String?): SavedPage {
        require(SavedPolicy.validId(owner) && raw.getString("owner_id") == owner)
        val rows = raw.getJSONArray("items");require(rows.length() <= 20)
        val items = (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i);val id = r.getString("post_id");val entry = r.getString("id");require(SavedPolicy.validId(id) && SavedPolicy.validId(entry))
            val kind = SavedKind.entries.firstOrNull { it != SavedKind.ALL && it.wire == r.getString("kind") } ?: throw invalidPostResponse()
            require(filter.kind == SavedKind.ALL || kind == filter.kind)
            val rev = r.getLong("revision");require(rev > 0)
            val available = r.getBoolean("available");val author = r.optJSONObject("author")?.let { postAuthor(it) }
            require(if (available) author != null else author == null && r.optString("thumbnail").isEmpty())
            require(filter.search.isEmpty() || available)
            SavedItem(id,kind,rev,Instant.parse(r.getString("saved_at")),available,r.getString("title").take(100),r.getString("text").take(500),author,secureUrl(r.optString("thumbnail")),entry)
        }
        require(items.map { it.postId }.distinct().size == items.size && items.zipWithNext().all { (a,b) -> SavedPolicy.ordered(a,b) })
        val next = raw.getString("next_cursor").takeIf { it.isNotEmpty() };val more = raw.getBoolean("has_more")
        require(next == null || next.length <= 2048);require(if (more) next != null && next != cursor && items.isNotEmpty() else next == null)
        val change = raw.getLong("change_revision");require(change >= 0)
        return SavedPage(items,next,more,change)
    }
    override suspend fun resolve(token: String,owner: String,postIds: Set<String>): List<SavedItem> {
        require(postIds.isNotEmpty() && postIds.size <= 20 && postIds.all(SavedPolicy::validId))
        val raw = transport.request(token,"POST","/api/saved-items/resolve",body = JSONObject().put("post_ids",JSONArray(postIds.toList())))
        return withContext(Dispatchers.Default) { parse {
            raw.put("has_more",false).put("next_cursor","").put("change_revision",0)
            decodePage(raw,owner,SavedFilter(),null).items.also { rows -> require(rows.all { it.postId in postIds }) }
        } }
    }
    override suspend fun post(token: String,owner: String,postId: String): SavedTarget {
        require(SavedPolicy.validId(postId));val raw = transport.request(token,"GET","/api/saved-items/post",mapOf("post_id" to postId))
        return withContext(Dispatchers.Default) { try {
            require(raw.getString("owner_id") == owner)
            val p = raw.getJSONObject("post");require(p.getString("id") == postId)
            val source = if (p.getString("type") == "text") FeedSource.TEXT else FeedSource.MEDIA
            val page = JSONObject().put("posts",JSONArray().put(p)).put("has_more",false).put("next_cursor","")
            val post = FeedApi().parsePage(page.toString(),source,null).posts.single().also { require(it.isSelf == (it.userId == owner)) }
            if (post.type == FeedPostType.VIDEO) {
                val video = post.media.firstOrNull { it.type == FeedMediaType.VIDEO } ?: throw invalidPostResponse();require(secureUrl(video.url) != null)
                SavedTarget(postId,reel = Reel(post.id,post.author,video.url,post.createdAt,post.updatedAt,post.caption,
                    likesCount = post.likesCount,commentsCount = post.commentsCount))
            } else SavedTarget(postId,post = post.toHomePost())
        } catch (e: CancellationException) { throw e } catch (e: FeedApiException) { throw e } catch (e: Exception) { throw invalidPostResponse(e) } }
    }
    override fun changes(token: String,owner: String) = live.changes(token,owner)
    private fun secureUrl(value: String): String? = runCatching { URI(value).takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }?.toString() }.getOrNull()
    private inline fun <T> parse(block: () -> T): T = try { block() } catch (e: FeedApiException) { throw e } catch (e: Exception) { throw invalidPostResponse(e) }
}
