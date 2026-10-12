package com.mongosky.app.editmediapost

import com.mongosky.app.editpost.EditPostHttp
import com.mongosky.app.home.HomeHttp
import com.mongosky.app.home.editable
import com.mongosky.app.home.updatedAt
import com.mongosky.app.mediapost.*
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/** Media-only multipart endpoint. Retained files are sent as keys, without reuploading. */
internal class EditMediaPostApi(client: OkHttpClient = HomeHttp.client, baseUrl: String = "https://api.mongosky.com/") {
    private val http = EditPostHttp(client, baseUrl)
    suspend fun editable(token: String, post: HomePost.Media): HomePost.Media = withContext(Dispatchers.IO) {
        require(post.editable)
        http.requireId(post.id)
        decode(http.request(token, "GET", http.url("api", "posts", "media", "edit", post.id)), post)
    }
    suspend fun edit(token: String, request: EditMediaPostRequest): HomePost.Media = withContext(Dispatchers.IO) {
        val draft = request.draft
        require(draft.valid) { "Change the post before saving, and reload its latest version if needed." }
        require(request.newFiles.size == draft.selectedMedia.size && request.newFiles.map { it.id } == draft.selectedMedia.map { it.id })
        require(request.newFiles.all { it.sizeBytes in 1..it.kind.maxFileBytes })
        require(request.newFiles.zip(draft.selectedMedia).all { (file, selected) -> file.kind == selected.kind && file.mimeType == selected.mimeType })
        require(EditMediaPostPolicy.mediaError(draft.keptMedia, draft.selectedMedia) == null)
        http.requireId(draft.post.id)
        val body =
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("caption", draft.text.trim())
                .addFormDataPart("expected_updated_at", draft.post.updatedAt.toString())
                .apply {
                    draft.keptMedia.forEach { addFormDataPart("keep_media_key", it.id?.takeIf(String::isNotBlank) ?: it.url) }
                    request.newFiles.forEach { file ->
                        val source = File(file.localPath)
                        require(source.isFile && source.length() == file.sizeBytes)
                        addFormDataPart("media", file.name.replace('"', '_').replace('\n', '_').replace('\r', '_'),
                            source.asRequestBody(file.mimeType.toMediaType()))
                    }
                }.build()
        decode(http.request(token, "PATCH", http.url("api", "posts", "media", "edit", draft.post.id), body), draft.post)
    }
    private suspend fun decode(raw: JSONObject, original: HomePost.Media): HomePost.Media {
        val post = http.decodeOwned(raw, original)
        val type = when (post.type) {
            FeedPostType.IMAGE -> MediaPostType.IMAGE
            FeedPostType.VIDEO -> MediaPostType.VIDEO
            else -> throw invalidPostResponse()
        }
        if (post.source != FeedSource.MEDIA || EditMediaPostPolicy.mediaError(post.media, emptyList()) != null) throw invalidPostResponse()
        return HomePost.Media(MediaPost(post.id, post.userId, post.author, post.createdAt, post.updatedAt,
            type, post.caption, post.media, post.likesCount, post.commentsCount, post.sharesCount, isSelf = true))
    }
}
