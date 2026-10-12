package com.mongosky.app.notifications

import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

internal fun interface NotificationEvents { fun frames(token: String): Flow<NotificationFrame> }

/** A bounded incremental SSE decoder. Heartbeats do not create list entries. */
internal class NotificationSseDecoder {
    private var event = ""
    private val data = StringBuilder()
    private var size = 0
    var retryMs = 1_000L
        private set
    fun line(value: String): NotificationPage? {
        require(value.length <= 65_536) { "Oversized SSE line" }
        if (value.isEmpty()) {
            val name = event; val json = data.toString(); event = ""; data.setLength(0); size = 0
            if (json.isEmpty()) return null
            return when (name) {
                "notifications.snapshot" -> NotificationJson.page(JSONObject(json))
                "notifications.error" -> throw IOException("Live notifications are temporarily unavailable.")
                else -> null
            }
        }
        size += value.length + 1
        require(size <= 1_048_576) { "Oversized SSE event" }
        if (value.startsWith(":")) return null
        val field = value.substringBefore(':')
        val rest = value.substringAfter(':', "").removePrefix(" ")
        when (field) {
            "event" -> event = rest
            "data" -> { if (data.isNotEmpty()) data.append('\n'); data.append(rest) }
            "retry" -> rest.toLongOrNull()?.takeIf { it in 1_000..30_000 }?.let { retryMs = it }
        }
        return null
    }
}

/** One foreground owner. Closing the Flow cancels the socket, including a blocked read. */
internal class NotificationSse(private val baseUrl: String = "https://api.mongosky.com", private val http: OkHttpClient = client) : NotificationEvents {
    override fun frames(token: String): Flow<NotificationFrame> = channelFlow {
        require(token.isNotBlank() && token.none { it <= ' ' || it >= '\u007f' })
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            send(NotificationFrame.Connection(if (failures == 0) NotificationConnection.CONNECTING else NotificationConnection.RECONNECTING))
            val decoder = NotificationSseDecoder()
            val call = http.newCall(Request.Builder().url("$baseUrl/api/notifications/stream")
                .header("Authorization", "Bearer $token").header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache").build())
            var cooldown = 0L
            try {
                coroutineScope {
                    val read = async(Dispatchers.IO) {
                        call.execute().use { response ->
                            if (response.code == 401) throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED, 401)
                            if (!response.isSuccessful) {
                                cooldown = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 300)?.times(1_000) ?: 0
                                if (response.code != 408 && response.code != 429 && response.code !in 500..599) throw StreamRejected()
                                throw IOException("Stream unavailable")
                            }
                            if (response.header("Content-Type")?.substringBefore(';')?.trim() != "text/event-stream") throw StreamRejected()
                            send(NotificationFrame.Connection(NotificationConnection.CONNECTED))
                            val source = response.body?.source() ?: throw invalidPostResponse()
                            var first = true
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val line = source.readUtf8LineStrict(65_536).let { if (first) it.removePrefix("\uFEFF") else it }
                                first = false
                                decoder.line(line)?.let { failures = 0; send(NotificationFrame.Snapshot(it)) }
                            }
                        }
                    }
                    try { read.await() } finally { call.cancel() }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: FeedApiException) {
                if (error.failure == FeedApiFailure.SESSION_EXPIRED) { send(NotificationFrame.Connection(NotificationConnection.UNAUTHORIZED)); return@channelFlow }
                failures++
            } catch (_: StreamRejected) { send(NotificationFrame.Connection(NotificationConnection.ERROR)); return@channelFlow }
            catch (_: Exception) { failures++ }
            finally { call.cancel() }
            send(NotificationFrame.Connection(NotificationConnection.RECONNECTING))
            val backoff = (decoder.retryMs * (1L shl failures.coerceIn(0, 5))).coerceAtMost(30_000)
            delay(maxOf(cooldown, backoff / 2 + Random.nextLong(backoff / 2 + 1)))
        }
    }
    private class StreamRejected : IOException()
    private companion object {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
