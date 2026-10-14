package com.mongosky.app.downloadpost

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class PostDownloadEngine(private val network: DownloadNetwork, private val renderer: DownloadTextRenderer,
    private val gallery: DownloadGallery, private val cache: File) {
    fun clearInterruptedStaging() {
        cache.listFiles()?.filter { it.name.matches(Regex("[a-f0-9-]{36}")) }?.forEach { it.deleteRecursively() }
    }
    suspend fun save(plan: DownloadPlan, rowsChanged: suspend (List<SavedMedia>) -> Unit,
        committing: () -> Unit, completed: suspend (List<SavedMedia>) -> Unit,
        progress: (Int, Float?) -> Unit): List<SavedMedia> = withContext(Dispatchers.IO) {
        DownloadPolicy.validate(plan)
        val nonce = UUID.randomUUID().toString(); val directory = File(cache, nonce)
        if (!directory.mkdirs()) throw DownloadException("Could not prepare this download.")
        val rows = mutableListOf<SavedMedia>(); var published = emptyList<SavedMedia>(); var committed = false
        try {
            val budget = AtomicLong()
            val downloaded = if (plan.text != null) {
                val background = if (plan.text.kind == "IMAGE") network.fetch(DownloadAsset(plan.text.background, false), File(directory, "background"), budget) { _, _ -> progress(0, null) }.file else null
                listOf(renderer.render(plan.text, background, File(directory, "text.png")))
            } else coroutineScope {
                val slots = Semaphore(2); val fractions = FloatArray(plan.assets.size)
                plan.assets.mapIndexed { index, asset -> async {
                    slots.withPermit {
                        network.fetch(asset, File(directory, "$index.media"), budget) { bytes, total -> synchronized(fractions) {
                            fractions[index] = if (total > 0) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
                            progress(fractions.count { it >= 1f }, if (total > 0) fractions.sum() / plan.count * 0.9f else null)
                        } }
                    }
                } }.awaitAll()
            }
            for ((index, file) in downloaded.withIndex()) {
                ensureActive()
                // Persist the newly allocated URI before cancellation can hide it from cleanup/recovery.
                val row = withContext(NonCancellable) {
                    gallery.allocate(file, "Mongosky_${plan.postId}_${nonce}_${index + 1}.${file.format.extension}").also {
                        rows.add(it); rowsChanged(rows.toList())
                    }
                }
                gallery.copy(file.file, row); progress(index + 1, 0.9f + (index + 1f) / plan.count * 0.09f)
            }
            ensureActive(); committing()
            withContext(NonCancellable) { published = gallery.publish(rows); completed(published); committed = true }
            published
        } catch (error: Throwable) {
            if (committed) published else { withContext(NonCancellable) { runCatching { gallery.remove(rows) } }; throw error }
        } finally { directory.deleteRecursively() }
    }
}
