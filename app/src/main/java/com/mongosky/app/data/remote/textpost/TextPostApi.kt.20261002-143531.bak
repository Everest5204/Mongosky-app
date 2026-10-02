package com.mongosky.app.data.remote.textpost

import com.mongosky.app.data.remote.FeedApi
import com.mongosky.app.data.remote.FeedApiException
import com.mongosky.app.data.remote.FeedApiFailure
import com.mongosky.app.domain.model.FeedPostType
import com.mongosky.app.domain.model.FeedSource
import com.mongosky.app.domain.model.textpost.TextFeedPage
import com.mongosky.app.domain.model.textpost.TextPost
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The text feed entry point for /api/text-posts/feed.
 * FeedApi supplies shared HTTP handling; this API returns text-only models.
 * The caller keeps the text cursor separate from the media cursor.
 */
class TextPostApi(
    private val feedApi: FeedApi = FeedApi()
) {
    suspend fun loadFeed(
        token: String,
        cursor: String? = null,
        pageSize: Int = FeedApi.DEFAULT_PAGE_SIZE
    ): TextFeedPage {
        currentCoroutineContext().ensureActive()

        val page = feedApi.loadPage(
            token = token,
            source = FeedSource.TEXT,
            cursor = cursor,
            pageSize = pageSize
        )

        val posts = ArrayList<TextPost>(page.posts.size)

        for (post in page.posts) {
            currentCoroutineContext().ensureActive()

            if (post.source != FeedSource.TEXT || post.type != FeedPostType.TEXT) {
                throw FeedApiException(
                    message = "Could not read the text feed. Please try again.",
                    failure = FeedApiFailure.INVALID_RESPONSE,
                    statusCode = 200
                )
            }

            posts.add(
                TextPost(
                    id = post.id,
                    userId = post.userId,
                    author = post.author,
                    createdAt = post.createdAt,
                    updatedAt = post.updatedAt,
                    text = post.text,
                    caption = post.caption,
                    textBackground = post.textBackground,
                    textColor = post.textColor,
                    textStyle = post.textStyle,
                    likesCount = post.likesCount,
                    commentsCount = post.commentsCount,
                    sharesCount = post.sharesCount,
                    isSelf = post.isSelf,
                    isFollowing = post.isFollowing
                )
            )
        }

        currentCoroutineContext().ensureActive()

        return TextFeedPage(
            posts = posts.toList(),
            nextCursor = page.nextCursor,
            hasMore = page.hasMore
        )
    }
}
