package com.mongosky.app.downloadpost

import com.mongosky.app.language.appText

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Visible, user-started data-sync service survives navigation, rotation and backgrounding. */
class PostDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var downloads: PostDownloads
    private var accepting = 0; private var foreground = false; private var lastTerminal: String? = null
    override fun onCreate() {
        super.onCreate(); downloads = PostDownloads.get(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, appText("Post downloads"), NotificationManager.IMPORTANCE_LOW).apply {
            description = appText("Progress for posts you save to Gallery"); setShowBadge(false)
        })
        scope.launch { downloads.coordinator.state.collectLatest { rows ->
            val active = foregroundDownload(rows)
            if (active != null && foreground) runCatching { manager.notify(ID, notification(active)) }
            if (active == null && accepting == 0 && foreground) finish(rows.lastOrNull())
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            intent.getStringExtra(KEY)?.let(downloads.coordinator::cancel)
            if (!foreground) stopSelf(startId)
            return START_NOT_STICKY
        }
        val plan = runCatching { DownloadPlan.parse(requireNotNull(intent?.getStringExtra(PLAN))) }.getOrNull()
        if (plan == null) { if (!foreground) stopSelf(startId); return START_NOT_STICKY }
        accepting++
        try {
            val note = notification(DownloadProgress(plan.key, plan, DownloadPhase.QUEUED))
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, note)
            foreground = true
            scope.launch {
                try { withContext(Dispatchers.IO) { downloads.coordinator.enqueue(plan) } }
                catch (error: Exception) { if (error !is CancellationException) withContext(Dispatchers.IO) { downloads.coordinator.failedToStart(plan) } }
                finally {
                    accepting--
                    if (accepting == 0 && downloads.coordinator.state.value.none { it.active } && foreground) finish(downloads.coordinator.state.value.lastOrNull())
                }
            }
        } catch (_: Exception) { accepting--; downloads.reportStartFailure(plan); if (!foreground) stopSelf(startId) }
        return START_NOT_STICKY
    }
    private fun finish(row: DownloadProgress?) {
        if (row != null && lastTerminal != row.key + row.phase) {
            lastTerminal = row.key + row.phase
            runCatching { getSystemService(NotificationManager::class.java).notify(RESULT_ID, notification(row)) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }
    private fun notification(row: DownloadProgress): Notification {
        val title = when (row.phase) {
            DownloadPhase.QUEUED -> "Preparing post download"; DownloadPhase.SAVING -> "Saving post to Gallery"
            DownloadPhase.PUBLISHING -> "Finishing Gallery save"; DownloadPhase.SUCCEEDED -> "Saved to Gallery"
            DownloadPhase.CANCELLED -> "Download cancelled"; DownloadPhase.FAILED -> "Post download failed"
        }
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(appText(title)).setContentText(appText(row.message ?: "${row.completed} of ${row.plan.count} files"))
            .setOnlyAlertOnce(true).setOngoing(row.active).setAutoCancel(!row.active)
        if (row.active) {
            builder.setProgress(100, ((row.fraction ?: 0f) * 100).toInt(), row.fraction == null)
            if (row.phase != DownloadPhase.PUBLISHING) {
                val cancel = Intent(this, PostDownloadService::class.java).setAction(CANCEL).putExtra(KEY, row.key)
                builder.addAction(Notification.Action.Builder(null, "Cancel", PendingIntent.getService(this, row.key.hashCode(), cancel,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            }
        } else if (row.phase == DownloadPhase.SUCCEEDED && row.saved.isNotEmpty()) {
            val file = row.saved.first()
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(file.uri), file.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(PendingIntent.getActivity(this, row.key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else if (row.phase == DownloadPhase.FAILED) {
            val retry = Intent(this, PostDownloadService::class.java).setAction(START).putExtra(PLAN, row.plan.json().toString())
            builder.addAction(Notification.Action.Builder(null, "Retry", PendingIntent.getForegroundService(this, row.key.hashCode(), retry,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        }
        return builder.build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { downloads.coordinator.cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf() }
    override fun onDestroy() { if (downloads.coordinator.state.value.any { it.active }) downloads.coordinator.cancelAll(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        internal const val START = "com.mongosky.app.downloadpost.START"; internal const val CANCEL = "com.mongosky.app.downloadpost.CANCEL"
        internal const val PLAN = "plan"; internal const val KEY = "key"
        private const val CHANNEL = "mongosky_post_downloads"; private const val ID = 7011; private const val RESULT_ID = 7012
    }
}
