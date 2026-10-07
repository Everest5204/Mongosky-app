@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")
package com.mongosky.app.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
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
import com.mongosky.app.comments.CommentsSheet
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.PhotoViewer
import com.mongosky.app.post.PhotoViewerState
import com.mongosky.app.reactions.ReactionPeopleSheet
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun UserProfileScreen(viewModel: UserProfileViewModel, actionsModel: HomeFeedViewModel,
    viewerId: String?, userId: String, viewerName: String, hint: FeedAuthor?, onBack: () -> Unit, onSignInAgain: () -> Unit) {
    val current by viewModel.state.collectAsState()
    // A saved back-stack entry never renders another user's state for a frame.
    val state = current.takeIf { it.userId == userId } ?: UserProfileState(userId = userId, profileLoading = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val navigateProfile = LocalProfileNavigator.current
    val lifecycleOwner = remember(context) { context.ownProfileLifecycleOwner() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var optionsOpen by rememberSaveable(userId) { mutableStateOf(false) }
    var photoViewer by remember(userId) { mutableStateOf<PhotoViewerState?>(null) }
    var videoViewer by remember(userId) { mutableStateOf<ProfileGalleryAsset?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var reactionVersion by remember(userId) { mutableStateOf(0L) }
    var started by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true) }
    LaunchedEffect(viewerId, userId) { viewModel.enter(viewerId, userId, hint) }
    LaunchedEffect(userId, state.refreshVersion, state.profile != null) {
        if (state.profile != null) { actionsModel.refreshProfileReactions(); reactionVersion++ }
    }
    DisposableEffect(viewModel, lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { started = true; viewModel.enter(viewerId, userId) }
                Lifecycle.Event.ON_STOP -> { started = false; viewModel.pause() }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer); viewModel.pause(); actionsModel.closeOverlays() }
    }
    LaunchedEffect(viewModel, userId, started, photoViewer, videoViewer?.key) {
        if (started && photoViewer == null && videoViewer == null) while (isActive) {
            delay(UserProfileViewModel.UPDATE_INTERVAL_MILLIS)
            viewModel.checkForUpdates(); now = Instant.now()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.dismissNotice() }
    }

    fun copyLink(link: String) {
        clipboard.setText(AnnotatedString(link))
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Link copied") }
    }
    fun sharePost(post: HomePost) {
        val caption = when (post) { is HomePost.Text -> post.post.displayText; is HomePost.Media -> post.post.caption }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT,
            listOf(caption.take(650), "https://mongosky.com/post/${post.id}").filter { it.isNotBlank() }.joinToString("\n\n")) }
        try { context.startActivity(Intent.createChooser(intent, "Share post")) }
        catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    }
    fun openProfile(id: String) {
        viewModel.connections.close()
        navigateProfile(ProfileOpenRequest(id))
    }

    val assets = remember(state.posts, state.tab) { if (state.tab == OwnProfileTab.ALL) emptyList() else
        profileGallery(state.posts, if (state.tab == OwnProfileTab.PHOTOS) FeedMediaType.IMAGE else FeedMediaType.VIDEO) }
    val photos = remember(state.posts) { profileGallery(state.posts, FeedMediaType.IMAGE).map { it.media } }
    val visibleCount = if (state.tab == OwnProfileTab.ALL) state.posts.size else assets.size
    val available = state.profile != null && !state.unavailable && !state.sessionExpired

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 620.dp).fillMaxSize()) {
            val columns = if (maxWidth < 350.dp) 2 else 3
            val rows = remember(assets, columns) { assets.chunked(columns) }
            val nearKeys = remember(state.posts, rows, state.tab) {
                if (state.tab == OwnProfileTab.ALL) state.posts.takeLast(2).map { "post:${it.source}:${it.id}" }.toSet()
                else rows.takeLast(2).map { "gallery:${it.first().key}" }.toSet()
            }
            LaunchedEffect(list, state.pageVersion, state.postsLoading, state.hasMore, state.postErrors, available, state.tab) {
                if (available && state.hasMore && !state.postsLoading && state.postErrors.isEmpty())
                    snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key in nearKeys || it.key == "load_more" } }
                        .distinctUntilChanged().collect { if (it) viewModel.loadMore() }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item("profile_header", "header") { UserProfileHeader(state, onBack,
                        onConnections = { viewModel.connections.open(userId, it) }, onFollow = viewModel::toggleFollowing,
                        onMessage = { scope.launch { snackbar.showSnackbar("Messaging will be connected in the Chat section.") } },
                        onOptions = { optionsOpen = true }) }
                    if (state.profileLoading && state.profile == null) item("profile_loading", "loading") {
                        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { OwnProfileSpinner() }
                    }
                    state.profileError?.let { error -> item("profile_error", "notice") {
                        OwnProfileNotice(error, if (state.sessionExpired) "Sign in again" else "Retry",
                            if (state.sessionExpired) onSignInAgain else viewModel::retryProfile)
                    } }
                    if (state.followingUncertain && !state.sessionExpired) item("follow_check", "notice") {
                        OwnProfileNotice("Refresh the profile to confirm your follow status.", "Check profile", viewModel::refresh)
                    }
                    if (available) {
                        item("profile_tabs", "tabs") { OwnProfileTabs(state.tab) { tab ->
                            if (tab != state.tab) { viewModel.selectTab(tab); scope.launch { list.scrollToItem(0) } }
                        } }
                        if (state.tab == OwnProfileTab.ALL) {
                            items(state.posts, key = { "post:${it.source}:${it.id}" }, contentType = { it.type.name }) { post ->
                                OwnProfilePostItem(post, state.profile?.asPostOwner(), now, actionsModel, !actionsModel.uiState.sessionExpired,
                                    onPhotos = { media, index -> photoViewer = PhotoViewerState(media, index) }, onVideo = { videoViewer = it },
                                    onCopy = { copyLink("https://mongosky.com/post/${it.id}") }, onShare = ::sharePost,
                                    isSelf = state.profile?.isSelf == true, reactionRefreshVersion = reactionVersion)
                            }
                        } else items(rows, key = { "gallery:${it.first().key}" }, contentType = { "gallery_row" }) { row ->
                            OwnProfileGalleryRow(row, columns) { asset ->
                                if (asset.media.type == FeedMediaType.VIDEO) videoViewer = asset
                                else photoViewer = PhotoViewerState(photos, photos.indexOf(asset.media).coerceAtLeast(0))
                            }
                        }
                        if (state.postsLoading && (!state.checking || visibleCount == 0)) item("posts_loading", "loading") {
                            Box(Modifier.fillMaxWidth().height(if (visibleCount == 0) 140.dp else 64.dp), contentAlignment = Alignment.Center) { OwnProfileSpinner() }
                        }
                        if (state.postErrors.isNotEmpty()) item("posts_error", "notice") {
                            OwnProfileNotice(state.postErrors.values.distinct().joinToString("\n"), "Retry", viewModel::refresh)
                        }
                        if (visibleCount == 0 && !state.postsLoading && state.postErrors.isEmpty()) item("empty", "notice") {
                            Text(if (state.hasMore) "More ${state.tab.label.lowercase()} may be in older posts" else
                                "No ${if (state.tab == OwnProfileTab.ALL) "posts" else state.tab.label.lowercase()} yet",
                                color = OwnProfileColors.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(24.dp))
                        }
                        if (state.hasMore && !state.postsLoading) item("load_more", "footer") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(viewModel::loadMore) { Text("Load more", color = OwnProfileColors.brand) } }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
    OwnProfileConnectionsSheet(viewModel.connections, ::openProfile)
    if (optionsOpen && available) ModalBottomSheet(onDismissRequest = { optionsOpen = false }, containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Profile options", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OwnProfileColors.text)
            TextButton({ optionsOpen = false; copyLink("https://mongosky.com/profile/$userId") }, modifier = Modifier.fillMaxWidth()) {
                Text("Copy profile link", color = OwnProfileColors.text)
            }
        }
    }
    actionsModel.commentsState?.let { CommentsSheet(it, viewerName, actionsModel.comments) }
    actionsModel.peopleState?.let {
        ReactionPeopleSheet(it, actionsModel::closeReactionPeople,
            actionsModel::selectReactionPeopleFilter, actionsModel::retryReactionPeople)
    }
    photoViewer?.let { PhotoViewer(it) { photoViewer = null } }
    videoViewer?.let { OwnProfileVideoViewer(it) { videoViewer = null } }
}
