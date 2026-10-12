package com.mongosky.app.comments

import java.time.Duration
import java.time.Instant

/** Web textarea limit, without cutting a UTF-16 surrogate pair during paste/IME edits. */
internal object CommentPolicy {
    const val MAX_TEXT = 2000
    const val UPDATE_INTERVAL_MILLIS = 8_000L
    private val objectId = Regex("[a-fA-F0-9]{24}")
    fun validId(value: String) = objectId.matches(value) && value.any { it != '0' }
    fun limitDraft(value: String): String {
        if (value.length <= MAX_TEXT) return value
        val end = if (value[MAX_TEXT - 1].isHighSurrogate()) MAX_TEXT - 1 else MAX_TEXT
        return value.substring(0, end)
    }
    fun canSend(value: String) = value.isNotBlank() && value.length <= MAX_TEXT
    fun requestText(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_TEXT) {
            "Write a comment of up to 2,000 characters."
        }
    }

    fun append(thread: CommentThread, page: CommentPage): CommentThread {
        val rows = LinkedHashMap<String, PostComment>()
        thread.comments.forEach { rows[it.id] = it }
        page.comments.forEach { rows[it.id] = it }
        return thread.copy(comments = rows.values.toList(), cursor = page.nextCursor, hasMore = page.hasMore,
            seenCursors = thread.seenCursors + listOfNotNull(page.nextCursor),
            headIds = if (thread.initialized) thread.headIds else page.comments.map { it.id },
            initialized = true, loading = false, error = null)
    }

    /** Refresh the head without discarding older pages or moving a keyed scroll anchor. */
    fun refresh(thread: CommentThread, page: CommentPage, protectedIds: Set<String> = emptySet()): CommentThread {
        val incoming = page.comments.associateBy { it.id }
        val boundary = page.comments.lastOrNull()
        val retained = thread.comments.filter { row ->
            row.id !in incoming && (row.id in protectedIds || (page.hasMore && boundary != null &&
                (row.createdAt < boundary.createdAt || (row.createdAt == boundary.createdAt && row.id < boundary.id))))
        }
        val head = page.comments.map { row -> thread.comments.firstOrNull { it.id == row.id && it.id in protectedIds } ?: row }
        val ids = page.comments.map { it.id }
        val previousIds = thread.comments.mapTo(HashSet()) { it.id }
        val resetCursor = !thread.initialized || (ids != thread.headIds && ids.any { it !in previousIds })
        val rows = (head + retained).distinctBy { it.id }.sortedWith(
            compareByDescending<PostComment> { it.createdAt }.thenByDescending { it.id })
        return thread.copy(comments = rows, headIds = ids, initialized = true, refreshing = false,
            cursor = if (resetCursor) page.nextCursor else if (page.hasMore) thread.cursor else null,
            hasMore = if (resetCursor) page.hasMore else page.hasMore && thread.hasMore,
            seenCursors = if (resetCursor) setOfNotNull(page.nextCursor) else thread.seenCursors,
            loading = false, error = null)
    }

    fun timeAgo(created: Instant, now: Instant): String {
        val seconds = Duration.between(created, now).seconds.coerceAtLeast(0)
        val (count, unit) = when {
            seconds >= 2_592_000 -> seconds / 2_592_000 to "month"
            seconds >= 86_400 -> seconds / 86_400 to "day"
            seconds >= 3_600 -> seconds / 3_600 to "hour"
            seconds >= 60 -> seconds / 60 to "minute"
            else -> return "Just now"
        }
        return "$count $unit${if (count == 1L) "" else "s"} ago"
    }
}
