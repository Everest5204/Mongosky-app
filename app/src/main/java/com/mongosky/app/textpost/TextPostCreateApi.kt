package com.mongosky.app.textpost

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedTextBackground
import com.mongosky.app.post.FeedTextBackgroundKind
import com.mongosky.app.post.FeedTextStyle
import com.mongosky.app.textpost.TextPost
import com.mongosky.app.textpost.TextPostDraft
import com.mongosky.app.textpost.TextPostLimits
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject

class TextPostCreateException(
    message: String,
    val canRetry: Boolean = false,
    val outcomeUnknown: Boolean = false,
    val requiresSignIn: Boolean = false,
    cause: Throwable? = null
) : IOException(message, cause)

fun interface TextPostCreator {
    suspend fun create(token: String, draft: TextPostDraft): TextPost
}

/** Small JSON publication request; feed loading remains in TextPostApi. */
class TextPostCreateApi(
    client: OkHttpClient = SHARED_CLIENT,
    private val endpoint: String = "https://api.mongosky.com/api/text-posts"
) : TextPostCreator {
    // A POST must never be automatically repeated or forwarded with a bearer token.
    private val client = client.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()

    override suspend fun create(token: String, draft: TextPostDraft): TextPost = withContext(Dispatchers.IO) {
        ensureActive()
        val cleanToken = token.trim()
        if (cleanToken.isEmpty() || cleanToken.any { it <= ' ' || it >= '\u007f' }) {
            throw TextPostCreateException("Please sign in again.", requiresSignIn = true)
        }
        val text = draft.text.trim()
        if (!OBJECT_ID.matches(draft.ownerUserId) || text.isEmpty() ||
            TextPostLimits.length(text) > TextPostLimits.MAX_CHARACTERS
        ) throw TextPostCreateException("Enter between 1 and 650 characters.", canRetry = true)

        val background = JSONObject().put("kind", draft.preset.background.kind.name.lowercase())
            .put("value", draft.preset.background.value)
        val bytes = JSONObject().put("type", "text").put("text", text).put("caption", text)
            .put("text_background", background).put("background", background)
            .put("text_color", draft.preset.textColor).put("text_style", "bold_center")
            .toString().toByteArray(Charsets.UTF_8)
        val started = AtomicBoolean(false)
        val body = object : RequestBody() {
            override fun contentType() = "application/json; charset=utf-8".toMediaType()
            override fun contentLength() = bytes.size.toLong()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                started.set(true)
                sink.write(bytes)
            }
        }
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $cleanToken")
            .header("Accept", "application/json").header("Cache-Control", "no-store")
            .post(body).build()

        try {
            val result = await(request)
            ensureActive()
            if (result.status != 201) throw httpError(result)
            parsePost(result.body, draft.ownerUserId, text)
        } catch (error: TextPostCreateException) {
            ensureActive()
            throw error
        } catch (error: IOException) {
            ensureActive()
            throw TextPostCreateException(
                if (started.get()) UNKNOWN_RESULT else "Could not connect. Check your internet and try again.",
                canRetry = !started.get(), outcomeUnknown = started.get(), cause = error
            )
        }
    }

    private data class Result(val status: Int, val body: String)

    private suspend fun await(request: Request): Result = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val body = it.body ?: throw IOException("Empty response")
                        body.byteStream().use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Response too large")
                                output.write(buffer, 0, count)
                            }
                            Result(it.code, output.toString("UTF-8"))
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun httpError(result: Result): TextPostCreateException {
        val message = runCatching { JSONObject(result.body).optString("error") }.getOrDefault("").trim()
        return when {
            result.status == 401 -> TextPostCreateException("Your session expired. Please sign in again.", requiresSignIn = true)
            result.status == 403 -> TextPostCreateException("You don't have permission to publish this post.")
            result.status == 429 -> TextPostCreateException("Too many posts. Wait a moment before trying again.", canRetry = true)
            result.status in listOf(405, 413, 415, 422) -> TextPostCreateException("The server rejected this post.", canRetry = true)
            result.status == 400 && message in VALIDATION_ERRORS -> TextPostCreateException(message, canRetry = true)
            // The backend can return 400 after InsertOne if author lookup fails.
            else -> TextPostCreateException(UNKNOWN_RESULT, outcomeUnknown = true)
        }
    }

    private fun parsePost(body: String, ownerUserId: String, requestedText: String): TextPost = try {
        val root = JSONObject(body)
        val raw = root.optJSONObject("post") ?: root
        val user = raw.getJSONObject("user")
        val id = raw.getString("id")
        val userId = raw.getString("user_id")
        val text = raw.getString("text")
        require(OBJECT_ID.matches(id) && userId == ownerUserId && user.getString("id") == ownerUserId)
        require(raw.getString("type") == "text" && raw.getString("text_style") == "bold_center" &&
            text == requestedText && !raw.optBoolean("is_deleted"))
        val created = Instant.parse(raw.getString("created_at"))
        val updated = Instant.parse(raw.getString("updated_at"))
        val bg = raw.getJSONObject("text_background")
        val kind = when (bg.getString("kind")) {
            "plain" -> FeedTextBackgroundKind.PLAIN
            "solid" -> FeedTextBackgroundKind.SOLID
            "gradient" -> FeedTextBackgroundKind.GRADIENT
            else -> throw IllegalArgumentException("Unexpected background")
        }
        TextPost(
            id = id, userId = userId,
            author = FeedAuthor(userId, user.optString("first_name"), user.optString("last_name"),
                user.optString("profile_image_url").takeIf { it.startsWith("https://") }),
            createdAt = created, updatedAt = updated, text = text, caption = raw.optString("caption", text),
            textBackground = FeedTextBackground(kind, bg.getString("value")),
            textColor = raw.getString("text_color"), textStyle = FeedTextStyle.BOLD_CENTER,
            likesCount = raw.optLong("likes_count").coerceAtLeast(0),
            commentsCount = raw.optLong("comments_count").coerceAtLeast(0),
            sharesCount = raw.optLong("shares_count").coerceAtLeast(0), isSelf = true
        )
    } catch (error: Exception) {
        throw TextPostCreateException(UNKNOWN_RESULT, outcomeUnknown = true, cause = error)
    }

    companion object {
        const val UNKNOWN_RESULT = "The server may have received your post. Check your Home feed before posting it again."
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private val OBJECT_ID = Regex("^[0-9a-f]{24}$")
        private val VALIDATION_ERRORS = setOf("text is required", "text is too long", "invalid user id", "Invalid request body")
        private val SHARED_CLIENT = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
