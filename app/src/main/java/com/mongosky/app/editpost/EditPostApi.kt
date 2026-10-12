package com.mongosky.app.editpost

import com.mongosky.app.edittextpost.*
import com.mongosky.app.editmediapost.*
import com.mongosky.app.home.HomeHttp
import com.mongosky.app.home.HomeEditRequest
import com.mongosky.app.post.HomePost
import okhttp3.OkHttpClient

/** Existing Home API compatibility router; each editor owns its endpoint and request logic. */
internal class EditPostApi(client: OkHttpClient = HomeHttp.client, baseUrl: String = "https://api.mongosky.com/") {
    private val text = EditTextPostApi(client, baseUrl)
    private val media = EditMediaPostApi(client, baseUrl)
    suspend fun editable(token: String, post: HomePost): HomePost = when (post) {
        is HomePost.Text -> text.editable(token, post)
        is HomePost.Media -> media.editable(token, post)
    }
    suspend fun edit(token: String, request: HomeEditRequest): HomePost = when (val draft = request.draft) {
        is EditTextPostState -> { require(request.newFiles.isEmpty()); text.edit(token, EditTextPostRequest(draft)) }
        is EditMediaPostState -> media.edit(token, EditMediaPostRequest(draft, request.newFiles))
        else -> error("Unknown edit section")
    }
}
