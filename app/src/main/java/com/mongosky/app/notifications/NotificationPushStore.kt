package com.mongosky.app.notifications

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

internal data class NotificationBinding(val owner: String, val binding: String, val device: String, val revision: Long, val expiresAt: Long)

/** Persist only routing metadata and FCM's installation token. The JWT stays in TokenStore. */
internal class NotificationPushStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("mongosky_notifications_v1", Context.MODE_PRIVATE)
    fun active(): NotificationBinding? {
        val owner = preferences.getString("owner", null) ?: return null
        val binding = preferences.getString("binding", null) ?: return null
        val device = preferences.getString("device", null) ?: return null
        return NotificationBinding(owner, binding, device, preferences.getLong("revision", 0), preferences.getLong("expires", 0))
    }
    fun activate(owner: String, jwt: String): NotificationBinding? = synchronized(lock) {
        if (!NotificationPolicy.validId(owner)) return@synchronized null
        val expires = try {
            val decoded = Base64.decode(jwt.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decoded, Charsets.UTF_8)).getLong("exp")
        } catch (_: Exception) { return@synchronized null }
        if (expires <= System.currentTimeMillis() / 1_000) return@synchronized null
        val digest = MessageDigest.getInstance("SHA-256").digest(jwt.toByteArray()).joinToString("") { "%02x".format(it) }
        val old = active()
        if (old?.owner == owner && preferences.getString("session", null) == digest) return@synchronized old
        val entry = NotificationBinding(owner, UUID.randomUUID().toString(), old?.device ?: preferences.getString("device", null) ?: UUID.randomUUID().toString(),
            preferences.getLong("revision", 0) + 1, expires)
        preferences.edit().putString("owner", owner).putString("binding", entry.binding).putString("device", entry.device)
            .putString("session", digest).putLong("revision", entry.revision).putLong("expires", expires).remove("registered").apply()
        entry
    }
    fun clear() = synchronized(lock) {
        preferences.edit().remove("owner").remove("binding").remove("expires").remove("session").remove("registered")
            .putLong("revision", preferences.getLong("revision", 0) + 1).apply()
    }
    fun token() = preferences.getString("fcm_token", null)
    fun setToken(value: String) { preferences.edit().putString("fcm_token", value).remove("registered").apply() }
    fun registered(binding: NotificationBinding, token: String) = synchronized(lock) {
        if (active() == binding && this.token() == token) preferences.edit().putLong("registered", System.currentTimeMillis()).apply()
    }
    fun needsRegistration() = System.currentTimeMillis() - preferences.getLong("registered", 0) > 12 * 60 * 60 * 1_000L
    fun askedPermission() = preferences.getBoolean("asked_permission", false)
    fun notePermissionAsked() { preferences.edit().putBoolean("asked_permission", true).apply() }
    fun wasDelivered(id:String) = preferences.getStringSet("delivered",emptySet()).orEmpty().contains(id)
    fun noteDelivered(id:String) = synchronized(lock) {
        val ids=preferences.getStringSet("delivered",emptySet()).orEmpty().toMutableSet()
        ids+=id
        // Object IDs sort chronologically; keep a bounded history across process restarts.
        preferences.edit().putStringSet("delivered",ids.sortedDescending().take(256).toSet()).apply()
    }
    companion object { private val lock = Any() }
}
