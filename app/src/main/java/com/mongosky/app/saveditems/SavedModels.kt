package com.mongosky.app.saveditems

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.HomePost
import com.mongosky.app.reels.Reel
import java.time.Instant

internal enum class SavedKind(val wire: String,val label: String) { ALL("all","All"),TEXT("text","Text"),PHOTOS("photos","Photos"),VIDEOS("videos","Videos") }
internal data class SavedFilter(val kind: SavedKind = SavedKind.ALL,val search: String = "")
internal data class SavedStatus(val postId: String,val saved: Boolean,val revision: Long,val savedAt: Instant? = null)
internal data class SavedCell(val status: SavedStatus? = null,val loading: Boolean = false,val saving: Boolean = false,val error: String? = null)
internal data class SavedItem(val postId: String,val kind: SavedKind,val revision: Long,val savedAt: Instant,
    val available: Boolean,val title: String,val text: String,val author: FeedAuthor? = null,val thumbnail: String? = null,val entryId: String = postId)
internal data class SavedPage(val items: List<SavedItem>,val cursor: String?,val hasMore: Boolean,val changeRevision: Long = 0)
internal data class SaveAttempt(val postId: String,val saved: Boolean,val expectedRevision: Long,val requestId: String)
internal data class SavedTarget(val postId: String,val post: HomePost? = null,val reel: Reel? = null)
internal data class SavedUndo(val postId: String,val expectedRevision: Long,val generation: Long)
internal data class SavedNotice(val message: String,val generation: Long,val undo: SavedUndo? = null)
internal data class SavedListState(val filter: SavedFilter = SavedFilter(),val searchDraft: String = "",
    val items: List<SavedItem> = emptyList(),val loading: Boolean = true,val refreshing: Boolean = false,
    val loadingMore: Boolean = false,val hasMore: Boolean = false,val error: String? = null,val pageError: String? = null,
    val updatesAvailable: Boolean = false,val targetLoading: String? = null,val target: SavedTarget? = null)
internal object SavedPolicy {
    fun validId(id: String) = id.length == 24 && id.all { it in '0'..'9' || it in 'a'..'f' } && id.any { it != '0' }
    fun ordered(a: SavedItem,b: SavedItem) = if (a.savedAt != b.savedAt) a.savedAt > b.savedAt else a.entryId > b.entryId
    fun query(text: String) = text.take(80).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}
