package com.mongosky.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Data messages let us reject deliveries for a logged-out or replaced account before display. */
class NotificationMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        NotificationPushPolicy.parse(message.data)?.let { NotificationSystem.show(applicationContext, it) }
    }
    @Suppress("OVERRIDE_DEPRECATION") // Paired with the explicitly disabled FID targeting flag in the manifest.
    override fun onNewToken(token: String) {
        NotificationPushStore(applicationContext).setToken(token)
        NotificationRegistration.schedule(applicationContext)
    }
}
