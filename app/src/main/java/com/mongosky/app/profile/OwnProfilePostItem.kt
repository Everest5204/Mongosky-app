package com.mongosky.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedDivider
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedPostType
import com.mongosky.app.post.FeedSource
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.MediaPostCard
import com.mongosky.app.post.PostCaption
import com.mongosky.app.post.PostCardActions
import com.mongosky.app.post.PostFooter
import com.mongosky.app.post.PostHeader
import com.mongosky.app.post.TextPostCard
import com.mongosky.app.profile.OwnProfile
import com.mongosky.app.profile.ProfileGalleryAsset
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionSummary
import com.mongosky.app.textpost.TextPost
import java.time.Instant

@Composable
internal fun OwnProfilePostItem(
    raw: FeedPost, owner: OwnProfile?, now: Instant, actionsModel: HomeFeedViewModel,
    enabled: Boolean, onPhotos: (List<FeedMedia>, Int) -> Unit, onVideo: (ProfileGalleryAsset) -> Unit,
    onCopy: (HomePost) -> Unit, onShare: (HomePost) -> Unit,
    isSelf: Boolean = true, reactionRefreshVersion: Long = 0
) {
    val post = remember(raw, owner, isSelf) { raw.toHomePost(owner?.author ?: raw.author, isSelf) }
    LaunchedEffect(post.key, actionsModel.uiState.refreshVersion, reactionRefreshVersion) { actionsModel.ensureReaction(post) }
    val reaction = actionsModel.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val comments = actionsModel.commentCounts[post.key] ?: post.commentsCount
    val actions = PostCardActions(onReact = { actionsModel.react(post, it) }, onComments = { actionsModel.openComments(post) },
        onShare = { onShare(post) }, onCopyLink = { onCopy(post) }, onReactionPeople = { actionsModel.openReactionPeople(post) },
        onRetryReaction = { actionsModel.ensureReaction(post) })
    when (post) {
        is HomePost.Text -> TextPostCard(post.post, now, reaction, comments, enabled, actions)
        is HomePost.Media -> {
            val video = remember(post) { post.post.media.firstOrNull { it.type == FeedMediaType.VIDEO } }
            if (video == null) MediaPostCard(post.post, now, reaction, comments, enabled, actions, onPhotos)
            else Column(Modifier.fillMaxWidth().background(Color.White)) {
                PostHeader(post.author, post.createdAt, now, actions)
                PostCaption(post.post.caption)
                val asset = remember(video, post) { ProfileGalleryAsset(post.key, post.id, video, post.post.caption) }
                ProfileGalleryTile(asset, { onVideo(asset) }, Modifier.fillMaxWidth().height(430.dp))
                PostFooter(reaction, comments, enabled, actions)
            }
        }
    }
    FeedDivider()
}

internal fun FeedPost.toHomePost(author: FeedAuthor, isSelf: Boolean): HomePost = if (source == FeedSource.TEXT) {
    HomePost.Text(TextPost(id, userId, author, createdAt, updatedAt, text, caption, textBackground, textColor, textStyle,
        likesCount, commentsCount, sharesCount, isSelf = isSelf))
} else {
    val mediaType = when (type) {
        FeedPostType.IMAGE -> MediaPostType.IMAGE
        FeedPostType.VIDEO -> MediaPostType.VIDEO
        FeedPostType.PROFILE_PHOTO_UPDATE -> MediaPostType.PROFILE_PHOTO_UPDATE
        FeedPostType.COVER_PHOTO_UPDATE -> MediaPostType.COVER_PHOTO_UPDATE
        else -> MediaPostType.UNKNOWN
    }
    HomePost.Media(MediaPost(id, userId, author, createdAt, updatedAt, mediaType, caption, media,
        likesCount, commentsCount, sharesCount, isSelf = isSelf))
}
