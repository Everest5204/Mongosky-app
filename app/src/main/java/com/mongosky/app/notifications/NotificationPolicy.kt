package com.mongosky.app.notifications

internal object NotificationPolicy {
    private val objectId = Regex("[a-f0-9]{24}")
    fun validId(id: String) = objectId.matches(id) && id != "0".repeat(24)
    fun badge(count: Long) = if (count > 9) "9+" else count.coerceAtLeast(0).toString()
    fun keepRead(item: NotificationItem, previous: NotificationItem?) =
        if (previous?.isRead == true && !item.isRead) item.copy(isRead = true, readAt = previous.readAt) else item
    fun firstPage(page: NotificationPage, old: List<NotificationItem>, preserveTail: Boolean): List<NotificationItem> {
        val previous = old.associateBy { it.id }
        val head = page.items.map { keepRead(it, previous[it.id]) }
        val boundary = old.indexOfFirst { it.id == head.lastOrNull()?.id }
        val ids = head.mapTo(HashSet()) { it.id }
        return if (preserveTail && page.hasMore && boundary >= 0) head + old.drop(boundary + 1).filter { it.id !in ids } else head
    }
    fun reactionEmoji(value: String?) = when (value) {
        "love" -> "❤️"; "haha" -> "😆"; "wow" -> "😮"; "sad" -> "😢"; "angry" -> "😡"; else -> null
    }
}
