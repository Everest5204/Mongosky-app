package com.mongosky.app.downloadpost

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One app-wide queue deduplicates requests across menus, navigation and rotation. */
internal class PostDownloadCoordinator(private val scope: CoroutineScope, private val journal: DownloadJournal,
    private val gallery: DownloadGallery, private val engine: PostDownloadEngine,
    private val changed: (DownloadProgress) -> Unit = {}) {
    private val mutable = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val state = mutable.asStateFlow()
    val visible = MutableStateFlow<String?>(null)
    private val mutex = Mutex(); private val execution = Mutex()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var loaded = false

    suspend fun initialize() = mutex.withLock {
        if (loaded) return@withLock
        engine.clearInterruptedStaging()
        val recovered = journal.load().map { row -> if (row.active) {
            val published = runCatching {
                if (row.saved.size == row.plan.count && gallery.exists(row.saved)) gallery.publish(row.saved) else null
            }.getOrNull()
            if (published != null) row.copy(phase = DownloadPhase.SUCCEEDED, saved = published, completed = row.plan.count, fraction = 1f, message = "Saved to Gallery")
            else {
                runCatching { gallery.remove(row.saved) }
                row.copy(phase = DownloadPhase.FAILED, saved = emptyList(), message = "Download interrupted. Tap Retry.")
            }
        } else row }
        journal.write(recovered); mutable.value = recovered; loaded = true
        recovered.lastOrNull { it.message == "Download interrupted. Tap Retry." }?.let { visible.value = it.key }
    }
    suspend fun enqueue(plan: DownloadPlan) {
        initialize(); DownloadPolicy.validate(plan)
        mutex.withLock {
            val key = plan.key; visible.value = key
            val previous = mutable.value.firstOrNull { it.key == key }
            if (previous?.active == true) return@withLock
            if (previous?.phase == DownloadPhase.SUCCEEDED && gallery.exists(previous.saved)) {
                val existing = previous.copy(message = "Already saved to Gallery"); update(existing); announce(existing); return@withLock
            }
            if (mutable.value.count { it.active } >= 4) {
                val rejected = DownloadProgress(key, plan, DownloadPhase.FAILED, message = "Four downloads are already queued. Try again shortly.")
                update(rejected); announce(rejected); return@withLock
            }
            val queued = DownloadProgress(key, plan, DownloadPhase.QUEUED)
            journal.write(next(mutable.value, queued)); update(queued); announce(queued)
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try { execution.withLock {
                    val saving = queued.copy(phase = DownloadPhase.SAVING)
                    mutex.withLock { update(saving); persist() }; announce(saving)
                    var lastTick = 0L
                    engine.save(plan, rowsChanged = { files -> mutex.withLock {
                        update(requireNotNull(mutable.value.firstOrNull { it.key == key }).copy(saved = files)); persist()
                    } }, committing = {
                        val row = requireNotNull(mutable.value.firstOrNull { it.key == key }).copy(phase = DownloadPhase.PUBLISHING, completed = plan.count, fraction = 0.99f)
                        update(row); announce(row)
                    }, completed = { files -> mutex.withLock {
                        val done = DownloadProgress(key, plan, DownloadPhase.SUCCEEDED, plan.count, 1f, "Saved to Gallery", files)
                        // A failed receipt never produces a false success; the engine rolls back the media rows.
                        journal.write(next(mutable.value, done)); update(done); announce(done)
                    } }, progress = { count, fraction ->
                        val now = System.nanoTime() / 1_000_000
                        if (now - lastTick >= 200) {
                            lastTick = now
                            mutable.value.firstOrNull { it.key == key }?.takeIf { it.phase == DownloadPhase.SAVING }?.let {
                                val row = it.copy(completed = maxOf(it.completed, count), fraction = fraction); update(row); announce(row)
                            }
                        }
                    })
                } } catch (error: Throwable) {
                    withContext(NonCancellable) { mutex.withLock {
                        if (mutable.value.firstOrNull { it.key == key }?.phase != DownloadPhase.SUCCEEDED) {
                            val failed = DownloadProgress(key, plan, if (error is CancellationException) DownloadPhase.CANCELLED else DownloadPhase.FAILED,
                                message = if (error is CancellationException) "Download cancelled" else if (error is DownloadException) error.message
                                    else "Could not save this post. Check your connection and free space, then retry.")
                            update(failed); runCatching { persist() }; announce(failed)
                        }
                    } }
                    if (error is CancellationException) throw error
                }
            }
            jobs[key] = job
            job.invokeOnCompletion { cause -> scope.launch { mutex.withLock {
                if (jobs[key] !== job) return@withLock
                jobs.remove(key)
                mutable.value.firstOrNull { it.key == key }?.takeIf { it.active && cause is CancellationException }?.let {
                    val cancelled = it.copy(phase = DownloadPhase.CANCELLED, saved = emptyList(), message = "Download cancelled")
                    update(cancelled); runCatching { persist() }; announce(cancelled)
                }
            } } }
            job.start()
        }
    }
    suspend fun failedToStart(plan: DownloadPlan) = mutex.withLock {
        if (mutable.value.any { it.key == plan.key && it.active }) return@withLock
        val failed = DownloadProgress(plan.key, plan, DownloadPhase.FAILED, message = "Could not start saving. Open the app, check free space and try again.")
        update(failed); visible.value = plan.key; if (loaded) runCatching { persist() }; announce(failed)
    }
    fun cancel(key: String) {
        val row = mutable.value.firstOrNull { it.key == key } ?: return
        if (row.active && row.phase != DownloadPhase.PUBLISHING) jobs[key]?.cancel()
    }
    fun cancelAll() { jobs.values.toList().forEach { it.cancel() } }
    fun dismiss() { visible.value = null }
    private fun update(row: DownloadProgress) { mutable.update { next(it, row) } }
    private fun next(current: List<DownloadProgress>, row: DownloadProgress) = (current.filterNot { it.key == row.key } + row).let {
        it.filter { item -> item.active } + it.filterNot { item -> item.active }.takeLast(40)
    }
    private fun announce(row: DownloadProgress) { runCatching { changed(row) } }
    private fun persist() { journal.write(mutable.value) }
}
