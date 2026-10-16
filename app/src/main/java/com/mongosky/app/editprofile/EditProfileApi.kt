package com.mongosky.app.editprofile

import com.mongosky.app.profile.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Same small GET/PATCH contract as the web editor; every response must belong to the active account. */
internal class EditProfileApi(private val http: OwnProfileHttp = OwnProfileHttp()) {
    suspend fun load(token: String, ownerId: String) = parse(http.json(token, "profile/edit"), ownerId)
    suspend fun saveBio(token: String, ownerId: String, value: String): EditableProfile {
        val bio = ProfileBio.normalize(value)
        require(ProfileBio.count(bio) <= ProfileBio.MAX_LENGTH) { "Bio cannot exceed 100 characters." }
        val body = JSONObject().put("bio", bio).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val result = parse(http.json(token, "profile/edit", "PATCH", body), ownerId)
        if (result.bio != bio) throw OwnProfileException("Check your saved bio before trying again.", outcomeUnknown = true)
        return result
    }
    private fun parse(root: JSONObject, ownerId: String): EditableProfile {
        val p = root.optJSONObject("profile") ?: root
        val id = p.profileString("id").ifEmpty { p.profileString("_id") }
        requireProfileOwner(id, ownerId)
        return EditableProfile(requireNotNull(profileObjectId(id)), p.profileString("first_name"),
            p.profileString("last_name"), p.profileString("bio"),
            profileImageUrl(p.profileString("profile_image_url")), profileImageUrl(p.profileString("cover_image_url")))
    }
}
