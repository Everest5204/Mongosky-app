@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")
package com.mongosky.app.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mongosky.app.comments.CommentsSheet
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.PhotoViewer
import com.mongosky.app.post.PhotoViewerState
import com.mongosky.app.reactions.ReactionPeopleSheet
import com.mongosky.app.profile.feed.OwnProfileFeedEffects
import com.mongosky.app.profile.feed.OwnProfileFeedOverlays
import com.mongosky.app.profile.feed.OwnProfileFeedPostItem
import com.mongosky.app.profile.feed.rememberOwnProfileFeed
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun OwnProfileScreen(
    viewModel: OwnProfileViewModel, actionsModel: HomeFeedViewModel,
    userId: String?, userName: String, profileImageUrl: String?, signingOut: Boolean,
    signOutError: String?, onSignOut: () -> Unit, onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val navigateProfile = LocalProfileNavigator.current
    val lifecycleOwner = remember(context) { context.ownProfileLifecycleOwner() }
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var optionsOpen by rememberSaveable(userId) { mutableStateOf(false) }
    var requestedImage by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var photoViewer by remember { mutableStateOf<PhotoViewerState?>(null) }
    var videoViewer by remember { mutableStateOf<ProfileGalleryAsset?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var started by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true) }
    val feed = rememberOwnProfileFeed(viewModel, actionsModel)
    OwnProfileFeedEffects(feed, state, list,
        active = started && !signingOut && !state.sessionExpired && !actionsModel.uiState.sessionExpired,
        signingOut = signingOut)
    LaunchedEffect(feed) { feed.controls.notices.collect { snackbar.showSnackbar(it) } }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val kind = requestedImage?.let { value -> ProfileImageKind.entries.firstOrNull { it.name == value } }
        requestedImage = null
        if (uri != null && kind != null && !signingOut) viewModel.uploadImage(kind, uri.toString())
    }
    fun chooseImage(kind: ProfileImageKind) {
        if (requestedImage != null || state.busy || state.uploadUncertain || state.sessionExpired) return
        requestedImage = kind.name
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(userId, signingOut) { if (!signingOut) viewModel.enter(userId) }
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!started) viewModel.checkForUpdates(force = true)
                    started = true
                }
                Lifecycle.Event.ON_STOP -> { started = false; viewModel.pause() }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer); viewModel.pause(); actionsModel.closeOverlays() }
    }
    LaunchedEffect(viewModel, started, signingOut, videoViewer?.key, photoViewer) {
        if (started && !signingOut && videoViewer == null && photoViewer == null) while (isActive) {
            delay(OwnProfileViewModel.UPDATE_INTERVAL_MILLIS)
            viewModel.checkForUpdates()
            now = Instant.now()
        }
    }

    fun copyProfile() {
        if (state.ownerId.isBlank()) return
        clipboard.setText(AnnotatedString("https://mongosky.com/profile/${state.ownerId}"))
        optionsOpen = false
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Profile link copied") }
    }
    fun copyPost(post: HomePost) {
        clipboard.setText(AnnotatedString("https://mongosky.com/post/${post.id}"))
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Link copied") }
    }
    fun sharePost(post: HomePost) {
        val caption = when (post) { is HomePost.Text -> post.post.displayText; is HomePost.Media -> post.post.caption }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, listOf(caption.take(650), "https://mongosky.com/post/${post.id}").filter { it.isNotBlank() }.joinToString("\n\n"))
        }
        try { context.startActivity(Intent.createChooser(intent, "Share post")) }
        catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    }
    fun openProfile(id: String) {
        viewModel.connections.close()
        navigateProfile(ProfileOpenRequest(id))
    }

    val assets = remember(state.posts, state.tab) {
        if (state.tab == OwnProfileTab.ALL) emptyList() else profileGallery(state.posts,
            if (state.tab == OwnProfileTab.PHOTOS) FeedMediaType.IMAGE else FeedMediaType.VIDEO)
    }
    val photos = remember(state.posts) { profileGallery(state.posts, FeedMediaType.IMAGE).map { it.media } }
    val visibleCount = if (state.tab == OwnProfileTab.ALL) state.posts.size else assets.size

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
            val columns = if (maxWidth < 350.dp) 2 else 3
            val galleryRows = remember(assets, columns) { assets.chunked(columns) }
            val nearKeys = remember(state.posts, state.tab, galleryRows) {
                if (state.tab == OwnProfileTab.ALL) state.posts.takeLast(2).map { "post:${it.source}:${it.id}" }.toSet()
                else galleryRows.takeLast(2).map { "gallery:${it.first().key}" }.toSet()
            }
            LaunchedEffect(list, state.tab, nearKeys, state.postPageVersion, state.postsLoading,
                state.canLoadMore, state.sessionExpired, signingOut) {
                if (!state.postsLoading && state.canLoadMore && !state.sessionExpired && !signingOut)
                    snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key in nearKeys || it.key == "load_more" } }
                        .distinctUntilChanged().collect {
                        if (it) viewModel.loadMore()
                    }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item("profile_header", "header") {
                        OwnProfileHeader(state, userName, profileImageUrl, ::chooseImage,
                            onConnections = { viewModel.connections.open(state.ownerId, it) },
                            onStory = { scope.launch { snackbar.showSnackbar("This feature is currently unavailable.") } },
                            onEdit = viewModel::openEditor, onOptions = { optionsOpen = true },
                            onBack = onBack, backEnabled = !signingOut)
                    }
                    if (state.profileLoading && state.profile == null) item("profile_loading", "loading") {
                        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) { OwnProfileSpinner() }
                    }
                    if (state.profileError != null) item("profile_error", "notice") {
                        OwnProfileNotice(requireNotNull(state.profileError), if (state.sessionExpired) "Sign in again" else "Retry",
                            if (state.sessionExpired) onSignOut else viewModel::retryProfile, enabled = !signingOut)
                    }
                    if (!signOutError.isNullOrBlank()) item("logout_error", "notice") { OwnProfileNotice(signOutError, "Retry sign out", onSignOut, !signingOut) }
                    if (state.operationError != null && !state.editorOpen) item("operation_error", "notice") {
                        OwnProfileNotice(requireNotNull(state.operationError), "Check profile", viewModel::refresh, !state.busy && !signingOut)
                    }
                    item("profile_tabs", "tabs") { OwnProfileTabs(state.tab) { tab ->
                        if (tab != state.tab) { viewModel.selectTab(tab); scope.launch { list.scrollToItem(0) } }
                    } }
                    if (state.tab == OwnProfileTab.ALL) {
                        items(state.posts, key = { "post:${it.source}:${it.id}" }, contentType = { it.type.name }) { post ->
                            OwnProfileFeedPostItem(post, state.profile, now, actionsModel, feed,
                                !state.sessionExpired && !actionsModel.uiState.sessionExpired && !signingOut,
                                onPhotos = { media, index -> photoViewer = PhotoViewerState(media, index) },
                                onVideo = { videoViewer = it }, onCopy = ::copyPost, onShare = ::sharePost)
                        }
                    } else {
                        items(galleryRows, key = { "gallery:${it.first().key}" }, contentType = { "gallery_row" }) { row ->
                            OwnProfileGalleryRow(row, columns) { asset ->
                                if (asset.media.type == FeedMediaType.VIDEO) videoViewer = asset
                                else photoViewer = PhotoViewerState(photos, photos.indexOf(asset.media).coerceAtLeast(0))
                            }
                        }
                    }
                    if (state.postsLoading) item("posts_loading", "loading") {
                        Box(Modifier.fillMaxWidth().height(if (visibleCount == 0) 140.dp else 64.dp), contentAlignment = Alignment.Center) { OwnProfileSpinner() }
                    }
                    if (state.activePostErrors.isNotEmpty() && !state.sessionExpired) item("posts_error", "notice") {
                        OwnProfileNotice(state.activePostErrors.values.distinct().joinToString("\n"), "Retry", viewModel::retryPosts)
                    }
                    if (state.updatesError != null && !state.sessionExpired) item("updates_error", "notice") {
                        OwnProfileNotice(requireNotNull(state.updatesError), "Check updates", { viewModel.checkForUpdates(force = true) })
                    }
                    if (visibleCount == 0 && !state.postsLoading && state.activePostErrors.isEmpty() && !state.sessionExpired) item("empty", "notice") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 44.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.hasMore) "More ${state.tab.label.lowercase()} may be in older posts" else "No ${if (state.tab == OwnProfileTab.ALL) "posts" else state.tab.label.lowercase()} yet",
                                color = OwnProfileColors.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (state.tab == OwnProfileTab.ALL) "Share your moments with the world." else "Your ${state.tab.label.lowercase()} will appear here.",
                                color = OwnProfileColors.muted, fontSize = 14.sp)
                        }
                    }
                    if (state.canLoadMore && !state.postsLoading && !state.sessionExpired) item("load_more", "footer") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(viewModel::loadMore) { Text("Load more", color = OwnProfileColors.brand) } }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
    if (state.editorOpen) EditOwnProfileSheet(state, viewModel::updateBio, { viewModel.saveBio() }, ::chooseImage, viewModel::closeEditor)
    OwnProfileConnectionsSheet(viewModel.connections, ::openProfile)
    if (optionsOpen) ModalBottomSheet(onDismissRequest = { optionsOpen = false }, containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profile options", color = OwnProfileColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(::copyProfile, enabled = state.ownerId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Copy profile link", color = OwnProfileColors.text) }
            TextButton({ optionsOpen = false; onSignOut() }, enabled = !signingOut, modifier = Modifier.fillMaxWidth()) {
                if (signingOut) OwnProfileSpinner() else Text("Sign out", color = OwnProfileColors.brand)
            }
        }
    }
    OwnProfileFeedOverlays(feed, !state.sessionExpired && !actionsModel.uiState.sessionExpired && !signingOut, ::copyPost)
    actionsModel.commentsState?.let { CommentsSheet(it, state.profile?.displayName ?: userName, actionsModel.comments) }
    actionsModel.peopleState?.let {
        ReactionPeopleSheet(it, actionsModel::closeReactionPeople,
            actionsModel::selectReactionPeopleFilter, actionsModel::retryReactionPeople)
    }
    photoViewer?.let { PhotoViewer(it) { photoViewer = null } }
    videoViewer?.let { OwnProfileVideoViewer(it) { videoViewer = null } }
}

internal fun Context.ownProfileLifecycleOwner(): LifecycleOwner? {
    var current = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? LifecycleOwner
}
