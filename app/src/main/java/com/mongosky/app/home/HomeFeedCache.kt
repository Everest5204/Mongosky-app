package com.mongosky.app.home

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal interface HomeFeedCache {
    suspend fun read(token: String): HomeTimelinePage? = null
    suspend fun write(token: String, snapshot: String) {}
    suspend fun clear(token: String) {}
}

internal object NoHomeFeedCache : HomeFeedCache

/** App-private, excluded from backup, token-scoped, bounded metadata only.
 * Network starts in parallel; disk failure never delays or fails the feed.
 */
internal class HomeFeedDiskCache(context: Context, private val clock: () -> Long = System::currentTimeMillis) : HomeFeedCache {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "home_feed_v1.json"))
    override suspend fun read(token: String): HomeTimelinePage? = withContext(Dispatchers.IO) {
        val body = synchronized(lock) {
            runCatching {
                val bytes = file.openRead().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val root = JSONObject(String(bytes, Charsets.UTF_8))
                require(root.optString("owner") == owner(token))
                val age = clock() - root.getLong("saved_at")
                require(age in 0..MAX_AGE_MILLIS)
                root.getString("body")
            }.getOrNull()
        } ?: return@withContext null
        runCatching { HomeTimelineApi.decode(body) }.getOrNull()
    }

    override suspend fun write(token: String, snapshot: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            ensureActive()
            val bytes = JSONObject().put("owner", owner(token)).put("saved_at", clock()).put("body", snapshot)
                .toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_BYTES) return@synchronized
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
    }

    override suspend fun clear(token: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            // A late logout from account A must not delete account B's snapshot.
            val saved = runCatching { JSONObject(String(file.readFully(), Charsets.UTF_8)).optString("owner") }.getOrNull()
            if (saved == owner(token)) file.delete()
        }
    }

    private fun owner(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object {
        val lock = Any()
        const val MAX_BYTES = 1_048_576
        const val MAX_AGE_MILLIS = 10 * 60_000L
    }
}
