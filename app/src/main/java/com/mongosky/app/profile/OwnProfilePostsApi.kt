package com.mongosky.app.profile

import com.mongosky.app.post.FeedApi
import com.mongosky.app.post.FeedPage
import com.mongosky.app.post.FeedPostType
import com.mongosky.app.post.FeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** /posts/me is mixed; text content comes from its complete text-post endpoint. */
class OwnProfilePostsApi(
    private val http: OwnProfileHttp = OwnProfileHttp(),
    private val parser: FeedApi = FeedApi()
) {
    suspend fun load(token: String, ownerId: String, source: FeedSource, cursor: String?): FeedPage =
        withContext(Dispatchers.Default) {
            require(cursor == null || cursor.length <= 2048)
            val path = if (source == FeedSource.MEDIA) "posts/me" else "text-posts/me"
            val query = buildMap {
                put("limit", "20")
                put("t", System.currentTimeMillis().toString())
                if (!cursor.isNullOrBlank()) put("cursor", cursor)
            }
            val page = parser.parsePage(http.text(token, path, query = query), source, cursor)
            if (page.posts.any { it.userId != ownerId ||
                    (source == FeedSource.TEXT && it.type != FeedPostType.TEXT) }) {
                throw OwnProfileException("Could not read your posts. Refresh your profile.")
            }
            // Both backend services use the same posts collection. Keep the raw
            // cursor even if this page contains only text; older media can follow.
            val relevant = if (source == FeedSource.MEDIA) page.posts.filter { it.type != FeedPostType.TEXT } else page.posts
            // The own-post backend uses a nil viewer when populating is_self.
            page.copy(posts = relevant.map { it.copy(isSelf = true, isFollowing = false) })
        }
}
