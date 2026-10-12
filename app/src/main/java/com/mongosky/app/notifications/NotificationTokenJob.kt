package com.mongosky.app.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.network.PostHttp
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NotificationTokenJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val store = NotificationPushStore(applicationContext)
            val binding = store.active()
            var retry = false
            try {
                val fcm = store.token(); val auth = TokenStore(applicationContext).read()
                if (binding != null && !fcm.isNullOrBlank() && !auth.isNullOrBlank() && NotificationSystem.enabled(applicationContext) && store.active() == binding) {
                    PostHttp().request(auth, "PUT", "/api/notifications/push/device", body = JSONObject()
                        .put("device_id", binding.device).put("binding_id", binding.binding).put("revision", binding.revision).put("token", fcm))
                    store.registered(binding, fcm)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if ((error as? FeedApiException)?.failure == FeedApiFailure.SESSION_EXPIRED && store.active() == binding) NotificationSystem.clear(applicationContext)
                else retry = store.active() == binding && binding != null && NotificationSystem.enabled(applicationContext)
            }
            withContext(Dispatchers.Main) { jobFinished(params, retry) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return NotificationPushStore(applicationContext).active() != null && NotificationSystem.enabled(applicationContext) }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
