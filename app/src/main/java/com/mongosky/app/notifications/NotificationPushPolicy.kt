package com.mongosky.app.notifications

internal data class NotificationPush(val id: String, val ownerId: String, val bindingId: String, val actorId: String?, val body: String)
internal object NotificationPushPolicy {
    fun parse(data: Map<String, String>): NotificationPush? {
        val id = data["notification_id"] ?: return null
        val owner = data["recipient_id"] ?: return null
        val binding = data["binding_id"] ?: return null
        val body = data["body"]?.trim() ?: return null
        if (!NotificationPolicy.validId(id) || !NotificationPolicy.validId(owner) || binding.length != 36 || body.isEmpty() || body.length > 1_000) return null
        return NotificationPush(id, owner, binding, data["actor_id"]?.takeIf(NotificationPolicy::validId), body)
    }
    fun allowed(push: NotificationPush, owner: String?, binding: String?, expiresAt: Long, nowSeconds: Long) =
        push.ownerId == owner && push.bindingId == binding && expiresAt > nowSeconds
}
