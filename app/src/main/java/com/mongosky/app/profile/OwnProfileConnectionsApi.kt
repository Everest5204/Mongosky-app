package com.mongosky.app.profile

import com.mongosky.app.profile.ProfileConnection
import com.mongosky.app.profile.ProfileConnectionKind
import com.mongosky.app.profile.ProfileConnectionPage
import okhttp3.RequestBody.Companion.toRequestBody

class OwnProfileConnectionsApi(private val http: OwnProfileHttp = OwnProfileHttp()) {
    suspend fun load(token: String, ownerId: String, kind: ProfileConnectionKind, cursor: String?): ProfileConnectionPage {
        require(cursor == null || cursor.length <= 2048)
        val query = buildMap { put("limit", "20"); if (!cursor.isNullOrBlank()) put("cursor", cursor) }
        val root = http.json(token, "profile-connections/me/${kind.path}", query = query)
        val owner = root.optJSONObject("owner") ?: throw OwnProfileException("Invalid connection list.")
        requireProfileOwner(owner.profileString("id"), ownerId)
        val rows = root.optJSONArray("items") ?: throw OwnProfileException("Invalid connection list.")
        val hasMore = root.opt("has_more") as? Boolean ?: throw OwnProfileException("Invalid connection cursor.")
        val next = root.profileString("next_cursor").trim().takeIf { it.isNotEmpty() }
        if (rows.length() > 50 || (next != null && next.length > 2048) ||
            (hasMore && (next == null || next == cursor))) throw OwnProfileException("Invalid connection cursor.")
        val items = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: throw OwnProfileException("Invalid connection user.")
            val id = profileObjectId(row.profileString("id")) ?: throw OwnProfileException("Invalid connection user.")
            ProfileConnection(id, row.profileString("first_name"), row.profileString("last_name"),
                profileImageUrl(row.profileString("profile_image_url")), row.optBoolean("is_following"),
                row.optBoolean("follows_you"), id == ownerId)
        }.distinctBy { it.id }
        return ProfileConnectionPage(items, if (hasMore) next else null, hasMore)
    }

    suspend fun setFollowing(token: String, personId: String, following: Boolean): Boolean {
        val id = profileObjectId(personId) ?: throw OwnProfileException("Invalid profile.")
        val root = http.json(token, "users/$id/follow", if (following) "PUT" else "DELETE",
            if (following) ByteArray(0).toRequestBody() else null)
        val result = root.opt("is_following") as? Boolean ?: throw OwnProfileException("Refresh the list to check this change.")
        if (result != following) throw OwnProfileException("Refresh the list to check this change.")
        return result
    }
}
