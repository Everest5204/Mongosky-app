package com.mongosky.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.post.FeedMedia
import java.net.URI
import kotlin.math.roundToInt

/** Keep original URLs for the viewer, editor and downloads. Only Home previews are resized. */
internal fun homeFeedPreviewUrl(url: String, width: Int = 1080): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.scheme != "https" || uri.host != "res.cloudinary.com" || uri.userInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null) return url
    val marker = "/image/upload/"
    val path = uri.rawPath.orEmpty()
    val index = path.indexOf(marker)
    if (index < 0) return url
    val split = index + marker.length
    val asset = path.substring(split)
    if (asset.isBlank() || asset.startsWith("s--")) return url
    return "https://${uri.rawAuthority}${path.substring(0, split)}c_limit,w_${homeFeedPreviewWidth(width)},f_auto,q_auto/$asset"
}

@Composable
internal fun homeFeedImageWidth(columns: Int = 1): Int {
    val width = LocalConfiguration.current.screenWidthDp.coerceAtMost(680)
    val density = LocalDensity.current.density
    return homeFeedPreviewWidth((width * density / columns.coerceAtLeast(1)).roundToInt())
}

/** Home supplies this slot; the shared Profile card's image presentation stays unchanged. */
@Composable
internal fun HomeFeedPhoto(photo: FeedMedia, images: HomeFeedImageState, enabled: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val width = homeFeedImageWidth()
    val generation = images.generation
    val aspect by remember(images, generation, photo.url) { images.aspect(photo.url) }
    val preview = remember(photo.url, width) { homeFeedPreviewUrl(photo.url, width) }
    var fallback by remember(photo.url, preview) { mutableStateOf(false) }
    val url = if (fallback) photo.url else preview
    // Explicit, bounded decode dimensions stay stable when the displayed aspect changes.
    // FIT here controls bitmap allocation; the composable crops to fill the entire card.
    val request = remember(context, url, width) {
        ImageRequest.Builder(context).data(url).size(width, (width / 0.8f).roundToInt())
            .scale(Scale.FIT).precision(Precision.INEXACT).crossfade(false).build()
    }
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(FeedColors.soft)
        .clickable(enabled = enabled, onClick = onOpen), contentAlignment = Alignment.Center) {
        Icon(FeedIcons.image, null, tint = FeedColors.line, modifier = Modifier.size(36.dp))
        AsyncImage(model = request, contentDescription = "Post photo", modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded ->
                val drawable = loaded.result.drawable
                images.resolved(photo.url, drawable.intrinsicWidth, drawable.intrinsicHeight, generation)
            },
            onError = { if (url != photo.url) fallback = true })
    }
}
