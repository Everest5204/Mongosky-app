package com.mongosky.app.myactivity

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.HomePost
import com.mongosky.app.reels.Reel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class ActivityCategory(val wire: String, val label: String) {
    ALL("all", "All"), POSTS("posts", "Posts"), REACTIONS("reactions", "Reactions"),
    COMMENTS("comments", "Comments"), CONNECTIONS("connections", "Connections")
}
internal enum class ActivityRange(val label: String, val days: Long?) {
    ALL("All time", null), WEEK("7 days", 7), MONTH("30 days", 30), QUARTER("90 days", 90)
}
internal enum class ActivityAction(val wire: String, val label: String, val confirmation: String) {
    NONE("none", "", ""),
    DELETE_POST("delete_post", "Delete post", "This post will be removed, including its comments and reactions."),
    DELETE_COMMENT("delete_comment", "Delete comment", "This comment and its replies will be removed."),
    REMOVE_REACTION("remove_reaction", "Remove reaction", "Your reaction will be removed from this post.")
}
internal data class ActivityFilter(val category: ActivityCategory = ActivityCategory.ALL,
    val range: ActivityRange = ActivityRange.ALL, val from: Instant? = null, val before: Instant? = null) {
    fun withRange(range: ActivityRange, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ActivityFilter =
        copy(range = range, from = range.days?.let { today.minusDays(it - 1).atStartOfDay(zone).toInstant() }, before = null)
}
internal data class ActivityItem(val id: String, val kind: String, val category: ActivityCategory,
    val createdAt: Instant, val title: String, val text: String, val author: FeedAuthor? = null,
    val thumbnail: String? = null, val postId: String? = null, val available: Boolean,
    val action: ActivityAction = ActivityAction.NONE) { val key get() = "$kind:$id" }
internal data class ActivityPage(val items: List<ActivityItem>, val counts: Map<ActivityCategory, Long>?,
    val cursor: String?, val hasMore: Boolean)
internal data class ActivityTarget(val item: ActivityItem, val post: HomePost? = null,
    val profile: FeedAuthor? = null, val reel: Reel? = null)
internal data class ActivityState(val filter: ActivityFilter = ActivityFilter(), val items: List<ActivityItem> = emptyList(),
    val counts: Map<ActivityCategory, Long> = emptyMap(), val loading: Boolean = true, val refreshing: Boolean = false,
    val loadingMore: Boolean = false, val hasMore: Boolean = false, val error: String? = null, val pageError: String? = null,
    val sessionExpired: Boolean = false, val updatesAvailable: Boolean = false, val targetLoading: String? = null,
    val target: ActivityTarget? = null, val confirmation: ActivityItem? = null, val saving: Boolean = false,
    val actionError: String? = null, val notice: String? = null)

internal object ActivityPolicy {
    val kinds = mapOf("post" to ActivityCategory.POSTS, "reaction" to ActivityCategory.REACTIONS,
        "comment_love" to ActivityCategory.REACTIONS, "comment" to ActivityCategory.COMMENTS, "follow" to ActivityCategory.CONNECTIONS)
    fun validId(id: String) = Regex("[a-f0-9]{24}").matches(id) && id.any { it != '0' }
    fun allowedAction(kind: String) = when (kind) {
        "post" -> ActivityAction.DELETE_POST
        "reaction" -> ActivityAction.REMOVE_REACTION
        "comment" -> ActivityAction.DELETE_COMMENT
        else -> ActivityAction.NONE
    }
    fun ordered(a: ActivityItem, b: ActivityItem): Boolean = if (a.createdAt != b.createdAt) a.createdAt > b.createdAt
        else if (a.kind != b.kind) a.kind < b.kind else a.id > b.id
}
