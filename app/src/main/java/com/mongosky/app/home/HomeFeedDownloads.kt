package com.mongosky.app.home

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.HomePost
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*

internal data class HomeDownloadState(val post: HomePost, val choosing: Boolean = true) {
    val fileName: String get() = "mongosky-post-${post.id}." + when {
        post is HomePost.Text || post.photos.isEmpty() -> "txt"
        post.photos.size > 1 -> "zip"
        else -> homeMediaExtension(post.photos.first())
    }
    val mimeType: String get() = when {
        post is HomePost.Text || post.photos.isEmpty() -> "text/plain"
        post.photos.size > 1 -> "application/zip"
        else -> when (homeMediaExtension(post.photos.first())) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            else -> "image/jpeg"
        }
    }
}

internal fun homeMediaExtension(media: FeedMedia): String {
    val path = runCatching { URI(media.url).path }.getOrNull().orEmpty()
    val extension = path.substringAfterLast('.', "").lowercase()
    return extension.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4") }
        ?: if (media.type == FeedMediaType.VIDEO) "mp4" else "jpg"
}

/** Android's document picker needs no storage permission; originals are streamed off Main. */
internal object HomeFeedDownloads {
    suspend fun write(context: Context, uri: Uri, post: HomePost) = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Unable to open this file")
            output.use { out ->
                val media = post.photos
                when {
                    post is HomePost.Text || media.isEmpty() -> out.write(post.content.toByteArray(Charsets.UTF_8))
                    media.size == 1 -> writeMedia(media.first(), out)
                    else -> ZipOutputStream(out).use { zip ->
                        for ((index, photo) in media.withIndex()) {
                            ensureActive()
                            zip.putNextEntry(ZipEntry("mongosky-post-${post.id}-${index + 1}.${homeMediaExtension(photo)}"))
                            writeMedia(photo, zip)
                            zip.closeEntry()
                        }
                        if (post.content.isNotBlank()) {
                            zip.putNextEntry(ZipEntry("caption.txt"))
                            zip.write(post.content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                        }
                    }
                }
            }
            ensureActive()
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            throw error
        }
    }

    private suspend fun writeMedia(media: FeedMedia, output: OutputStream) {
        val uri = runCatching { URI(media.url) }.getOrNull()
        require(uri?.scheme == "https" && uri.userInfo == null) { "This photo is unavailable." }
        val call = HomeHttp.client.newCall(Request.Builder().url(media.url).get().build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() }
                    else response.close()
                }
            })
        }
        response.use {
            if (!it.isSuccessful) throw IOException("Could not download this photo")
            val body = it.body ?: throw IOException("This photo is unavailable")
            val maxBytes = if (media.type == FeedMediaType.VIDEO) 200L * 1_024 * 1_024 else 15L * 1_024 * 1_024
            if (body.contentLength() > maxBytes) throw IOException("This file is too large")
            body.byteStream().use { input ->
                val buffer = ByteArray(32_768)
                var written = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    if (written > maxBytes) throw IOException("This file is too large")
                    output.write(buffer, 0, count)
                }
            }
        }
    }
}
