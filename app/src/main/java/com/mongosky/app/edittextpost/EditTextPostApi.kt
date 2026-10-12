package com.mongosky.app.edittextpost

import com.mongosky.app.editpost.EditPostHttp
import com.mongosky.app.home.HomeHttp
import com.mongosky.app.home.editable
import com.mongosky.app.home.updatedAt
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Text-only JSON endpoint and response mapping. */
internal class EditTextPostApi(client: OkHttpClient = HomeHttp.client, baseUrl: String = "https://api.mongosky.com/") {
    private val http = EditPostHttp(client, baseUrl)
    suspend fun editable(token: String, post: HomePost.Text): HomePost.Text = withContext(Dispatchers.IO) {
        require(post.editable)
        http.requireId(post.id)
        decode(http.request(token, "GET", http.url("api", "text-posts", "edit", post.id)), post)
    }
    suspend fun edit(token: String, request: EditTextPostRequest): HomePost.Text = withContext(Dispatchers.IO) {
        val draft = request.draft
        require(draft.valid) { "Change the post before saving, and reload its latest version if needed." }
        require(draft.text.isNotBlank())
        http.requireId(draft.post.id)
        val body = http.jsonBody(JSONObject().put("text", draft.text.trim()).put("caption", draft.text.trim())
            .put("text_background", JSONObject().put("kind", draft.background.kind.name.lowercase()).put("value", draft.background.value))
            .put("text_color", draft.textColor).put("text_style", draft.textStyle.name.lowercase())
            .put("expected_updated_at", draft.post.updatedAt.toString()))
        decode(http.request(token, "PATCH", http.url("api", "text-posts", "edit", draft.post.id), body), draft.post)
    }
    private suspend fun decode(raw: JSONObject, original: HomePost.Text): HomePost.Text {
        val post = http.decodeOwned(raw, original)
        if (post.source != FeedSource.TEXT || post.type != FeedPostType.TEXT) throw invalidPostResponse()
        return HomePost.Text(TextPost(post.id, post.userId, post.author, post.createdAt, post.updatedAt,
            post.text, post.caption, post.textBackground, post.textColor, post.textStyle,
            post.likesCount, post.commentsCount, post.sharesCount, isSelf = true))
    }
}
