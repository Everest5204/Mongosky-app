package com.mongosky.app.home

import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A separate dispatcher keeps a long-lived stream out of the feed request pool. */
internal object HomeFeedRealtime {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    fun events(token: String): Flow<Unit> = callbackFlow {
        val call = client.newCall(Request.Builder().url("https://api.mongosky.com/api/posts/home/events")
            .header("Authorization", "Bearer $token").header("Accept", "text/event-stream").build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (response.code == 401) throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED, 401)
                        if (response.code in listOf(404, 405, 501)) throw FeedApiException(
                            "Live feed updates are unavailable.", FeedApiFailure.REQUEST_REJECTED, response.code)
                        if (!response.isSuccessful || !response.header("Content-Type").orEmpty().startsWith("text/event-stream")) {
                            throw IOException("Feed updates are unavailable")
                        }
                        val source = response.body?.source() ?: throw IOException("Empty feed updates")
                        var event = ""
                        while (!isClosedForSend && !source.exhausted()) {
                            val line = source.readUtf8LineStrict(8192)
                            if (line.startsWith("event:")) event = line.substringAfter(':').trim()
                            if (line.isEmpty()) {
                                if (event == "feed.changed" || event == "feed.ready") trySend(Unit)
                                event = ""
                            }
                        }
                        close()
                    } catch (e: Exception) { close(e) }
                }
            }
        })
        awaitClose { call.cancel() }
    }.conflate()
}
