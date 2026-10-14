package com.mongosky.app.downloadpost

import com.mongosky.app.home.photos
import com.mongosky.app.post.*
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal data class DownloadAsset(val url: String, val video: Boolean)
internal data class DownloadText(val text: String, val kind: String, val background: String, val color: String, val style: String)
internal data class DownloadPlan(val postId: String, val source: String, val updatedAt: String,
    val assets: List<DownloadAsset>, val text: DownloadText? = null) {
    val key: String get() = MessageDigest.getInstance("SHA-256").digest(json().toString().toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val count get() = if (text != null) 1 else assets.size
    fun json() = JSONObject().put("post_id", postId).put("source", source).put("updated_at", updatedAt)
        .put("assets", JSONArray().apply { assets.forEach { put(JSONObject().put("url", it.url).put("video", it.video)) } })
        .put("text", text?.let { JSONObject().put("text", it.text).put("kind", it.kind).put("background", it.background)
            .put("color", it.color).put("style", it.style) } ?: JSONObject.NULL)
    companion object {
        fun from(post: HomePost): DownloadPlan = when (post) {
            is HomePost.Text -> DownloadPlan(post.id, "text", post.post.updatedAt.toString(), emptyList(),
                DownloadText(post.post.displayText, post.post.textBackground.kind.name, post.post.textBackground.value,
                    post.post.textColor, post.post.textStyle.name))
            is HomePost.Media -> DownloadPlan(post.id, "media", post.post.updatedAt.toString(), post.photos.distinctBy { it.url }.map {
                require(it.type in setOf(FeedMediaType.IMAGE, FeedMediaType.VIDEO)); DownloadAsset(it.url, it.type == FeedMediaType.VIDEO)
            })
        }.also(DownloadPolicy::validate)
        fun parse(value: String): DownloadPlan {
            require(value.toByteArray().size <= DownloadPolicy.MAX_PLAN_BYTES)
            val raw = JSONObject(value); val assets = raw.getJSONArray("assets"); require(assets.length() <= 10)
            val text = if (raw.isNull("text")) null else raw.getJSONObject("text").let {
                DownloadText(it.getString("text"), it.getString("kind"), it.getString("background"), it.getString("color"), it.getString("style"))
            }
            return DownloadPlan(raw.getString("post_id"), raw.getString("source"), raw.getString("updated_at"),
                (0 until assets.length()).map { assets.getJSONObject(it).let { row ->
                    require(row.get("video") is Boolean); DownloadAsset(row.getString("url"), row.getBoolean("video"))
                } }, text).also(DownloadPolicy::validate)
        }
    }
}
internal object DownloadPolicy {
    const val MAX_PLAN_BYTES = 32 * 1024
    const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 250L * 1024 * 1024
    const val MAX_BATCH_BYTES = 300L * 1024 * 1024
    fun safeUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        value.length in 1..2048 && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null &&
            value.none { it <= ' ' || it == '\u007f' }
    }.getOrDefault(false)
    fun validate(plan: DownloadPlan) {
        require(plan.postId.matches(Regex("[a-f0-9]{24}")) && plan.postId.any { it != '0' }); Instant.parse(plan.updatedAt)
        require(plan.json().toString().toByteArray().size <= MAX_PLAN_BYTES)
        if (plan.source == "text") {
            val text = requireNotNull(plan.text); require(plan.assets.isEmpty())
            require(text.text.isNotBlank() && text.text.codePointCount(0, text.text.length) <= 650)
            require(text.kind in FeedTextBackgroundKind.entries.map { it.name } && text.style in FeedTextStyle.entries.map { it.name })
            require(text.background.length <= 2048 && text.color.length <= 64)
            if (text.kind == "IMAGE") require(safeUrl(text.background))
        } else {
            require(plan.source == "media" && plan.text == null && plan.assets.size in 1..10)
            require(plan.assets.all { safeUrl(it.url) } && plan.assets.map { it.url }.distinct().size == plan.assets.size)
        }
    }
}
internal enum class DownloadPhase { QUEUED, SAVING, PUBLISHING, SUCCEEDED, FAILED, CANCELLED }
internal data class SavedMedia(val uri: String, val name: String, val mime: String, val size: Long)
internal data class DownloadProgress(val key: String, val plan: DownloadPlan, val phase: DownloadPhase,
    val completed: Int = 0, val fraction: Float? = null, val message: String? = null, val saved: List<SavedMedia> = emptyList()) {
    val active get() = phase in setOf(DownloadPhase.QUEUED, DownloadPhase.SAVING, DownloadPhase.PUBLISHING)
}
internal class DownloadException(message: String) : java.io.IOException(message)
internal fun foregroundDownload(rows: List<DownloadProgress>): DownloadProgress? =
    rows.firstOrNull { it.active && it.phase != DownloadPhase.QUEUED } ?: rows.firstOrNull { it.active }
