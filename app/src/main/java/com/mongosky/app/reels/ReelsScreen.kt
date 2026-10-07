@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.mongosky.app.reels

import com.mongosky.app.profile.LocalProfileNavigator
import com.mongosky.app.profile.ProfileOpenRequest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.mongosky.app.comments.CommentsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ReelsScreen(viewModel: ReelsViewModel, userId: String?, userName: String, onBack: () -> Unit,
    onOwnProfile: () -> Unit, onSignInAgain: () -> Unit) {
    val context = LocalContext.current
    val navigateProfile = LocalProfileNavigator.current
    val owner = remember(context) { context.reelsLifecycleOwner() }
    var resumed by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    var muted by rememberSaveable(userId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state = viewModel.state
    val rows = state.reels
    val pager = rememberPagerState(initialPage = rows.indexOfFirst { it.id == state.activeId }.coerceAtLeast(0)) { rows.size }
    val current = rows.getOrNull(pager.settledPage)
    val comments = viewModel.comments.commentsState
    val playback = rememberReelPlayback(current, rows.getOrNull(pager.settledPage + 1),
        resumed && !pager.isScrollInProgress && comments == null && !state.sessionExpired, muted)

    LaunchedEffect(viewModel, userId) { viewModel.enter(userId); viewModel.setForeground(resumed) }
    DisposableEffect(viewModel, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { resumed = true; viewModel.setForeground(true) }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> { resumed = false; viewModel.setForeground(false) }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); viewModel.setForeground(false) }
    }
    LaunchedEffect(viewModel, resumed, state.sessionExpired) {
        if (resumed && !state.sessionExpired) while (isActive) {
            delay(ReelsViewModel.UPDATE_INTERVAL_MILLIS); viewModel.checkForUpdates()
        }
    }
    LaunchedEffect(pager, rows.map { it.id }) {
        if (!pager.isScrollInProgress) {
            val index = rows.indexOfFirst { it.id == viewModel.state.activeId }
            if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
        }
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.distinctUntilChanged().collect { (scrolling, index) ->
            if (!scrolling) rows.getOrNull(index)?.let { viewModel.select(it.id) }
        }
    }
    LaunchedEffect(pager.currentPage, rows.size, state.pageVersion, state.loadingMore, state.hasMore) {
        if (rows.isNotEmpty() && pager.currentPage >= rows.lastIndex - 2 && state.error == null) viewModel.loadMore()
    }
    BackHandler(enabled = comments == null, onBack = onBack)

    fun share(reel: Reel) {
        val text = listOf(reel.caption.take(650), "https://mongosky.com/reels/${reel.id}").filter { it.isNotBlank() }.joinToString("\n\n")
        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Share reel")) }
        catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    }
    fun profile(reel: Reel) {
        navigateProfile(ProfileOpenRequest(reel.author.id, reel.author))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF090909)), contentAlignment = Alignment.Center) {
        val large = maxWidth >= 721.dp && maxHeight >= 520.dp
        val width = if (large) minOf(506.dp, (maxHeight - 24.dp) * 0.5625f) else maxWidth
        val height = if (large) minOf(900.dp, maxHeight - 24.dp) else maxHeight
        val pageHeightPixels = with(LocalDensity.current) { height.toPx() }
        Box(Modifier.width(width).height(height).clipToBounds().then(if (large) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)) {
            // One stable video surface for the entire route, independent of lazy page disposal.
            ContentFrame(if (current != null) playback?.player else null, Modifier.fillMaxSize().graphicsLayer {
                translationY = (pager.settledPage - pager.currentPage - pager.currentPageOffsetFraction) * pageHeightPixels
            }, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit)
            if (rows.isNotEmpty()) VerticalPager(state = pager, key = { rows.getOrNull(it)?.id ?: "reel-page-$it" },
                beyondViewportPageCount = 0, modifier = Modifier.fillMaxSize()) { index ->
                val reel = rows.getOrNull(index) ?: return@VerticalPager
                ReelCard(reel, playback, index == pager.settledPage, reel.author.id in state.verifiedAuthors,
                    viewModel.loves[reel.id], viewModel.comments.commentCounts["MEDIA:${reel.id}"] ?: reel.commentsCount,
                    onLove = { viewModel.love(reel) }, onDoubleLove = { viewModel.love(reel, onlyLove = true) },
                    onComment = { viewModel.openComments(reel) }, onShare = { share(reel) }, onProfile = { profile(reel) },
                    onClearError = { viewModel.dismissLoveError(reel.id) })
            }
            if (rows.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading || !state.initialized && !state.sessionExpired && state.error == null) {
                    CircularProgressIndicator(Modifier.size(43.dp), color = Color.White, strokeWidth = 3.dp)
                    Text("Loading Reels...", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp)
                } else {
                    Text(if (state.error == null) "No Reels yet" else "Could not load Reels", color = Color.White,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(state.error ?: "New videos will appear here.", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                    TextButton(if (state.sessionExpired) onSignInAgain else viewModel::retry) {
                        Text(if (state.sessionExpired) "Sign in again" else "Refresh", color = Color.White)
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReelRoundButton(ReelsIcons.back, "Go back", onBack)
                Text("Reels", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                ReelRoundButton(if (muted) ReelsIcons.muted else ReelsIcons.volume,
                    if (muted) "Turn sound on" else "Mute video", { muted = !muted }, iconSize = 22)
            }
            if (state.loadingMore) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(30.dp)).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Loading more...", color = Color.White, fontSize = 12.sp)
            }
            if (rows.isNotEmpty() && state.error != null) Row(Modifier.align(Alignment.BottomCenter).padding(12.dp)
                .background(Color(0xFFA51B33), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error, color = Color.White, fontSize = 12.sp, maxLines = 2, modifier = Modifier.weight(1f))
                TextButton(if (state.sessionExpired) onSignInAgain else viewModel::retry) {
                    Text(if (state.sessionExpired) "Sign in" else "Retry", color = Color.White)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
        }
    }
    comments?.let { CommentsSheet(it, userName, viewModel.comments) }
}

internal fun Context.reelsLifecycleOwner(): LifecycleOwner? {
    var current = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? LifecycleOwner
}
