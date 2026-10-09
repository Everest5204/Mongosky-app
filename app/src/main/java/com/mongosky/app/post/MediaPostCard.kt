package com.mongosky.app.post

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.reactions.PostReactionState
import java.time.Instant

@Composable
fun MediaPostCard(post: MediaPost, now: Instant, reaction: PostReactionState, commentsCount: Long,
    enabled: Boolean, actions: PostCardActions, onOpenPhotos: (List<FeedMedia>, Int) -> Unit, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null,
    previewUrl: (FeedMedia) -> String = { it.url },
    singlePhoto: (@Composable (FeedMedia, () -> Unit) -> Unit)? = null) {
    val photos = remember(post.media) { post.media.filter { it.type != FeedMediaType.VIDEO }.sortedBy { it.order } }
    val action = when (post.type) {
        MediaPostType.PROFILE_PHOTO_UPDATE -> "updated profile picture"
        MediaPostType.COVER_PHOTO_UPDATE -> "updated cover photo"
        else -> "shared a post"
    }
    Column(modifier.fillMaxWidth().background(Color.White)) {
        if (header != null) header() else PostHeader(post.author, post.createdAt, now, actions, action)
        if (post.type != MediaPostType.PROFILE_PHOTO_UPDATE && post.type != MediaPostType.COVER_PHOTO_UPDATE) {
            PostCaption(post.caption)
        }
        if (photos.size == 1 && singlePhoto != null) singlePhoto(photos[0]) { onOpenPhotos(photos, 0) }
        else if (photos.isNotEmpty()) MediaGrid(photos, enabled, previewUrl) { onOpenPhotos(photos, it) }
        if (footer != null) footer() else PostFooter(reaction, commentsCount, enabled, actions)
    }
}

@Composable
private fun MediaGrid(photos: List<FeedMedia>, enabled: Boolean, previewUrl: (FeedMedia) -> String, onOpen: (Int) -> Unit) {
    when (photos.size) {
        1 -> MediaTile(photos[0], 0, enabled, onOpen, Modifier.fillMaxWidth().aspectRatio(1f), previewUrl, fit = true)
        2 -> Row(Modifier.fillMaxWidth().aspectRatio(1.1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl)
            MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl)
        }
        3 -> Row(Modifier.fillMaxWidth().aspectRatio(1.08f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1.35f).fillMaxHeight(), previewUrl)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxWidth(), previewUrl)
                MediaTile(photos[2], 2, enabled, onOpen, Modifier.weight(1f).fillMaxWidth(), previewUrl)
            }
        }
        else -> Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[0], 0, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl)
                MediaTile(photos[1], 1, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MediaTile(photos[2], 2, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl)
                MediaTile(photos[3], 3, enabled, onOpen, Modifier.weight(1f).fillMaxHeight(), previewUrl, more = photos.size - 4)
            }
        }
    }
}

@Composable
private fun MediaTile(photo: FeedMedia, index: Int, enabled: Boolean, onOpen: (Int) -> Unit,
    modifier: Modifier, previewUrl: (FeedMedia) -> String, fit: Boolean = false, more: Int = 0) {
    val context = LocalContext.current
    val preview = remember(photo.url, previewUrl) { previewUrl(photo) }
    var fallback by remember(photo.url, preview) { mutableStateOf(false) }
    val url = if (fallback) photo.url else preview
    val request = remember(context, url) { ImageRequest.Builder(context.applicationContext).data(url).crossfade(false).build() }
    Box(modifier.background(FeedColors.soft).clickable(enabled = enabled) { onOpen(index) }, contentAlignment = Alignment.Center) {
        Icon(FeedIcons.image, null, tint = FeedColors.line, modifier = Modifier.size(36.dp))
        AsyncImage(model = request, contentDescription = "Post photo ${index + 1}", modifier = Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            onError = { if (url != photo.url) fallback = true })
        if (more > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Text("+$more", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}
