package com.mongosky.app.profile

import com.mongosky.app.profile.ProfileImageKind
import com.mongosky.app.profile.ProfileMediaChange
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class OwnProfileMediaApi(private val http: OwnProfileHttp = OwnProfileHttp()) {
    suspend fun upload(token: String, ownerId: String, kind: ProfileImageKind, file: File): ProfileMediaChange {
        require(file.isFile && file.length() in 1..8_388_608)
        val image = object : RequestBody() {
            override fun contentType() = "image/jpeg".toMediaType()
            override fun contentLength() = file.length()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                file.source().use { source ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("Upload cancelled.")
                        val count = source.read(sink.buffer, 8192)
                        if (count < 0) break
                        sink.emitCompleteSegments()
                    }
                }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(kind.field, "profile-image.jpg", image).build()
        val root = http.json(token, "profile-media/${kind.endpoint}", "POST", body)
        val user = root.optJSONObject("user") ?: root
        requireProfileOwner(user.profileString("id"), ownerId)
        val field = if (kind == ProfileImageKind.AVATAR) "profile_image_url" else "cover_image_url"
        val url = profileImageUrl(user.profileString(field)) ?: throw OwnProfileException(
            "Upload may have finished. Refresh your profile to check it.", outcomeUnknown = true)
        return ProfileMediaChange(ownerId, kind, url)
    }
}
