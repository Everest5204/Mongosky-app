package com.mongosky.app.profile

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedApi
import com.mongosky.app.post.FeedPage
import com.mongosky.app.post.FeedPostType
import com.mongosky.app.post.FeedSource
import com.mongosky.app.reels.ReelsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface UserProfileDataSource {
    suspend fun profile(token: String, viewerId: String, userId: String): UserProfile
    suspend fun posts(token: String, viewerId: String, userId: String, source: FeedSource, cursor: String?): FeedPage
    suspend fun verified(token: String, userId: String): Boolean
    suspend fun follow(token: String, userId: String, following: Boolean): UserProfileFollow
    suspend fun connections(token: String, viewerId: String, userId: String, kind: ProfileConnectionKind, cursor: String?): ProfileConnectionPage
}

/** Matches the web's public profile endpoints; no own-profile or editing endpoints. */
class UserProfileApi internal constructor(private val http: PostTransport) : UserProfileDataSource {
    constructor() : this(PostHttp())
    private val parser = FeedApi()
    private val badges = ReelsApi(http)

    override suspend fun profile(token: String, viewerId: String, userId: String): UserProfile {
        val id = safeId(userId)
        val raw = http.request(token, "GET", "/api/users/$id/profile", freshQuery())
        return parse {
            val row = raw.getJSONObject("profile")
            if (safeId(row.getString("id")) != id || row.getBoolean("is_self") != (id == safeId(viewerId))) throw invalidPostResponse()
            UserProfile(id, row.optString("first_name"), row.optString("last_name"), row.optString("profile_title"),
                row.optString("bio"), ReelsApi.safeUrl(row.optString("profile_image_url")), ReelsApi.safeUrl(row.optString("cover_image_url")),
                count(row, "followers_count"), count(row, "following_count"), count(row, "posts_count"),
                row.getBoolean("is_self"), row.getBoolean("is_following"), row.getBoolean("can_message"))
        }
    }

    override suspend fun posts(token: String, viewerId: String, userId: String, source: FeedSource, cursor: String?): FeedPage =
        withContext(Dispatchers.Default) {
            val id = safeId(userId)
            val path = if (source == FeedSource.MEDIA) "/api/posts/user" else "/api/text-posts/user"
            val raw = http.request(token, "GET", path, pageQuery(cursor) + ("user_id" to id))
            // Validate raw identities even if the generic feed parser skips an unsupported row.
            val rows = raw.getJSONArray("posts")
            if (rows.length() > PAGE_SIZE) throw invalidPostResponse()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (safeId(row.getString("user_id")) != id ||
                    row.optJSONObject("user")?.let { safeId(it.getString("id")) != id } == true) throw invalidPostResponse()
            }
            val page = parser.parsePage(raw.toString(), source, cursor)
            if (page.posts.any { it.userId != id || it.author.id != id ||
                    source == FeedSource.TEXT && it.type != FeedPostType.TEXT }) throw invalidPostResponse()
            // /posts/user is mixed. Preserve its cursor through text-only pages.
            page.copy(posts = page.posts.filter { source == FeedSource.TEXT || it.type != FeedPostType.TEXT }
                .map { it.copy(isSelf = id == safeId(viewerId)) })
        }

    override suspend fun verified(token: String, userId: String) = safeId(userId).let { it in badges.verified(token, listOf(it)) }

    override suspend fun follow(token: String, userId: String, following: Boolean): UserProfileFollow {
        val raw = http.request(token, if (following) "PUT" else "DELETE", "/api/users/${safeId(userId)}/follow",
            freshQuery(), if (following) JSONObject() else null)
        return parse { UserProfileFollow(raw.getBoolean("is_following"), count(raw, "followers_count")) }
    }

    override suspend fun connections(token: String, viewerId: String, userId: String,
        kind: ProfileConnectionKind, cursor: String?): ProfileConnectionPage {
        val id = safeId(userId); val viewer = safeId(viewerId)
        val raw = http.request(token, "GET", "/api/profile-connections/$id/${kind.path}", pageQuery(cursor))
        return parse {
            val owner = raw.getJSONObject("owner")
            if (safeId(owner.getString("id")) != id) throw invalidPostResponse()
            val rows = raw.getJSONArray("items")
            if (rows.length() > PAGE_SIZE) throw invalidPostResponse()
            val more = raw.getBoolean("has_more")
            val next = raw.optString("next_cursor").trim().takeIf { it.isNotEmpty() && it != "null" }
            if (next != null && next.length > 2048 || more && (next == null || next == cursor)) throw invalidPostResponse()
            val items = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index); val personId = safeId(row.getString("id"))
                if (row.getBoolean("is_self") != (personId == viewer)) throw invalidPostResponse()
                ProfileConnection(personId, row.optString("first_name"), row.optString("last_name"),
                    ReelsApi.safeUrl(row.optString("profile_image_url")), row.getBoolean("is_following"),
                    row.getBoolean("follows_you"), personId == viewer)
            }.distinctBy { it.id }
            ProfileConnectionPage(items, if (more) next else null, more)
        }
    }

    private fun freshQuery() = mapOf("t" to System.currentTimeMillis().toString())
    private fun pageQuery(cursor: String?): Map<String, String> = freshQuery() + buildMap {
        put("limit", PAGE_SIZE.toString())
        cursor?.let { require(it.isNotBlank() && it.length <= 2048); put("cursor", it) }
    }
    private fun safeId(id: String) = requireNotNull(profileUserId(id)) { "Invalid user ID." }
    private fun count(raw: JSONObject, key: String): Long = raw.getLong(key).also { if (it < 0) throw invalidPostResponse() }
    private inline fun <T> parse(block: () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { throw invalidPostResponse(error) }
    companion object { const val PAGE_SIZE = 20 }
}
