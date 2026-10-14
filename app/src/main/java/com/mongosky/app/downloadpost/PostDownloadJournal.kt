package com.mongosky.app.downloadpost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal interface DownloadJournal { fun load(): List<DownloadProgress>; fun write(rows: List<DownloadProgress>) }
/** Private receipts are persisted only at transaction boundaries, never for individual network buffers. */
internal class PostDownloadJournal(context: Context) : DownloadJournal {
    private val preferences = context.getSharedPreferences("mongosky_post_download_v1", Context.MODE_PRIVATE)
    override fun load(): List<DownloadProgress> {
        val raw = preferences.getString("records", "[]") ?: "[]"
        if (raw.toByteArray().size > 2 * 1024 * 1024) return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until minOf(rows.length(), 44)).mapNotNull { index -> runCatching {
                val row = rows.getJSONObject(index); val plan = DownloadPlan.parse(row.getJSONObject("plan").toString()); val files = row.getJSONArray("saved")
                require(files.length() <= 10)
                DownloadProgress(plan.key, plan, DownloadPhase.valueOf(row.getString("phase")), message = row.optString("message").takeIf { it.isNotBlank() },
                    saved = (0 until files.length()).map { files.getJSONObject(it).let { file ->
                        SavedMedia(file.getString("uri"), file.getString("name"), file.getString("mime"), file.getLong("size"))
                    } })
            }.getOrNull() }
        }.getOrDefault(emptyList())
    }
    override fun write(rows: List<DownloadProgress>) {
        val data = JSONArray().apply { rows.forEach { row -> put(JSONObject().put("plan", row.plan.json()).put("phase", row.phase.name)
            .put("message", row.message ?: "").put("saved", JSONArray().apply { row.saved.forEach { file ->
                put(JSONObject().put("uri", file.uri).put("name", file.name).put("mime", file.mime).put("size", file.size))
            } })) } }
        if (!preferences.edit().putString("records", data.toString()).commit()) throw DownloadException("Could not record this Gallery download.")
    }
}
