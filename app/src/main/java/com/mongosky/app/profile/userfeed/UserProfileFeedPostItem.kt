package com.mongosky.app.profile.userfeed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mongosky.app.home.HomeFeedPhoto
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.home.HomePostHeader
import com.mongosky.app.home.homeFeedImageWidth
import com.mongosky.app.home.homeFeedPreviewUrl
import com.mongosky.app.post.*
import com.mongosky.app.profile.ProfileGalleryAsset
import com.mongosky.app.profile.ProfileGalleryTile
import com.mongosky.app.profile.UserProfileState
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionSummary
import java.time.Instant

/** Home's actual header, text/photo cards and footer; no separately styled public-profile copy. */
@Composable
internal fun UserProfileFeedPostItem(
    raw: FeedPost, state: UserProfileState, now: Instant, actionsModel: HomeFeedViewModel,
    feed: UserProfileFeedController, enabled: Boolean, reactionRefreshVersion: Long,
    onPhotos: (List<FeedMedia>, Int) -> Unit, onVideo: (ProfileGalleryAsset) -> Unit,
    onCopy: (HomePost) -> Unit, onShare: (HomePost) -> Unit, onFollow: () -> Unit
) {
    val profile = state.profile
    val post = remember(raw, profile?.id, profile?.author, profile?.isSelf, profile?.isFollowing, state.viewerId) {
        userProfileFeedPost(raw, profile, state.viewerId)
    }
    LaunchedEffect(post.key, actionsModel.uiState.refreshVersion, reactionRefreshVersion) { actionsModel.ensureReaction(post) }
    val actions = remember(actionsModel, post, onShare, onCopy) {
        PostCardActions(onReact = { actionsModel.react(post, it) }, onComments = { actionsModel.openComments(post) },
            onShare = { onShare(post) }, onCopyLink = { onCopy(post) },
            onReactionPeople = { actionsModel.openReactionPeople(post) }, onRetryReaction = { actionsModel.ensureReaction(post) })
    }
    val fallback = remember(post.likesCount) { PostReactionState(ReactionSummary(total = post.likesCount)) }
    val header: @Composable () -> Unit = {
        HomePostHeader(post, now, feed.controls, feed.images, enabled && feed.ready,
            authorStateOverride = userProfileFeedAuthor(state, post.author.id), onFollow = onFollow,
            onMenu = { feed.openMenu(post) })
    }
    val footer: @Composable () -> Unit = { UserProfileFeedFooter(post, actionsModel, enabled, actions) }
    when (post) {
        is HomePost.Text -> TextPostCard(post.post, now, fallback, post.commentsCount, enabled, actions,
            header = header, footer = footer)
        is HomePost.Media -> {
            val video = remember(post) { post.post.media.firstOrNull { it.type == FeedMediaType.VIDEO } }
            if (video == null) {
                val width = homeFeedImageWidth(if (post.post.media.size > 1) 2 else 1)
                val preview = remember(width) { { media: FeedMedia -> homeFeedPreviewUrl(media.url, width) } }
                MediaPostCard(post.post, now, fallback, post.commentsCount, enabled, actions, onPhotos,
                    header = header, footer = footer, previewUrl = preview,
                    singlePhoto = { photo, open -> HomeFeedPhoto(photo, feed.images, enabled, open) })
            } else Column(Modifier.fillMaxWidth().background(Color.White)) {
                header()
                PostCaption(post.post.caption)
                val asset = remember(video, post) { ProfileGalleryAsset(post.key, post.id, video, post.post.caption) }
                ProfileGalleryTile(asset, { onVideo(asset) }, Modifier.fillMaxWidth().height(430.dp))
                footer()
            }
        }
    }
    FeedDivider()
}

@Composable
private fun UserProfileFeedFooter(post: HomePost, actionsModel: HomeFeedViewModel, enabled: Boolean, actions: PostCardActions) {
    val reaction = actionsModel.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val comments = actionsModel.commentCounts[post.key] ?: post.commentsCount
    PostFooter(reaction, comments, enabled, actions)
}
