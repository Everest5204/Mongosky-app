@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mongosky.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mongosky.app.domain.model.FeedMedia
import com.mongosky.app.presentation.home.cards.FeedColors
import com.mongosky.app.presentation.home.cards.FeedDivider
import com.mongosky.app.presentation.home.cards.FeedIcons
import com.mongosky.app.presentation.home.cards.PostAvatar

internal data class PhotoViewerState(val photos: List<FeedMedia>, val index: Int)

@Composable
internal fun PhotoViewer(state: PhotoViewerState, onDismiss: () -> Unit) {
    if (state.photos.isEmpty()) return
    val pager = rememberPagerState(initialPage = state.index.coerceIn(state.photos.indices)) { state.photos.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(FeedIcons.close, "Close photos", tint = FeedColors.text) }
                Text("${pager.currentPage + 1} / ${state.photos.size}", color = FeedColors.muted, fontSize = 14.sp)
            }
            FeedDivider()
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0,
                key = { state.photos[it].url + ":" + it }) { index ->
                var scale by remember(index) { mutableStateOf(1f) }
                var offset by remember(index) { mutableStateOf(Offset.Zero) }
                var size by remember(index) { mutableStateOf(IntSize.Zero) }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    val maxX = size.width * (scale - 1f) / 2f
                    val maxY = size.height * (scale - 1f) / 2f
                    offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                    if (scale <= 1f) offset = Offset.Zero
                }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
                    .transformable(state = transform, canPan = { scale > 1f })
                    .pointerInput(index) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = Offset.Zero }) },
                    contentAlignment = Alignment.Center) {
                    Icon(FeedIcons.image, null, tint = FeedColors.line, modifier = Modifier.size(40.dp))
                    AsyncImage(model = state.photos[index].url, contentDescription = "Photo ${index + 1} of ${state.photos.size}",
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                        contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
internal fun ReactionPeopleSheet(state: ReactionPeopleState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(FeedIcons.close, "Close reactions", tint = FeedColors.text) }
                Text("Reactions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FeedColors.text)
            }
            FeedDivider()
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FeedSpinner() }
                state.error != null -> FeedNotice(state.error, "Close", onDismiss)
                state.people.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reactions yet", color = FeedColors.muted, fontSize = 14.sp)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.people, key = { it.id }, contentType = { "reactor" }) { person ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PostAvatar(person.author, Modifier.size(40.dp))
                            Text(person.author.displayName.ifBlank { "Mongosky User" }, color = FeedColors.text,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(person.reaction.emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
