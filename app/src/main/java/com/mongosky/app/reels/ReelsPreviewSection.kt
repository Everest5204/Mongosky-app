package com.mongosky.app.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.mongosky.app.post.compactCount
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Home-only lifecycle: background/navigation stops requests and keeps ten cached rows. */
@Composable
fun ReelsPreviewRefreshEffect(viewModel: ReelsPreviewViewModel, enabled: Boolean, refreshVersion: Int) {
    val context = LocalContext.current
    val owner = remember(context) { context.reelsLifecycleOwner() }
    var resumed by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    DisposableEffect(viewModel, owner, enabled) {
        fun update() {
            resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
            viewModel.setForeground(enabled && resumed)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner?.lifecycle?.addObserver(observer)
        update()
        onDispose { owner?.lifecycle?.removeObserver(observer); viewModel.setForeground(false) }
    }
    LaunchedEffect(viewModel, enabled, resumed, viewModel.state.sessionExpired) {
        if (enabled && resumed && !viewModel.state.sessionExpired) while (isActive) {
            delay(ReelsViewModel.UPDATE_INTERVAL_MILLIS)
            viewModel.checkForUpdates()
        }
    }
    LaunchedEffect(viewModel, enabled, resumed, refreshVersion) {
        if (enabled && resumed) viewModel.refresh()
    }
}

@Composable
fun ReelsPreviewSection(state: ReelsPreviewState, onOpen: (Reel) -> Unit,
    onOpenAll: () -> Unit, onRetry: () -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val desktop = maxWidth > 640.dp
        val cardWidth = if (desktop) (maxWidth * 0.42f).coerceIn(144.dp, 196.dp)
            else (maxWidth * 0.43f).coerceIn(142.dp, 182.dp)
        val shape = RoundedCornerShape(if (desktop) 14.dp else 0.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Color.White)
            .then(if (desktop) Modifier.border(1.dp, Color(0x24000000), shape) else Modifier)) {
            Row(Modifier.fillMaxWidth().heightIn(min = if (desktop) 56.dp else 52.dp)
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(9.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open all Reels", onClick = onOpenAll)
                    .padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ReelsIcons.preview, null, tint = Color(0xFF171717), modifier = Modifier.size(if (desktop) 25.dp else 23.dp))
                    Text("Reels", color = Color(0xFF171717), fontSize = if (desktop) 17.sp else 16.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onOpenAll) { Text("See all", color = Color(0xFF0866FF), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                if (desktop) {
                    IconButton(onClick = { scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex - 3).coerceAtLeast(0)) } },
                        enabled = list.canScrollBackward) { Icon(ReelsIcons.back, "Previous Reels", tint = Color(0xFF171717)) }
                    IconButton(onClick = { scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex + 3)
                        .coerceAtMost((state.reels.size - 1).coerceAtLeast(0))) } }, enabled = list.canScrollForward) {
                        Icon(ReelsIcons.forward, "Next Reels", tint = Color(0xFF171717))
                    }
                }
            }
            if (state.reels.isEmpty() && state.error != null) Column(
                Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.error, color = Color(0xFF65676B), fontSize = 14.sp)
                Button(onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF))) { Text("Retry") }
            } else LazyRow(state = list, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (desktop) 9.dp else 8.dp)) {
                if (state.reels.isEmpty()) items(5, key = { "reel-skeleton-$it" }, contentType = { "skeleton" }) {
                    Box(Modifier.width(cardWidth).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFD9DCE1)))
                } else items(state.reels, key = { it.id }, contentType = { "reel-thumbnail" }) { reel ->
                    val optimized = remember(reel.videoUrl, reel.posterUrl) { reelPreviewPoster(reel) }
                    var fallback by remember(optimized, reel.posterUrl) { mutableStateOf(false) }
                    val poster = if (fallback) reel.posterUrl else optimized
                    Box(Modifier.width(cardWidth).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFF1F4), Color(0xFFEDF1F7), Color(0xFFDCE5F0))))
                        .clickable(role = Role.Button, onClickLabel = "Play reel", onClick = { onOpen(reel) })
                        .semantics { contentDescription = "Play reel by ${reel.author.displayName}. ${reel.likesCount} likes." }) {
                        Icon(ReelsIcons.preview, null, tint = Color(0xFFD81736), modifier = Modifier.align(Alignment.Center)
                            .size(54.dp).background(Color.White.copy(alpha = 0.84f), RoundedCornerShape(18.dp)).padding(12.dp))
                        if (poster != null) AsyncImage(model = poster, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            onError = { if (!fallback && poster != reel.posterUrl) fallback = true })
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.2f), 0.22f to Color.Transparent,
                            0.58f to Color.Transparent, 0.69f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.86f))))
                        Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(ReelsIcons.filledHeart, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(compactCount(reel.likesCount), color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** The same optimized still frame used by the supplied web preview; no video decode in Home. */
internal fun reelPreviewPoster(reel: Reel): String? {
    val uri = runCatching { URI(reel.videoUrl) }.getOrNull() ?: return reel.posterUrl
    val host = uri.host.orEmpty().lowercase()
    if (uri.scheme != "https" || uri.userInfo != null || (host != "cloudinary.com" && !host.endsWith(".cloudinary.com"))) return reel.posterUrl
    val marker = "/video/upload/"
    val path = uri.rawPath.orEmpty()
    val at = path.indexOf(marker)
    if (at < 0) return reel.posterUrl
    val split = at + marker.length
    val asset = path.substring(split)
    val dot = asset.lastIndexOf('.')
    if (dot <= asset.lastIndexOf('/')) return reel.posterUrl
    return "https://${uri.rawAuthority}${path.substring(0, split)}so_auto,c_fill,g_auto,w_480,h_854,f_auto,q_auto/${asset.substring(0, dot)}.jpg"
}
