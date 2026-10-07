package com.mongosky.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.profile.ProfileGalleryAsset

/** One row in the parent's lazy list; no nested scrolling or eager grid decoding. */
@Composable
internal fun OwnProfileGalleryRow(assets: List<ProfileGalleryAsset>, columns: Int, onOpen: (ProfileGalleryAsset) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(columns) { index ->
            val asset = assets.getOrNull(index)
            val video = assets.firstOrNull()?.media?.type == FeedMediaType.VIDEO
            val modifier = Modifier.weight(1f).aspectRatio(if (video) 9f / 16f else 1f)
            if (asset == null) Spacer(modifier) else ProfileGalleryTile(asset, { onOpen(asset) }, modifier)
        }
    }
}

@Composable
internal fun ProfileGalleryTile(asset: ProfileGalleryAsset, onOpen: () -> Unit, modifier: Modifier) {
    val video = asset.media.type == FeedMediaType.VIDEO
    Box(modifier.background(OwnProfileColors.soft).clickable(role = Role.Button, onClick = onOpen), contentAlignment = Alignment.Center) {
        Icon(if (video) OwnProfileIcons.play else FeedIcons.image, null, tint = OwnProfileColors.muted, modifier = Modifier.size(30.dp))
        asset.thumbnail?.let { AsyncImage(it, if (video) "Video preview" else "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (video) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(OwnProfileIcons.play, "Play video", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            asset.caption.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, fontSize = 12.sp, maxLines = 2,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(8.dp))
            }
        }
    }
}
