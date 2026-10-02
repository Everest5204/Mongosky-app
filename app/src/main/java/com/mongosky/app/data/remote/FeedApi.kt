package com.mongosky.app.data.remote

import com.mongosky.app.domain.model.FeedAuthor
import com.mongosky.app.domain.model.FeedMedia
import com.mongosky.app.domain.model.FeedMediaType
import com.mongosky.app.domain.model.FeedPage
import com.mongosky.app.domain.model.FeedPost
import com.mongosky.app.domain.model.FeedPostType
import com.mongosky.app.domain.model.FeedSource
import com.mongosky.app.domain.model.FeedTextBackground
import com.mongosky.app.domain.model.FeedTextBackgroundKind
import com.mongosky.app.domain.model.FeedTextStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

enum class FeedApiFailure {
    SESSION_EXPIRED,
    ACCESS_DENIED,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    SERVER,
    INVALID_RESPONSE,
    REQUEST_REJECTED
}

class FeedApiException(
    message: String,
    val failure: FeedApiFailure,
    val statusCode: Int? = null,
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class FeedApi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * The caller reads the saved token once and supplies it to both sources.
     * Each source must retain its own cursor. Tokens are never logged or cleared here.
     */
    suspend fun loadPage(
        token: String,
        source: FeedSource,
        cursor: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): FeedPage = withContext(ioDispatcher) {
        ensureActive()

        val cleanToken = token.trim()
        if (cleanToken.isEmpty() || cleanToken.any { it <= ' ' || it >= '\u007f' }) {
            throw FeedApiException(
                message = "Please sign in to load your feed.",
                failure = FeedApiFailure.SESSION_EXPIRED
            )
        }

        require(pageSize in 1..MAX_PAGE_SIZE) {
            "Feed page size must be between 1 and $MAX_PAGE_SIZE."
        }

        val cleanCursor = cursor?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanCursor == null || cleanCursor.length <= MAX_CURSOR_LENGTH) {
            "Feed cursor is too long."
        }

        val path = when (source) {
            FeedSource.MEDIA -> "/api/posts/feed"
            FeedSource.TEXT -> "/api/text-posts/feed"
        }
        val cursorQuery = cleanCursor?.let {
            "&cursor=" + URLEncoder.encode(it, "UTF-8")
        }.orEmpty()

        var connection: HttpsURLConnection? = null
        try {
            val request = URL("$BASE_URL$path?limit=$pageSize$cursorQuery")
                .openConnection() as HttpsURLConnection
            connection = request
            request.requestMethod = "GET"
            request.connectTimeout = 15_000
            request.readTimeout = 30_000
            request.useCaches = false
            request.instanceFollowRedirects = false
            request.setRequestProperty("Accept", "application/json")
            request.setRequestProperty("Authorization", "Bearer $cleanToken")
            request.setRequestProperty("Cache-Control", "no-store")

            ensureActive()
            val status = request.responseCode
            ensureActive()

            if (status != 200) {
                val retryAfter = request.getHeaderField("Retry-After")
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.coerceAtMost(3_600)
                throw httpError(status, retryAfter)
            }

            val body = readBody(request.inputStream)
            ensureActive()
            parsePage(body, source, cleanCursor)
        } catch (error: FeedApiException) {
            ensureActive()
            throw error
        } catch (error: SocketTimeoutException) {
            ensureActive()
            throw FeedApiException(
                message = "The feed request timed out. Please try again.",
                failure = FeedApiFailure.TIMEOUT,
                cause = error
            )
        } catch (error: IOException) {
            ensureActive()
            throw FeedApiException(
                message = "Could not connect. Check your connection and try again.",
                failure = FeedApiFailure.NETWORK,
                cause = error
            )
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun readBody(stream: InputStream): String = stream.use { input ->
        val output = ByteArrayOutputStream(8_192)
        val buffer = ByteArray(8_192)

        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_RESPONSE_BYTES) {
                throw invalidResponse()
            }
            output.write(buffer, 0, count)
        }

        currentCoroutineContext().ensureActive()
        output.toString("UTF-8")
    }

    private suspend fun parsePage(
        body: String,
        source: FeedSource,
        requestedCursor: String?
    ): FeedPage {
        val root = try {
            JSONObject(body)
        } catch (error: JSONException) {
            throw invalidResponse(error)
        }

        val rows = root.optJSONArray("posts") ?: throw invalidResponse()
        val hasMore = root.opt("has_more") as? Boolean ?: throw invalidResponse()
        if (rows.length() > MAX_PAGE_SIZE) throw invalidResponse()

        val rawCursor = root.opt("next_cursor")
        if (rawCursor != null && rawCursor != JSONObject.NULL && rawCursor !is String) {
            throw invalidResponse()
        }
        val nextCursor = (rawCursor as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (nextCursor != null && nextCursor.length > MAX_CURSOR_LENGTH) {
            throw invalidResponse()
        }
        if (hasMore && (nextCursor == null || nextCursor == requestedCursor)) {
            throw invalidResponse()
        }

        val posts = ArrayList<FeedPost>(rows.length())
        val seenIds = HashSet<String>(rows.length())
        for (index in 0 until rows.length()) {
            currentCoroutineContext().ensureActive()
            val row = rows.optJSONObject(index) ?: throw invalidResponse()
            val post = parsePost(row, source) ?: continue
            if (seenIds.add(post.id)) posts.add(post)
        }

        return FeedPage(
            posts = posts.toList(),
            nextCursor = if (hasMore) nextCursor else null,
            hasMore = hasMore
        )
    }

    private fun parsePost(raw: JSONObject, source: FeedSource): FeedPost? {
        if (raw.optBoolean("is_deleted", false)) return null

        val id = objectId(raw.opt("id")) ?: objectId(raw.opt("_id"))
            ?: throw invalidResponse()
        val userId = objectId(raw.opt("user_id")) ?: throw invalidResponse()
        val user = raw.optJSONObject("user")
        val createdAt = instant(raw.string("created_at")) ?: throw invalidResponse()
        val updatedAt = instant(raw.string("updated_at")) ?: createdAt
        val isSelf = raw.optBoolean("is_self", false)

        return FeedPost(
            id = id,
            userId = userId,
            author = FeedAuthor(
                id = objectId(user?.opt("id")) ?: userId,
                firstName = user?.string("first_name").orEmpty(),
                lastName = user?.string("last_name").orEmpty(),
                profileImageUrl = mediaUrl(user?.string("profile_image_url").orEmpty())
            ),
            source = source,
            type = postType(raw.string("type"), source),
            createdAt = createdAt,
            updatedAt = updatedAt,
            caption = raw.string("caption"),
            text = raw.string("text"),
            media = parseMedia(raw),
            textBackground = parseTextBackground(raw.opt("text_background")),
            textColor = raw.string("text_color").trim().ifEmpty { "#111827" },
            textStyle = textStyle(raw.string("text_style")),
            likesCount = raw.optLong("likes_count", 0L).coerceAtLeast(0L),
            commentsCount = raw.optLong("comments_count", 0L).coerceAtLeast(0L),
            sharesCount = raw.optLong("shares_count", 0L).coerceAtLeast(0L),
            isSelf = isSelf,
            isFollowing = !isSelf && raw.optBoolean("is_following", false)
        )
    }

    private fun parseMedia(raw: JSONObject): List<FeedMedia> {
        val rows: JSONArray? = raw.optJSONArray("media")
        val items = ArrayList<FeedMedia>()
        if (rows != null) {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val url = mediaUrl(item.string("url")) ?: continue
                items.add(
                    FeedMedia(
                        url = url,
                        type = mediaType(item.string("type")),
                        id = item.string("id").trim().takeIf { it.isNotEmpty() },
                        order = item.optInt("order", index).coerceAtLeast(0)
                    )
                )
            }
        }
        if (items.isNotEmpty()) return items.sortedBy { it.order }

        // Older image/profile posts may use the single-media fields.
        val legacyUrl = mediaUrl(raw.string("media_url")) ?: return emptyList()
        return listOf(
            FeedMedia(
                url = legacyUrl,
                type = mediaType(raw.string("media_type")),
                id = raw.string("media_id").trim().takeIf { it.isNotEmpty() }
            )
        )
    }

    private fun parseTextBackground(value: Any?): FeedTextBackground {
        if (value is JSONObject) {
            val background = value.string("value").trim()
            if (background.isEmpty()) return FeedTextBackground()

            val kind = when (value.string("kind")) {
                "solid" -> FeedTextBackgroundKind.SOLID
                "gradient" -> FeedTextBackgroundKind.GRADIENT
                "image" -> FeedTextBackgroundKind.IMAGE
                else -> FeedTextBackgroundKind.PLAIN
            }
            return FeedTextBackground(kind = kind, value = background)
        }

        if (value is String && value.isNotBlank()) {
            val background = value.trim()
            val lower = background.lowercase(Locale.ROOT)
            val kind = when {
                lower.contains("gradient") -> FeedTextBackgroundKind.GRADIENT
                lower.startsWith("http://") || lower.startsWith("https://") ||
                    lower.startsWith("/") || lower.startsWith("data:image/") ->
                    FeedTextBackgroundKind.IMAGE
                lower == "white" || lower == "#ffffff" -> FeedTextBackgroundKind.PLAIN
                else -> FeedTextBackgroundKind.SOLID
            }
            return FeedTextBackground(kind = kind, value = background)
        }
        return FeedTextBackground()
    }

    private fun postType(value: String, source: FeedSource): FeedPostType = when (value) {
        "text" -> FeedPostType.TEXT
        "image" -> FeedPostType.IMAGE
        "video" -> FeedPostType.VIDEO
        "profile_photo_update" -> FeedPostType.PROFILE_PHOTO_UPDATE
        "cover_photo_update" -> FeedPostType.COVER_PHOTO_UPDATE
        "" -> if (source == FeedSource.TEXT) FeedPostType.TEXT else FeedPostType.IMAGE
        else -> FeedPostType.UNKNOWN
    }

    private fun mediaType(value: String): FeedMediaType = when (value) {
        "image", "" -> FeedMediaType.IMAGE
        "video" -> FeedMediaType.VIDEO
        else -> FeedMediaType.UNKNOWN
    }

    private fun textStyle(value: String): FeedTextStyle = when (value) {
        "normal_center" -> FeedTextStyle.NORMAL_CENTER
        "bold_left" -> FeedTextStyle.BOLD_LEFT
        "normal_left" -> FeedTextStyle.NORMAL_LEFT
        else -> FeedTextStyle.BOLD_CENTER
    }

    private fun objectId(value: Any?): String? {
        val text = when (value) {
            is String -> value
            is JSONObject -> value.string("\$oid").ifEmpty { value.string("hex") }
            else -> return null
        }.trim()
        if (text.length != 24 || text.all { it == '0' } || text.any {
                it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F'
            }
        ) return null
        return text.lowercase(Locale.ROOT)
    }

    private fun instant(value: String): Instant? {
        if (value.isBlank()) return null
        return try {
            Instant.parse(value.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun mediaUrl(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return try {
            val parsed = URI(text)
            val resolved = if (parsed.isAbsolute) parsed else URI("$BASE_URL/").resolve(parsed)
            val scheme = resolved.scheme?.lowercase(Locale.ROOT)
            if ((scheme != "https" && scheme != "http") ||
                resolved.host.isNullOrBlank() || resolved.rawUserInfo != null
            ) null else resolved.toASCIIString()
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }

    private fun JSONObject.string(key: String): String =
        (opt(key) as? String).orEmpty()

    private fun httpError(status: Int, retryAfter: Int?): FeedApiException = when (status) {
        401 -> FeedApiException(
            message = "Your session has expired. Please sign in again.",
            failure = FeedApiFailure.SESSION_EXPIRED,
            statusCode = status
        )
        403 -> FeedApiException(
            message = "Your account cannot access the feed right now.",
            failure = FeedApiFailure.ACCESS_DENIED,
            statusCode = status
        )
        429 -> FeedApiException(
            message = "Too many requests. Please wait a moment and try again.",
            failure = FeedApiFailure.RATE_LIMITED,
            statusCode = status,
            retryAfterSeconds = retryAfter ?: 60
        )
        in 500..599 -> FeedApiException(
            message = "Feed is temporarily unavailable. Please try again.",
            failure = FeedApiFailure.SERVER,
            statusCode = status,
            retryAfterSeconds = retryAfter
        )
        else -> FeedApiException(
            message = "Could not load posts. Please try again.",
            failure = FeedApiFailure.REQUEST_REJECTED,
            statusCode = status
        )
    }

    private fun invalidResponse(cause: Throwable? = null): FeedApiException =
        FeedApiException(
            message = "Could not read the feed response. Please try again.",
            failure = FeedApiFailure.INVALID_RESPONSE,
            statusCode = 200,
            cause = cause
        )

    companion object {
        const val DEFAULT_PAGE_SIZE = 6
        const val MAX_PAGE_SIZE = 20
        private const val BASE_URL = "https://api.mongosky.com"
        private const val MAX_CURSOR_LENGTH = 2_048
        private const val MAX_RESPONSE_BYTES = 1_048_576
    }
}
