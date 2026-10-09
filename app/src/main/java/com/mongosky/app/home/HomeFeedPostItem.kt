package com.mongosky.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mongosky.app.post.*
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionSummary
import java.time.Instant

/** Header and footer read their own states; reaction responses do not rebuild the media grid. */
@Composable
internal fun HomeFeedPostItem(post: HomePost, now: Instant, viewModel: HomeFeedViewModel, enabled: Boolean,
    share: (HomePost) -> Unit, copy: (HomePost) -> Unit, openPhotos: (List<FeedMedia>, Int) -> Unit) {
    val actions = remember(viewModel, post, share, copy) {
        PostCardActions(onReact = { viewModel.react(post, it) }, onComments = { viewModel.openComments(post) },
            onShare = { share(post) }, onCopyLink = { copy(post) },
            onReactionPeople = { viewModel.openReactionPeople(post) }, onRetryReaction = { viewModel.ensureReaction(post) })
    }
    val fallback = remember(post.likesCount) { PostReactionState(ReactionSummary(total = post.likesCount)) }
    when (post) {
        is HomePost.Text -> TextPostCard(post.post, now, fallback, post.commentsCount, enabled, actions,
            header = { HomePostHeader(post, now, viewModel.controls, viewModel.images, enabled) },
            footer = { HomePostFooter(post, viewModel, enabled, actions) })
        is HomePost.Media -> {
            val width = homeFeedImageWidth(if (post.post.media.size > 1) 2 else 1)
            val preview = remember(width) { { media: FeedMedia -> homeFeedPreviewUrl(media.url, width) } }
            MediaPostCard(post.post, now, fallback, post.commentsCount, enabled, actions,
                onOpenPhotos = openPhotos,
                header = { HomePostHeader(post, now, viewModel.controls, viewModel.images, enabled) },
                footer = { HomePostFooter(post, viewModel, enabled, actions) },
                previewUrl = preview,
                singlePhoto = { photo, open -> HomeFeedPhoto(photo, viewModel.images, enabled, open) })
        }
    }
}

@Composable
private fun HomePostFooter(post: HomePost, viewModel: HomeFeedViewModel, enabled: Boolean, actions: PostCardActions) {
    val reaction = viewModel.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val comments = viewModel.commentCounts[post.key] ?: post.commentsCount
    PostFooter(reaction, comments, enabled, actions)
}
