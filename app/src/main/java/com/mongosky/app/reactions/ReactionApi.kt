package com.mongosky.app.reactions

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.network.postAuthor
import com.mongosky.app.network.postCount
import com.mongosky.app.network.postObjectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface ReactionActions {
    suspend fun summary(token: String, postId: String): ReactionSummary
    suspend fun react(token: String, postId: String, reaction: PostReaction?): ReactionSummary
    suspend fun reactionPeople(token: String, postId: String): List<ReactionPerson>

    suspend fun reactionDetails(token: String, postId: String, filter: PostReaction?): ReactionPeopleResult {
        val all = reactionPeople(token, postId)
        return ReactionPeopleResult(all.filter { filter == null || it.reaction == filter }, all.size.toLong(),
            PostReaction.entries.associateWith { type -> all.count { it.reaction == type }.toLong() })
    }

    suspend fun verifiedPeople(token: String, userIds: List<String>): Set<String> = emptySet()
}

class ReactionApi internal constructor(private val transport: PostTransport) : ReactionActions {
    constructor() : this(PostHttp())

    override suspend fun summary(token: String, postId: String): ReactionSummary =
        parseSummary(transport.request(token, "GET", "/api/reactions/post", mapOf("post_id" to postObjectId(postId))))

    override suspend fun react(token: String, postId: String, reaction: PostReaction?): ReactionSummary {
        val post = postObjectId(postId)
        val result = if (reaction == null) {
            transport.request(token, "DELETE", "/api/reactions", mapOf("post_id" to post))
        } else {
            transport.request(token, "POST", "/api/reactions", body = JSONObject()
                .put("post_id", post).put("reaction_type", reaction.wireName))
        }
        return parseSummary(result.getJSONObject("summary"))
    }

    override suspend fun reactionPeople(token: String, postId: String): List<ReactionPerson> =
        reactionDetails(token, postId, null).people

    override suspend fun reactionDetails(token: String, postId: String, filter: PostReaction?): ReactionPeopleResult {
        val result = transport.request(token, "GET", "/api/reactions/users", mapOf(
            "post_id" to postObjectId(postId), "type" to (filter?.wireName ?: "all"),
            "t" to System.currentTimeMillis().toString()))
        return withContext(Dispatchers.Default) {
            val rows = result.getJSONArray("items")
            val people = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val reaction = PostReaction.fromWire(row.getString("reaction_type")) ?: throw invalidPostResponse()
                if (filter != null && reaction != filter) throw invalidPostResponse()
                ReactionPerson(postObjectId(row.getString("id")), reaction, postAuthor(row.getJSONObject("user")))
            }.distinctBy { it.id }
            val counts = result.optJSONObject("counts")
            val mapped = PostReaction.entries.associateWith { type ->
                counts?.let { postCount(it, type.wireName) } ?: people.count { it.reaction == type }.toLong()
            }
            ReactionPeopleResult(people, if (result.has("total")) postCount(result, "total") else mapped.values.sum(), mapped)
        }
    }

    override suspend fun verifiedPeople(token: String, userIds: List<String>): Set<String> {
        val ids = userIds.map { postObjectId(it).lowercase() }.distinct()
        if (ids.isEmpty()) return emptySet()
        require(ids.size <= 200)
        val result = transport.request(token, "POST", "/api/verified-badges/resolve",
            body = JSONObject().put("user_ids", JSONArray(ids)))
        return withContext(Dispatchers.Default) {
            val badges = result.getJSONArray("badges")
            if (badges.length() > ids.size) throw invalidPostResponse()
            (0 until badges.length()).mapNotNull { index ->
                val row = badges.getJSONObject(index)
                val id = postObjectId(row.getString("user_id")).lowercase()
                if (id !in ids) throw invalidPostResponse()
                id.takeIf { row.optString("badge_type") == "verified" }
            }.toSet()
        }
    }

    private fun parseSummary(raw: JSONObject): ReactionSummary {
        val counts = raw.getJSONObject("counts")
        val mapped = PostReaction.entries.associateWith { postCount(counts, it.wireName) }
        val top = raw.optJSONArray("top_reactions")
        return ReactionSummary(
            total = postCount(raw, "total"), counts = mapped,
            topReactions = if (top != null) (0 until top.length()).mapNotNull { PostReaction.fromWire(top.optString(it)) }.take(2)
                else PostReaction.entries.filter { (mapped[it] ?: 0) > 0 }.sortedByDescending { mapped[it] ?: 0 }.take(2),
            currentReaction = PostReaction.fromWire(raw.optString("current_user_reaction")),
            firstReactor = raw.optJSONObject("first_reactor")?.let(::postAuthor)
        )
    }

}
