package com.mongosky.app.comments

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun interface CommentBadges {
    suspend fun verified(token: String, userIds: List<String>): Set<String>
}

/** Resolve a visible batch once, instead of requesting a badge from every row. */
internal class CommentBadgeApi(private val http: PostTransport = PostHttp()) : CommentBadges {
    override suspend fun verified(token: String, userIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val ids = userIds.distinct().map { it.lowercase() }
        require(ids.size <= 200 && ids.all(CommentPolicy::validId))
        if (ids.isEmpty()) return@withContext emptySet()
        val raw = http.request(token, "POST", "/api/verified-badges/resolve", body = JSONObject().put("user_ids", JSONArray(ids)))
        val rows = raw.optJSONArray("badges") ?: throw invalidPostResponse()
        if (rows.length() > 200) throw invalidPostResponse()
        buildSet {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val id = row.getString("user_id").lowercase()
                if (id !in ids) throw invalidPostResponse()
                if (row.optString("badge_type") == "verified") add(id)
            }
        }
    }
}
