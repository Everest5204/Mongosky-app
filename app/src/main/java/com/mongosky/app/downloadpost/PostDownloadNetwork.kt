package com.mongosky.app.downloadpost

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*

internal data class MediaFormat(val mime: String, val extension: String, val video: Boolean)
internal data class DownloadedFile(val file: File, val format: MediaFormat)
internal interface DownloadNetwork {
    suspend fun fetch(asset: DownloadAsset, destination: File, budget: AtomicLong, progress: (Long, Long) -> Unit): DownloadedFile
}
/** Streams originals off Main. The media client never carries account tokens or API interceptors. */
internal class PostDownloadNetwork(private val client: OkHttpClient = shared,
    private val allowedUrl: (String) -> Boolean = DownloadPolicy::safeUrl) : DownloadNetwork {
    override suspend fun fetch(asset: DownloadAsset, destination: File, budget: AtomicLong, progress: (Long, Long) -> Unit): DownloadedFile = withContext(Dispatchers.IO) {
        val maximum = if (asset.video) DownloadPolicy.MAX_VIDEO_BYTES else DownloadPolicy.MAX_IMAGE_BYTES
        var url = asset.url
        try {
            repeat(5) { redirect ->
                if (!allowedUrl(url)) throw DownloadException("This media link is unavailable.")
                val call = client.newCall(Request.Builder().url(url).header("Accept", if (asset.video) "video/*" else "image/*").get().build())
                val response = suspendCancellableCoroutine<Response> { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                        override fun onResponse(call: Call, response: Response) {
                            if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() } else response.close()
                        }
                    })
                }
                val guardian = CoroutineScope(currentCoroutineContext() + Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try { response.use { value ->
                    if (value.code in setOf(301, 302, 303, 307, 308)) {
                        url = value.request.url.resolve(value.header("Location") ?: throw DownloadException("This media link is unavailable."))?.toString()
                            ?: throw DownloadException("This media link is unavailable.")
                        if (redirect == 4 || !allowedUrl(url)) throw DownloadException("This media link is unavailable.")
                        return@use
                    }
                    if (value.code != 200) throw DownloadException("Could not download this media. Please try again.")
                    val body = value.body ?: throw DownloadException("This media is unavailable.")
                    val length = body.contentLength()
                    if (length == 0L || length > maximum) throw DownloadException("This media file is empty or too large.")
                    var written = 0L
                    body.byteStream().use { input -> destination.outputStream().buffered(64 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive(); val n = input.read(buffer); if (n < 0) break
                            written += n
                            if (written > maximum || budget.addAndGet(n.toLong()) > DownloadPolicy.MAX_BATCH_BYTES) throw DownloadException("This post is too large to save.")
                            output.write(buffer, 0, n); progress(written, length)
                        }
                    } }
                    ensureActive()
                    if (written == 0L || length >= 0 && written != length) throw DownloadException("The download was interrupted. Please retry.")
                    val head = destination.inputStream().use { input -> ByteArray(64).let { b -> b.copyOf(input.read(b).coerceAtLeast(0)) } }
                    val format = sniff(head, asset.video) ?: throw DownloadException("This media format could not be saved.")
                    return@withContext DownloadedFile(destination, format)
                } } finally { guardian.cancel(); call.cancel() }
            }
            throw DownloadException("This media link is unavailable.")
        } catch (error: Throwable) { destination.delete(); ensureActive(); throw error }
    }
    companion object {
        private val shared = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        fun sniff(bytes: ByteArray, video: Boolean): MediaFormat? {
            fun ascii(at: Int, value: String) = bytes.size >= at + value.length && value.indices.all { bytes[at + it].toInt() and 255 == value[it].code }
            return if (video) {
                if (ascii(4, "ftyp") && bytes.size >= 12) {
                    val brand = String(bytes, 8, 4, Charsets.US_ASCII)
                    if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis")) null
                    else MediaFormat(if (brand == "qt  ") "video/quicktime" else "video/mp4", if (brand == "qt  ") "mov" else "mp4", true)
                } else if (bytes.size >= 4 && bytes.take(4).map { it.toInt() and 255 } == listOf(26, 69, 223, 163)) MediaFormat("video/webm", "webm", true) else null
            } else when {
                bytes.size >= 3 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 255 == 216 && bytes[2].toInt() and 255 == 255 -> MediaFormat("image/jpeg", "jpg", false)
                bytes.size >= 8 && bytes.take(8).map { it.toInt() and 255 } == listOf(137, 80, 78, 71, 13, 10, 26, 10) -> MediaFormat("image/png", "png", false)
                ascii(0, "GIF87a") || ascii(0, "GIF89a") -> MediaFormat("image/gif", "gif", false)
                ascii(0, "RIFF") && ascii(8, "WEBP") -> MediaFormat("image/webp", "webp", false)
                else -> null
            }
        }
    }
}
