package com.mongosky.app.mediapost

import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.mediapost.MediaPostUploadLimits
import com.mongosky.app.mediapost.MediaPostUploadProgress
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.mediapost.PreparedMediaPost
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject

class MediaPostUploadException(
    message: String,
    val failure: FeedApiFailure,
    val canRetry: Boolean = false,
    val outcomeUnknown: Boolean = false,
    val statusCode: Int? = null,
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause) {
    val requiresSignIn: Boolean
        get() = failure == FeedApiFailure.SESSION_EXPIRED
}

class MediaPostUploadApi(
    private val client: OkHttpClient = SHARED_CLIENT
) {
    // Callbacks run off the main thread.
    // The repository will update StateFlow.
    suspend fun upload(
        token: String,
        prepared: PreparedMediaPost,
        onProgress: (MediaPostUploadProgress) -> Unit = {},
        onPublishing: () -> Unit = {}
    ): MediaPost = withContext(Dispatchers.IO) {
        ensureActive()

        val cleanToken = token.trim()

        if (
            cleanToken.isEmpty() ||
            cleanToken.any { it <= ' ' || it >= '\u007f' }
        ) {
            throw MediaPostUploadException(
                "Please sign in again.",
                FeedApiFailure.SESSION_EXPIRED
            )
        }

        val started = AtomicBoolean(false)
        val context = currentCoroutineContext()
        val multipart = buildMultipart(prepared)
        val length = multipart.contentLength()

        if (length !in 1L..MAX_REQUEST_BYTES) {
            throw rejected(
                "This upload is too large. Select smaller files."
            )
        }

        val body = ProgressBody(
            delegate = multipart,
            length = length,
            started = started,
            onProgress = {
                if (context.isActive) onProgress(it)
            },
            onPublishing = {
                if (context.isActive) onPublishing()
            }
        )

        val request = Request.Builder()
            .url("https://api.mongosky.com/api/posts/media")
            .header("Authorization", "Bearer $cleanToken")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .post(body)
            .build()

        try {
            val result = awaitResponse(request)

            ensureActive()

            if (result.code !in 200..299) {
                throw httpError(result)
            }

            parsePost(result.body, prepared)
        } catch (error: MediaPostUploadException) {
            ensureActive()
            throw error
        } catch (error: IOException) {
            ensureActive()

            val unknown = started.get()

            throw MediaPostUploadException(
                message = if (unknown) {
                    UNKNOWN_RESULT
                } else {
                    "Could not connect. Check your connection and try again."
                },
                failure = if (error is InterruptedIOException) {
                    FeedApiFailure.TIMEOUT
                } else {
                    FeedApiFailure.NETWORK
                },
                canRetry = !unknown,
                outcomeUnknown = unknown,
                cause = error
            )
        }
    }

    private fun buildMultipart(
        prepared: PreparedMediaPost
    ): MultipartBody {
        if (
            !OBJECT_ID.matches(prepared.ownerUserId) ||
            prepared.media.isEmpty()
        ) {
            throw rejected(
                "Upload media or account details are unavailable."
            )
        }

        val kind = prepared.media.first().kind

        val maxCount = if (kind == PostMediaKind.IMAGE) {
            MediaPostUploadLimits.MAX_IMAGES
        } else {
            MediaPostUploadLimits.MAX_VIDEOS
        }

        if (
            prepared.media.size > maxCount ||
            prepared.media.any { it.kind != kind }
        ) {
            throw rejected("Select up to 10 photos or one video.")
        }

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("caption", prepared.caption)

        for (item in prepared.media) {
            val file = File(item.localPath)
            val mime = item.mimeType.toMediaTypeOrNull()

            val expectedType = if (kind == PostMediaKind.IMAGE) {
                "image"
            } else {
                "video"
            }

            if (
                mime == null ||
                mime.type != expectedType ||
                !file.isFile ||
                item.sizeBytes !in 1L..item.kind.maxFileBytes ||
                file.length() != item.sizeBytes
            ) {
                throw rejected(
                    "Upload media is unavailable. Please select it again."
                )
            }

            builder.addFormDataPart(
                "media",
                item.name,
                file.asRequestBody(mime)
            )
        }

        return builder.build()
    }

    private suspend fun awaitResponse(
        request: Request
    ): HttpResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!continuation.isActive) return

                        val body = response.body
                            ?: throw invalidResponse()

                        if (body.contentLength() > MAX_RESPONSE_BYTES) {
                            throw invalidResponse()
                        }

                        val output = ByteArrayOutputStream()

                        body.byteStream().use { input ->
                            val buffer = ByteArray(8_192)

                            while (continuation.isActive) {
                                val count = input.read(buffer)

                                if (count < 0) break

                                if (
                                    output.size() + count >
                                    MAX_RESPONSE_BYTES
                                ) {
                                    throw invalidResponse()
                                }

                                output.write(buffer, 0, count)
                            }
                        }

                        val result = HttpResult(
                            code = response.code,
                            body = output.toString("UTF-8"),
                            retryAfter = response
                                .header("Retry-After")
                                ?.trim()
                                ?.toIntOrNull()
                                ?.coerceIn(1, 3_600)
                        )

                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            }
        })
    }

    private fun parsePost(
        body: String,
        prepared: PreparedMediaPost
    ): MediaPost {
        try {
            val post = JSONObject(body).getJSONObject("post")

            val id = objectId(post.opt("id"))
                ?: objectId(post.opt("_id"))
                ?: throw invalidResponse()

            val userId = objectId(post.opt("user_id"))
                ?: throw invalidResponse()

            require(
                userId.equals(
                    prepared.ownerUserId,
                    ignoreCase = true
                )
            )

            require(!post.optBoolean("is_deleted", false))

            val type = when (post.string("type")) {
                "image" -> MediaPostType.IMAGE
                "video" -> MediaPostType.VIDEO
                else -> throw invalidResponse()
            }

            val expectedKind = prepared.media.first().kind

            require(
                (type == MediaPostType.IMAGE) ==
                        (expectedKind == PostMediaKind.IMAGE)
            )

            val rows = post.getJSONArray("media")

            require(rows.length() == prepared.media.size)

            val media = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)

                val mediaType = when (row.string("type")) {
                    "image" -> FeedMediaType.IMAGE
                    "video" -> FeedMediaType.VIDEO
                    else -> throw invalidResponse()
                }

                require(
                    (mediaType == FeedMediaType.IMAGE) ==
                            (expectedKind == PostMediaKind.IMAGE)
                )

                FeedMedia(
                    url = secureUrl(row.string("url"))
                        ?: throw invalidResponse(),
                    type = mediaType,
                    id = row.string("id").takeIf {
                        it.isNotBlank()
                    },
                    order = row.optInt("order", index)
                )
            }.sortedBy { it.order }

            require(
                media.map { it.order } == media.indices.toList()
            )

            val user = post.getJSONObject("user")
            val authorId = objectId(user.opt("id")) ?: userId

            require(authorId == userId)

            val createdAt = Instant.parse(
                post.string("created_at")
            )

            val updatedAt = post.string("updated_at")
                .takeIf { it.isNotBlank() }
                ?.let(Instant::parse)
                ?: createdAt

            return MediaPost(
                id = id,
                userId = userId,
                author = FeedAuthor(
                    id = authorId,
                    firstName = user.string("first_name"),
                    lastName = user.string("last_name"),
                    profileImageUrl = secureUrl(
                        user.string("profile_image_url")
                    )
                ),
                createdAt = createdAt,
                updatedAt = updatedAt,
                type = type,
                caption = post.string("caption"),
                media = media,
                likesCount = post
                    .optLong("likes_count", 0L)
                    .coerceAtLeast(0L),
                commentsCount = post
                    .optLong("comments_count", 0L)
                    .coerceAtLeast(0L),
                sharesCount = post
                    .optLong("shares_count", 0L)
                    .coerceAtLeast(0L),
                isSelf = true,
                isFollowing = false
            )
        } catch (error: MediaPostUploadException) {
            throw error
        } catch (error: Exception) {
            throw invalidResponse(error)
        }
    }

    private fun httpError(
        result: HttpResult
    ): MediaPostUploadException {
        val code = result.code

        return when (code) {
            401 -> MediaPostUploadException(
                "Your session has expired. Please sign in again.",
                FeedApiFailure.SESSION_EXPIRED,
                statusCode = code
            )

            403 -> MediaPostUploadException(
                "You do not have permission to publish this post.",
                FeedApiFailure.ACCESS_DENIED,
                statusCode = code
            )

            404 -> rejected(
                "The upload service is unavailable.",
                code
            )

            413 -> rejected(
                "This upload is too large. Select smaller files.",
                code
            )

            415, 422 -> rejected(
                "This media format could not be uploaded.",
                code
            )

            429 -> MediaPostUploadException(
                "Too many requests. Please wait before retrying.",
                FeedApiFailure.RATE_LIMITED,
                canRetry = true,
                statusCode = code,
                retryAfterSeconds = result.retryAfter
            )

            400 -> {
                val serverError = runCatching {
                    JSONObject(result.body)
                        .string("error")
                        .trim()
                        .lowercase(Locale.ROOT)
                }.getOrNull()

                val known = VALIDATION_ERRORS[serverError]

                if (known != null) {
                    rejected(known, code)
                } else {
                    MediaPostUploadException(
                        UNKNOWN_RESULT,
                        FeedApiFailure.SERVER,
                        outcomeUnknown = true,
                        statusCode = code
                    )
                }
            }

            else -> MediaPostUploadException(
                UNKNOWN_RESULT,
                if (code >= 500) {
                    FeedApiFailure.SERVER
                } else {
                    FeedApiFailure.REQUEST_REJECTED
                },
                outcomeUnknown = true,
                statusCode = code
            )
        }
    }

    private fun rejected(
        message: String,
        status: Int? = null
    ) = MediaPostUploadException(
        message,
        FeedApiFailure.REQUEST_REJECTED,
        statusCode = status
    )

    private fun invalidResponse(
        cause: Throwable? = null
    ) = MediaPostUploadException(
        UNKNOWN_RESULT,
        FeedApiFailure.INVALID_RESPONSE,
        outcomeUnknown = true,
        cause = cause
    )

    private fun JSONObject.string(key: String): String =
        (opt(key) as? String).orEmpty()

    private fun objectId(value: Any?): String? {
        val raw = when (value) {
            is String -> value
            is JSONObject -> value.string("\$oid")
            else -> return null
        }.trim()

        return raw.takeIf {
            OBJECT_ID.matches(it)
        }?.lowercase(Locale.ROOT)
    }

    private fun secureUrl(value: String): String? =
        value.toHttpUrlOrNull()
            ?.takeIf {
                it.isHttps &&
                        it.username.isEmpty() &&
                        it.password.isEmpty()
            }
            ?.toString()

    private data class HttpResult(
        val code: Int,
        val body: String,
        val retryAfter: Int?
    )

    private class ProgressBody(
        private val delegate: RequestBody,
        private val length: Long,
        private val started: AtomicBoolean,
        private val onProgress: (MediaPostUploadProgress) -> Unit,
        private val onPublishing: () -> Unit
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()

        override fun contentLength() = length

        override fun isOneShot() = true

        override fun writeTo(sink: BufferedSink) {
            if (!started.compareAndSet(false, true)) {
                throw IOException(
                    "An upload cannot be automatically resent."
                )
            }

            var sent = 0L
            var lastUpdate = System.nanoTime()

            onProgress(MediaPostUploadProgress(0L, length))

            val tracking = object : ForwardingSink(sink) {
                override fun write(
                    source: Buffer,
                    byteCount: Long
                ) {
                    super.write(source, byteCount)

                    sent += byteCount

                    val now = System.nanoTime()

                    if (
                        sent == length ||
                        now - lastUpdate >= PROGRESS_INTERVAL_NANOS
                    ) {
                        lastUpdate = now

                        onProgress(
                            MediaPostUploadProgress(sent, length)
                        )
                    }
                }
            }.buffer()

            delegate.writeTo(tracking)
            tracking.flush()

            onPublishing()
        }
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 250L * 1024L * 1024L
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        const val PROGRESS_INTERVAL_NANOS = 100_000_000L

        const val UNKNOWN_RESULT =
            "Could not confirm whether your post was published. Check your posts before trying again."

        val OBJECT_ID = Regex("[a-fA-F0-9]{24}")

        val VALIDATION_ERRORS = mapOf(
            "media is required" to
                    "Select a photo or video first.",

            "media file is required" to
                    "Select a photo or video first.",

            "invalid media upload" to
                    "Could not read this upload. Please select the media again.",

            "only image or video media is allowed" to
                    "Please select a photo or video.",

            "video post supports only one video" to
                    "You can select only one video.",

            "maximum 10 images allowed" to
                    "You can select up to 10 photos.",

            "mixed image and video post is not allowed" to
                    "Photos and videos cannot be posted together.",

            "image file is too large" to
                    "Each photo must be 15 MB or smaller.",

            "video file is too large" to
                    "The video must be 200 MB or smaller."
        )

        val SHARED_CLIENT = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.MINUTES)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
