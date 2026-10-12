package com.mongosky.app.comments

/** Flatten only expanded threads: every visible row stays in one native LazyColumn. */
internal sealed interface CommentListEntry {
    val key: String
    data class Row(val comment: PostComment, val depth: Int) : CommentListEntry {
        override val key get() = "comment:${comment.id}"
    }
    data class Footer(val parentId: String, val depth: Int, val thread: CommentThread) : CommentListEntry {
        override val key get() = "thread:$parentId"
    }
}

internal fun commentEntries(root: CommentThread, replies: Map<String, CommentThread>, expanded: Set<String>): List<CommentListEntry> {
    val result = ArrayList<CommentListEntry>()
    val pending = ArrayDeque<CommentListEntry>()
    val visited = HashSet<String>()
    root.comments.asReversed().forEach { pending.addLast(CommentListEntry.Row(it, 0)) }
    while (pending.isNotEmpty()) {
        when (val entry = pending.removeLast()) {
            is CommentListEntry.Footer -> result.add(entry)
            is CommentListEntry.Row -> {
                if (!visited.add(entry.comment.id) || entry.comment.isDeleted) continue
                result.add(entry)
                if (entry.comment.id in expanded) {
                    val thread = replies[entry.comment.id] ?: CommentThread()
                    pending.addLast(CommentListEntry.Footer(entry.comment.id, entry.depth + 1, thread))
                    thread.comments.asReversed().forEach { pending.addLast(CommentListEntry.Row(it, entry.depth + 1)) }
                }
            }
        }
    }
    return result
}
