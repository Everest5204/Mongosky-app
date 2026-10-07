package com.mongosky.app.profile

import com.mongosky.app.profile.OwnProfile
import com.mongosky.app.profile.ProfileBio
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OwnProfileApi(private val http: OwnProfileHttp = OwnProfileHttp()) {
    suspend fun isVerified(token: String, ownerId: String): Boolean {
        require(profileObjectId(ownerId) != null)
        val body = JSONObject().put("user_ids", JSONArray().put(ownerId)).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val badges = http.json(token, "verified-badges/resolve", "POST", body).optJSONArray("badges")
            ?: throw OwnProfileException("Invalid badge response.")
        if (badges.length() > 200) throw OwnProfileException("Invalid badge response.")
        return (0 until badges.length()).any { index -> badges.optJSONObject(index)?.let {
            profileObjectId(it.profileString("user_id")) == ownerId && it.profileString("badge_type") == "verified"
        } == true }
    }
    suspend fun get(token: String, ownerId: String): OwnProfile {
        val root = http.json(token, "user/me")
        val user = root.optJSONObject("user") ?: root
        val id = user.profileString("id").ifEmpty { user.profileString("_id") }
        requireProfileOwner(id, ownerId)
        return OwnProfile(
            id = requireNotNull(profileObjectId(id)), firstName = user.profileString("first_name"),
            lastName = user.profileString("last_name"), bio = user.profileString("bio"),
            profileImageUrl = profileImageUrl(user.profileString("profile_image_url")),
            coverImageUrl = profileImageUrl(user.profileString("cover_image_url")),
            followersCount = user.optLong("followers_count").coerceAtLeast(0),
            followingCount = user.optLong("following_count").coerceAtLeast(0),
            postsCount = user.optLong("posts_count").coerceAtLeast(0)
        )
    }

    suspend fun saveBio(token: String, ownerId: String, value: String): String {
        val bio = ProfileBio.normalize(value)
        require(ProfileBio.count(bio) <= ProfileBio.MAX_LENGTH) { "Bio cannot exceed 100 characters." }
        val body = JSONObject().put("bio", bio).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val root = http.json(token, "profile/edit", "PATCH", body)
        val profile = root.optJSONObject("profile") ?: throw OwnProfileException("Invalid profile response.")
        requireProfileOwner(profile.profileString("id"), ownerId)
        if (profile.profileString("bio") != bio) throw OwnProfileException("Refresh your profile to check the saved bio.")
        return bio
    }
}
