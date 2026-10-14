package com.mongosky.app.downloadpost

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.*

internal class PostDownloads private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gallery = PostDownloadGallery(app)
    val coordinator = PostDownloadCoordinator(scope, PostDownloadJournal(app), gallery,
        PostDownloadEngine(PostDownloadNetwork(), PostDownloadTextRenderer(), gallery, File(app.cacheDir, "post-gallery-downloads")))
    @Volatile var startError: String? = null
        private set
    init { scope.launch { runCatching { coordinator.initialize() } } }
    fun reportStartFailure(plan: DownloadPlan) { scope.launch { coordinator.failedToStart(plan) } }
    fun request(plan: DownloadPlan): Boolean = try {
        DownloadPolicy.validate(plan); startError = null
        ContextCompat.startForegroundService(app, Intent(app, PostDownloadService::class.java)
            .setAction(PostDownloadService.START).putExtra(PostDownloadService.PLAN, plan.json().toString()))
        true
    } catch (_: Exception) { startError = "Could not start the download. Open the app and try again."; false }
    companion object {
        @Volatile private var instance: PostDownloads? = null
        fun get(context: Context): PostDownloads = instance ?: synchronized(this) {
            instance ?: PostDownloads(context).also { instance = it }
        }
    }
}
