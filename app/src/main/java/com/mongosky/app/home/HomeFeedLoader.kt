package com.mongosky.app.home

import com.mongosky.app.mediapost.*
import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import com.mongosky.app.textpost.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pooled, genuinely cancellable HTTP; the legacy feed JSON decoder stays shared. */
internal class HomeFeedLoader(private val transport: PostTransport = PostHttp()) {
    private val decoder = FeedApi()
    private suspend fun page(token: String, source: FeedSource, cursor: String?): FeedPage = withContext(Dispatchers.IO) {
        val path = if (source == FeedSource.MEDIA) "/api/posts/feed" else "/api/text-posts/feed"
        val query = buildMap { put("limit", FeedApi.DEFAULT_PAGE_SIZE.toString()); cursor?.let { put("cursor", it) } }
        decoder.parsePage(transport.request(token, "GET", path, query).toString(), source, cursor)
    }
    suspend fun media(token: String, cursor: String?): MediaFeedPage = withContext(Dispatchers.IO) {
        val page = page(token, FeedSource.MEDIA, cursor)
        MediaFeedPage(page.posts.map { (it.toHomePost() as? HomePost.Media)?.post ?: throw invalidPostResponse() },
            page.nextCursor, page.hasMore)
    }
    suspend fun text(token: String, cursor: String?): TextFeedPage = withContext(Dispatchers.IO) {
        val page = page(token, FeedSource.TEXT, cursor)
        TextFeedPage(page.posts.map { (it.toHomePost() as? HomePost.Text)?.post ?: throw invalidPostResponse() },
            page.nextCursor, page.hasMore)
    }
    companion object { val shared = HomeFeedLoader() }
}

internal fun FeedPost.toHomePost(): HomePost = if (source == FeedSource.TEXT && type == FeedPostType.TEXT) {
    HomePost.Text(TextPost(id, userId, author, createdAt, updatedAt, text, caption, textBackground,
        textColor, textStyle, likesCount, commentsCount, sharesCount, isSelf, isFollowing))
} else if (source == FeedSource.MEDIA) {
    val mediaType = when (type) {
        FeedPostType.IMAGE -> MediaPostType.IMAGE
        FeedPostType.VIDEO -> MediaPostType.VIDEO
        FeedPostType.PROFILE_PHOTO_UPDATE -> MediaPostType.PROFILE_PHOTO_UPDATE
        FeedPostType.COVER_PHOTO_UPDATE -> MediaPostType.COVER_PHOTO_UPDATE
        FeedPostType.UNKNOWN -> MediaPostType.UNKNOWN
        else -> throw invalidPostResponse()
    }
    HomePost.Media(MediaPost(id, userId, author, createdAt, updatedAt, mediaType, caption, media,
        likesCount, commentsCount, sharesCount, isSelf, isFollowing))
} else throw invalidPostResponse()
