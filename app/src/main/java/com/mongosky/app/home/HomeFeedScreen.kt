@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package com.mongosky.app.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.comments.CommentsSheet
import com.mongosky.app.mediapost.MediaPostUploadCard
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.mediapost.MediaPostViewModel
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.FeedDivider
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.MediaPostCard
import com.mongosky.app.post.PhotoViewer
import com.mongosky.app.post.PhotoViewerState
import com.mongosky.app.post.PostCardActions
import com.mongosky.app.post.TextPostCard
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionPeopleSheet
import com.mongosky.app.reactions.ReactionSummary
import com.mongosky.app.reels.Reel
import com.mongosky.app.reels.ReelsPreviewRefreshEffect
import com.mongosky.app.reels.ReelsPreviewSection
import com.mongosky.app.reels.ReelsPreviewViewModel
import com.mongosky.app.shared.FeedNotice
import com.mongosky.app.shared.FeedSpinner
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun HomeFeedScreen(
    viewModel: HomeFeedViewModel,
    userName: String,
    onSignInAgain: () -> Unit,
    mediaPostViewModel: MediaPostViewModel,
    onEditUpload: () -> Unit,
    onCheckUpload: () -> Unit,
    reelsPreviewViewModel: ReelsPreviewViewModel,
    onOpenReel: (Reel) -> Unit,
    onOpenReels: () -> Unit,
    scrollToTopRequest: Int = 0
) {
    val state = viewModel.uiState
    val upload by mediaPostViewModel.uploadState.collectAsState()
    val hasUpload = upload !== MediaPostUploadState.Idle &&
            upload !is MediaPostUploadState.Succeeded
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controls = viewModel.controls
    var now by remember { mutableStateOf(Instant.now()) }
    var photoViewer by remember { mutableStateOf<PhotoViewerState?>(null) }
    var handledTopRequest by rememberSaveable { mutableStateOf(0) }
    var launchedDownloadKey by rememberSaveable { mutableStateOf<String?>(null) }
    val downloadPicker = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("*/*") {
        override fun createIntent(context: android.content.Context, input: String): Intent =
            super.createIntent(context, input).setType(viewModel.download?.mimeType ?: "application/octet-stream")
    }) { uri ->
        launchedDownloadKey = null
        viewModel.completeDownloadPicker(context, uri)
    }
    val download = viewModel.download
    LaunchedEffect(download?.post?.key, download?.choosing) {
        if (download?.choosing == true && launchedDownloadKey != download.post.key) {
            launchedDownloadKey = download.post.key
            try { downloadPicker.launch(download.fileName) }
            catch (_: ActivityNotFoundException) {
                launchedDownloadKey = null; viewModel.cancelDownloadPicker()
                controls.notify("No file picker is available.")
            }
        }
    }
    LaunchedEffect(controls) { controls.notices.collect { snackbar.showSnackbar(it) } }
    HomeFeedEffects(viewModel, listState)
    val preview = reelsPreviewViewModel.state
    val hasReelsPreview = state.posts.size >= HOME_REELS_AFTER_POSTS &&
        (!preview.initialized || preview.reels.isNotEmpty() || preview.error != null)
    ReelsPreviewRefreshEffect(reelsPreviewViewModel,
        enabled = state.posts.size >= HOME_REELS_AFTER_POSTS && !state.sessionExpired,
        refreshVersion = state.refreshVersion)

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest != handledTopRequest) {
            listState.scrollToItem(0)
            handledTopRequest = scrollToTopRequest
        }
    }

    LaunchedEffect(Unit) {
        while (true) { delay(60_000); now = Instant.now() }
    }

    val loadMoreKeys = remember(state.posts) { state.posts.takeLast(3).map { it.key }.toSet() }
    LaunchedEffect(listState, loadMoreKeys, state.loading, state.hasMore, state.errors, state.sessionExpired) {
        if (loadMoreKeys.isNotEmpty() && !state.loading && state.hasMore && state.errors.isEmpty() && !state.sessionExpired) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key in loadMoreKeys } }
                .distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
        }
    }

    fun postLink(post: HomePost) = "https://mongosky.com/post/${post.id}"

    val sharePost: (HomePost) -> Unit = remember(context, scope, snackbar) { { post ->
        val text = listOf(post.content.take(650).trim(), postLink(post)).filter { it.isNotBlank() }.joinToString("\n\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "${post.author.displayName.ifBlank { "Mongosky" }}'s post")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try { context.startActivity(Intent.createChooser(intent, "Share post")) }
        catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    } }
    val copyPost: (HomePost) -> Unit = remember(clipboard, scope, snackbar) { { post ->
        clipboard.setText(AnnotatedString(postLink(post)))
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Link copied") }
    } }
    val openPhotos: (List<FeedMedia>, Int) -> Unit = remember { { photos, index ->
        photoViewer = PhotoViewerState(photos, index)
    } }

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (hasUpload) item(key = "media_upload", contentType = "upload") {
                    MediaPostUploadCard(
                        state = upload,
                        onRetry = mediaPostViewModel::retryUpload,
                        onEdit = onEditUpload,
                        onDismiss = mediaPostViewModel::dismissFailedUpload,
                        onSignIn = onSignInAgain,
                        onCheckPosts = onCheckUpload,
                        retryDelaySeconds = mediaPostViewModel::retryDelaySeconds,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                if (state.sessionError != null) item(key = "session_notice", contentType = "notice") {
                    FeedNotice(state.sessionError, if (state.sessionExpired) "Sign in again" else "Retry",
                        if (state.sessionExpired) onSignInAgain else viewModel::startSession)
                }
                if (state.newPostsCount > 0) item(key = "new-posts", contentType = "notice") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { viewModel.refresh(); scope.launch { listState.scrollToItem(0) } }) {
                            Text("${state.newPostsCount} new ${if (state.newPostsCount == 1) "post" else "posts"}",
                                color = FeedColors.brand, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (state.posts.isEmpty() && state.loading) item(key = "initial_loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { FeedSpinner() }
                }
                items(count = state.posts.size + if (hasReelsPreview) 1 else 0,
                    key = { index -> if (hasReelsPreview && index == HOME_REELS_AFTER_POSTS) "home-reels-preview"
                        else state.posts[homeFeedPostIndex(index, hasReelsPreview)].key },
                    contentType = { index -> if (hasReelsPreview && index == HOME_REELS_AFTER_POSTS) "reels-preview"
                        else state.posts[homeFeedPostIndex(index, hasReelsPreview)].source.name }) { index ->
                    if (hasReelsPreview && index == HOME_REELS_AFTER_POSTS) {
                        ReelsPreviewSection(preview, onOpenReel, onOpenReels,
                            if (preview.sessionExpired) onSignInAgain else reelsPreviewViewModel::refresh)
                        FeedDivider()
                    } else {
                        val post = state.posts[homeFeedPostIndex(index, hasReelsPreview)]
                        val enabled = !state.sessionExpired
                        HomeFeedPostItem(post, now, viewModel, enabled, sharePost, copyPost, openPhotos)
                        FeedDivider()
                    }
                }
                if (state.errors.isNotEmpty() && !state.sessionExpired) item(key = "feed_error", contentType = "notice") {
                    FeedNotice(state.errors.values.distinct().joinToString("\n"), "Retry", viewModel::retryFeed)
                }
                if (!hasUpload && state.posts.isEmpty() && !state.loading && state.errors.isEmpty() && state.sessionError == null) {
                    item(key = "empty_feed", contentType = "notice") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No posts yet", color = FeedColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("New posts will appear here.", color = FeedColors.muted, fontSize = 14.sp)
                            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                        }
                    }
                }
                if (state.posts.isNotEmpty() && !state.sessionExpired && state.errors.isEmpty()) {
                    item(key = "feed_footer", contentType = "loading") {
                        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> FeedSpinner()
                                state.hasMore -> TextButton(onClick = viewModel::loadMore) { Text("Load more", color = FeedColors.muted, fontSize = 14.sp) }
                                else -> Text("You're all caught up", color = FeedColors.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    viewModel.commentsState?.let { CommentsSheet(it, userName, viewModel.comments) }
    viewModel.peopleState?.let {
        ReactionPeopleSheet(it, viewModel::closeReactionPeople,
            viewModel::selectReactionPeopleFilter, viewModel::retryReactionPeople)
    }
    photoViewer?.let { PhotoViewer(it) { photoViewer = null } }
    if (!state.sessionExpired) {
        controls.menu?.let { post ->
            HomePostMenu(post, controls::closeMenu) { action -> when (action) {
                HomeMenuAction.COPY -> { controls.closeMenu(); copyPost(post) }
                HomeMenuAction.DOWNLOAD -> viewModel.requestDownload(post)
                HomeMenuAction.EDIT -> controls.openEditor(post)
                HomeMenuAction.DELETE -> controls.openDelete(post)
                else -> controls.unavailable(action)
            } }
        }
        controls.editor?.let { HomePostEditor(it, controls) }
        controls.deletion?.let { HomeDeleteDialog(it, controls::closeDelete, controls::confirmDelete) }
    }
}

internal const val HOME_REELS_AFTER_POSTS = 3
internal fun homeFeedPostIndex(itemIndex: Int, hasReelsPreview: Boolean): Int =
    if (hasReelsPreview && itemIndex > HOME_REELS_AFTER_POSTS) itemIndex - 1 else itemIndex
