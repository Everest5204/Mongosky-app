package com.mongosky.app.pinpost

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal interface PinPostSource {
    suspend fun read(token: String, owner: String): PinSnapshot
    suspend fun update(token: String, attempt: PinAttempt): PinUpdate
    suspend fun receipt(token: String, attempt: PinAttempt): PinUpdate
}
internal class PinPostApi(private val http: PostTransport = PostHttp()) : PinPostSource {
    private val parser = FeedApi()
    override suspend fun read(token: String, owner: String): PinSnapshot {
        require(PinPolicy.validId(owner))
        return snapshot(http.request(token, "GET", "/api/post-pins/users/$owner"), owner)
    }
    override suspend fun update(token: String, attempt: PinAttempt): PinUpdate = result(
        http.request(token, "PUT", "/api/post-pins/me", body = JSONObject()
            .put("post_id", attempt.target.postId).put("post_source", attempt.target.source)
            .put("pinned", attempt.pinned).put("expected_revision", attempt.expectedRevision).put("request_id", attempt.requestId)), attempt)
    override suspend fun receipt(token: String, attempt: PinAttempt): PinUpdate = result(
        http.request(token, "GET", "/api/post-pins/operations/${attempt.requestId}"), attempt)

    private suspend fun result(raw: JSONObject, attempt: PinAttempt): PinUpdate = parse {
        val row = raw.getJSONObject("receipt")
        val revision = revision(row, "result_revision")
        if (row.getString("owner_id") != attempt.target.ownerId || row.getString("request_id") != attempt.requestId ||
            row.getString("post_id") != attempt.target.postId || row.getString("post_source") != attempt.target.source ||
            row.get("pinned") != attempt.pinned || revision(row, "expected_revision") != attempt.expectedRevision ||
            revision != attempt.expectedRevision + 1) throw invalidPostResponse()
        val current = snapshot(raw.getJSONObject("current"), attempt.target.ownerId)
        if (current.revision < revision) throw invalidPostResponse()
        PinUpdate(attempt, revision, current)
    }
    private suspend fun snapshot(raw: JSONObject, owner: String): PinSnapshot = withContext(Dispatchers.Default) { parse {
        if (raw.getString("owner_id") != owner) throw invalidPostResponse()
        val revision = revision(raw, "revision")
        if (!raw.has("post") || !raw.has("pinned_at")) throw invalidPostResponse()
        if (raw.isNull("post")) {
            if (!raw.isNull("pinned_at") || raw.getString("post_source").isNotEmpty()) throw invalidPostResponse()
            PinSnapshot(owner, revision, null, null)
        } else {
            val source = when (raw.getString("post_source")) { "text" -> FeedSource.TEXT; "media" -> FeedSource.MEDIA; else -> throw invalidPostResponse() }
            val row = raw.getJSONObject("post")
            if (row.getString("user_id") != owner || row.getJSONObject("user").getString("id") != owner ||
                !PinPolicy.validId(row.getString("id")) || row.opt("is_deleted") == true) throw invalidPostResponse()
            val page = parser.parsePage(JSONObject().put("posts", JSONArray().put(row)).put("has_more", false).put("next_cursor", "").toString(), source, null)
            val post = page.posts.singleOrNull() ?: throw invalidPostResponse()
            if (post.source != source || (post.type == FeedPostType.TEXT) != (source == FeedSource.TEXT)) throw invalidPostResponse()
            val at = Instant.parse(raw.getString("pinned_at"))
            if (revision == 0L) throw invalidPostResponse()
            PinSnapshot(owner, revision, post, at)
        }
    } }
    private fun revision(raw: JSONObject, key: String): Long {
        val value = raw.get(key)
        if (value !is Int && value !is Long) throw invalidPostResponse()
        return (value as Number).toLong().also { if (it < 0 || it >= 9_007_199_254_740_990) throw invalidPostResponse() }
    }
    private suspend inline fun <T> parse(crossinline block: suspend () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: FeedApiException) { throw error }
    catch (error: Exception) { throw invalidPostResponse(error) }
}
