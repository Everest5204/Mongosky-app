package com.mongosky.app.presentation.home.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.mongosky.app.domain.model.FeedMedia
import com.mongosky.app.domain.model.FeedMediaType
import com.mongosky.app.domain.model.mediapost.MediaPost
import com.mongosky.app.domain.model.mediapost.MediaPostType
import com.mongosky.app.presentation.home.PostReactionState
import java.time.Instant

@Composable
fun MediaPostCard(post: MediaPost, now: Instant, reaction: PostReactionState, commentsCount: Long,
    enabled: Boolean, actions: PostCardActions, onOpenPhotos: (List<FeedMedia>, Int) -> Unit, modifier: Modifier = Modifier) {
    val photos = remember(post.media) { post.media.filter { it.type != FeedMediaType.VIDEO }.sortedBy { it.order } }
    val action = when (post.type) {
        MediaPostType.PROFILE_PHOTO_UPDATE -> "updated profile picture"
        MediaPostType.COVER_PHOTO_UPDATE -> "updated cover photo"
        else -> "shared a post"
    }
    Column(modifier.fillMaxWidth().background(Color.White)) {
        PostHeader(post.author, post.createdAt, now, actions, action)
        PostCaption(post.caption)
        if (photos.isNotEmpty()) MediaGrid(photos, enabled) { onOpenPhotos(photos, it) }
        PostFooter(reaction, commentsCount, enabled, actions)
    }
}

@Composable
private fun MediaGrid(photos: List<FeedMedia>, enabled: Boolean, onOpen: (Int) -> Unit) {
    when (photos.size) {
        1 -> MediaTile(photos[0], 0, enabled, onOpen, Modifier.fillMaxWidth().aspectRatio(1f), fit = true)
        2 -> Row(Modifier.fillMaxWidth().aspectRatio(1.1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1f).fillMaxHeight())
            MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxHeight())
        }
        3 -> Row(Modifier.fillMaxWidth().aspectRatio(1.08f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1.35f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxWidth())
                MediaTile(photos[2], 2, enabled, onOpen, Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1f).fillMaxHeight())
                MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[2], 2, enabled, onOpen, Modifier.weight(1f).fillMaxHeight())
                MediaTile(photos[3], 3, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), more = photos.size - 4)
            }
        }
    }
}

@Composable
private fun MediaTile(photo: FeedMedia, index: Int, enabled: Boolean, onOpen: (Int) -> Unit,
    modifier: Modifier, fit: Boolean = false, more: Int = 0) {
    Box(modifier.background(FeedColors.soft).clickable(enabled = enabled) { onOpen(index) }, contentAlignment = Alignment.Center) {
        Icon(FeedIcons.image, null, tint = FeedColors.line, modifier = Modifier.size(36.dp))
        AsyncImage(model = photo.url, contentDescription = "Post photo ${index + 1}", modifier = Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
        if (more > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Text("+$more", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}
