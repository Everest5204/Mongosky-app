package com.mongosky.app.notifications

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/** Android's own JobScheduler retries token registration without keeping the app alive. */
internal object NotificationRegistration {
    private const val JOB = 91027
    @Suppress("DEPRECATION") // HTTP v1 token targeting uses Firebase's supported token compatibility mode.
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!NotificationSystem.enabled(app) || NotificationPushStore(app).active() == null) { cancel(app); return }
        try {
            if (FirebaseApp.getApps(app).isEmpty() && FirebaseApp.initializeApp(app) == null) return
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener { value ->
                val store = NotificationPushStore(app)
                if (store.token() != value) store.setToken(value)
                if (store.active() != null && store.needsRegistration()) schedule(app)
            }
        } catch (_: IllegalStateException) { /* Firebase is optional until the real project configuration is installed. */ }
    }
    fun schedule(context: Context) {
        if (NotificationPushStore(context).active() == null || !NotificationSystem.enabled(context)) return
        val service = context.getSystemService(JobScheduler::class.java)
        if (service.getPendingJob(JOB) != null) return
        service.schedule(JobInfo.Builder(JOB, ComponentName(context, NotificationTokenJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    fun cancel(context: Context) { context.getSystemService(JobScheduler::class.java).cancel(JOB) }
}
