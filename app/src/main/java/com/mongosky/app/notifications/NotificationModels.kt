package com.mongosky.app.notifications

import com.mongosky.app.post.FeedAuthor
import java.time.Instant

internal enum class NotificationKind(val wire: String) {
    REACTION("post_reaction"), COMMENT("post_comment"), REPLY("comment_reply"),
    LOVE("comment_love"), FOLLOW("follow"), FOLLOW_BACK("follow_back")
}

internal data class NotificationItem(
    val id: String, val kind: NotificationKind, val actorId: String, val actor: FeedAuthor?,
    val isRead: Boolean, val createdAt: Instant, val updatedAt: Instant,
    val readAt: Instant? = null, val postId: String? = null, val commentId: String? = null,
    val parentId: String? = null, val reaction: String? = null
) {
    val actorName get() = actor?.displayName?.takeIf(String::isNotBlank) ?: "Someone"
    val actionText get() = when (kind) {
        NotificationKind.FOLLOW -> "started following you"
        NotificationKind.FOLLOW_BACK -> "followed you back"
        NotificationKind.REACTION -> if (reaction == "love") "loved your post" else "reacted to your post"
        NotificationKind.COMMENT -> "commented on your post"
        NotificationKind.REPLY -> "replied to your comment"
        NotificationKind.LOVE -> if (parentId == null) "loved your comment" else "loved your reply"
    }
}

internal data class NotificationPage(val items: List<NotificationItem>, val cursor: String?, val hasMore: Boolean, val unreadCount: Long)
internal data class NotificationReadResult(val modifiedCount: Long, val unreadCount: Long)
internal enum class NotificationConnection { CLOSED, CONNECTING, CONNECTED, RECONNECTING, ERROR, UNAUTHORIZED }
internal sealed interface NotificationFrame {
    data class Snapshot(val page: NotificationPage) : NotificationFrame
    data class Connection(val status: NotificationConnection) : NotificationFrame
}
internal data class NotificationsState(
    val items: List<NotificationItem> = emptyList(), val unreadCount: Long = 0,
    val loaded: Boolean = false, val ready: Boolean = false, val loading: Boolean = false, val loadingMore: Boolean = false,
    val hasMore: Boolean = false, val pendingReadIds: Set<String> = emptySet(),
    val failedReadIds: Set<String> = emptySet(),
    val connection: NotificationConnection = NotificationConnection.CLOSED,
    val error: String? = null, val moreError: String? = null, val readError: String? = null,
    val verifiedAuthors: Set<String> = emptySet()
)
