package com.mongosky.app.data.remote.mediapost

import com.mongosky.app.data.remote.FeedApi
import com.mongosky.app.data.remote.FeedApiException
import com.mongosky.app.data.remote.FeedApiFailure
import com.mongosky.app.domain.model.FeedPostType
import com.mongosky.app.domain.model.FeedSource
import com.mongosky.app.domain.model.mediapost.MediaFeedPage
import com.mongosky.app.domain.model.mediapost.MediaPost
import com.mongosky.app.domain.model.mediapost.MediaPostType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The media feed entry point for /api/posts/feed.
 * FeedApi supplies shared HTTP handling and legacy-media normalization.
 * The caller keeps the media cursor separate from the text cursor.
 * The backend currently excludes videos from this Home feed.
 */
class MediaPostApi(
    private val feedApi: FeedApi = FeedApi()
) {
    suspend fun loadFeed(
        token: String,
        cursor: String? = null,
        pageSize: Int = FeedApi.DEFAULT_PAGE_SIZE
    ): MediaFeedPage {
        currentCoroutineContext().ensureActive()

        val page = feedApi.loadPage(
            token = token,
            source = FeedSource.MEDIA,
            cursor = cursor,
            pageSize = pageSize
        )

        val posts = ArrayList<MediaPost>(page.posts.size)

        for (post in page.posts) {
            currentCoroutineContext().ensureActive()

            if (post.source != FeedSource.MEDIA) {
                throw invalidResponse()
            }

            val type = when (post.type) {
                FeedPostType.IMAGE -> MediaPostType.IMAGE
                FeedPostType.VIDEO -> MediaPostType.VIDEO
                FeedPostType.PROFILE_PHOTO_UPDATE -> MediaPostType.PROFILE_PHOTO_UPDATE
                FeedPostType.COVER_PHOTO_UPDATE -> MediaPostType.COVER_PHOTO_UPDATE
                FeedPostType.UNKNOWN -> MediaPostType.UNKNOWN
                FeedPostType.TEXT -> throw invalidResponse()
            }

            posts.add(
                MediaPost(
                    id = post.id,
                    userId = post.userId,
                    author = post.author,
                    createdAt = post.createdAt,
                    updatedAt = post.updatedAt,
                    type = type,
                    caption = post.caption,
                    media = post.media,
                    likesCount = post.likesCount,
                    commentsCount = post.commentsCount,
                    sharesCount = post.sharesCount,
                    isSelf = post.isSelf,
                    isFollowing = post.isFollowing
                )
            )
        }

        currentCoroutineContext().ensureActive()

        return MediaFeedPage(
            posts = posts.toList(),
            nextCursor = page.nextCursor,
            hasMore = page.hasMore
        )
    }

    private fun invalidResponse(): FeedApiException = FeedApiException(
        message = "Could not read the media feed. Please try again.",
        failure = FeedApiFailure.INVALID_RESPONSE,
        statusCode = 200
    )
}
