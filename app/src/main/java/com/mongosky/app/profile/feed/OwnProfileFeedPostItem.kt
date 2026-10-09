package com.mongosky.app.profile.feed

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
import com.mongosky.app.post.FeedDivider
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.MediaPostCard
import com.mongosky.app.post.PostCaption
import com.mongosky.app.post.PostCardActions
import com.mongosky.app.post.PostFooter
import com.mongosky.app.post.TextPostCard
import com.mongosky.app.profile.OwnProfile
import com.mongosky.app.profile.ProfileGalleryAsset
import com.mongosky.app.profile.ProfileGalleryTile
import com.mongosky.app.profile.toHomePost
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionSummary
import java.time.Instant

/** Uses the same card/header/photo slots as Home; public profile cards keep their existing renderer. */
@Composable
internal fun OwnProfileFeedPostItem(
    raw: FeedPost, owner: OwnProfile?, now: Instant, actionsModel: HomeFeedViewModel,
    feed: OwnProfileFeedController, enabled: Boolean,
    onPhotos: (List<FeedMedia>, Int) -> Unit, onVideo: (ProfileGalleryAsset) -> Unit,
    onCopy: (HomePost) -> Unit, onShare: (HomePost) -> Unit
) {
    val post = remember(raw, owner) { raw.toHomePost(owner?.author ?: raw.author, isSelf = true) }
    LaunchedEffect(post.key, actionsModel.uiState.refreshVersion) { actionsModel.ensureReaction(post) }
    val actions = remember(actionsModel, post, onShare, onCopy) {
        PostCardActions(onReact = { actionsModel.react(post, it) }, onComments = { actionsModel.openComments(post) },
            onShare = { onShare(post) }, onCopyLink = { onCopy(post) },
            onReactionPeople = { actionsModel.openReactionPeople(post) }, onRetryReaction = { actionsModel.ensureReaction(post) })
    }
    val fallback = remember(post.likesCount) { PostReactionState(ReactionSummary(total = post.likesCount)) }
    val header: @Composable () -> Unit = { HomePostHeader(post, now, feed.controls, feed.images, enabled && feed.ready) }
    val footer: @Composable () -> Unit = { OwnProfileFeedFooter(post, actionsModel, enabled, actions) }
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
private fun OwnProfileFeedFooter(post: HomePost, actionsModel: HomeFeedViewModel, enabled: Boolean, actions: PostCardActions) {
    val reaction = actionsModel.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val comments = actionsModel.commentCounts[post.key] ?: post.commentsCount
    PostFooter(reaction, comments, enabled, actions)
}
