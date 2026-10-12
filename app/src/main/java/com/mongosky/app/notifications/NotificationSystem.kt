package com.mongosky.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.ContextCompat
import com.mongosky.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NotificationSystem {
    const val CHANNEL = "mongosky_social_v1"
    private const val PREFIX = "mongosky-social:"
    private val deliveryLock = Any()
    private val mutableTap = MutableStateFlow<NotificationPush?>(null)
    val tap = mutableTap.asStateFlow()
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    fun channel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "Social activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Follows, reactions, comments and replies on Mongosky"
            enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }
    fun enabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return manager(context).areNotificationsEnabled() && manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun show(context: Context, push: NotificationPush) = synchronized(deliveryLock) {
        val store = NotificationPushStore(context)
        val active = store.active() ?: return
        if (!NotificationPushPolicy.allowed(push, active.owner, active.binding, active.expiresAt, System.currentTimeMillis() / 1_000) || !enabled(context)) return
        if (store.wasDelivered(push.id)) return
        channel(context)
        val launch = Intent(context, MainActivity::class.java).setAction("com.mongosky.app.OPEN_NOTIFICATION")
            .setData(Uri.parse("mongosky://notification/${push.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("notification_id", push.id).putExtra("recipient_id", push.ownerId).putExtra("binding_id", push.bindingId)
            .putExtra("actor_id", push.actorId).putExtra("body", push.body)
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = context.resources.getIdentifier("ic_mongosky_notification", "drawable", context.packageName).takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(icon).setContentTitle("Mongosky")
            .setContentText(push.body).setStyle(Notification.BigTextStyle().bigText(push.body)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SOCIAL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(context, CHANNEL).setSmallIcon(icon).setContentTitle("Mongosky").setContentText("New social activity").build())
            .build()
        try { manager(context).notify(PREFIX + push.id, 0, notification); store.noteDelivered(push.id) } catch (_: SecurityException) { /* Permission may change during delivery. */ }
    }
    fun dismiss(context: Context, id: String) {
        manager(context).cancel(PREFIX + id, 0)
        NotificationPushStore(context).noteDelivered(id)
    }
    fun receiveIntent(context: Context, intent: Intent?) {
        if (intent?.action != "com.mongosky.app.OPEN_NOTIFICATION") return
        val keys = listOf("notification_id", "recipient_id", "binding_id", "actor_id", "body")
        val data = keys.mapNotNull { key -> intent.getStringExtra(key)?.let { key to it } }.toMap()
        val push = NotificationPushPolicy.parse(data) ?: return
        val active = NotificationPushStore(context).active() ?: return
        if (NotificationPushPolicy.allowed(push, active.owner, active.binding, active.expiresAt, System.currentTimeMillis() / 1_000)) {
            mutableTap.value = push; dismiss(context, push.id)
        }
        intent.action = null
    }
    fun consumeTap() { mutableTap.value = null }
    fun activate(context: Context, owner: String, token: String) = synchronized(deliveryLock) {
        val store = NotificationPushStore(context)
        val previous = store.active()
        val current = store.activate(owner,token)
        if (previous != null && current != previous) {
            mutableTap.value = null
            manager(context).activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach { manager(context).cancel(it.tag,it.id) }
        }
    }
    fun clear(context: Context) = synchronized(deliveryLock) {
        NotificationPushStore(context).clear(); mutableTap.value = null
        NotificationRegistration.cancel(context)
        manager(context).activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach { manager(context).cancel(it.tag, it.id) }
    }
}
