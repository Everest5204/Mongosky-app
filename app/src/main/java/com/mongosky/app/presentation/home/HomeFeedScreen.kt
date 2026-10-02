@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package com.mongosky.app.presentation.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.lazy.items
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
import com.mongosky.app.domain.model.FeedMedia
import com.mongosky.app.domain.model.feed.ReactionSummary
import com.mongosky.app.presentation.home.cards.FeedColors
import com.mongosky.app.presentation.home.cards.FeedDivider
import com.mongosky.app.presentation.home.cards.MediaPostCard
import com.mongosky.app.presentation.home.cards.PostCardActions
import com.mongosky.app.presentation.home.cards.TextPostCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun HomeFeedScreen(viewModel: HomeFeedViewModel, userName: String, onSignInAgain: () -> Unit, scrollToTopRequest: Int = 0) {
    val state = viewModel.uiState
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Instant.now()) }
    var photoViewer by remember { mutableStateOf<PhotoViewerState?>(null) }
    var handledTopRequest by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest != handledTopRequest) {
            listState.scrollToItem(0)
            handledTopRequest = scrollToTopRequest
        }
    }

    LaunchedEffect(Unit) {
        while (true) { delay(60_000); now = Instant.now() }
    }

    LaunchedEffect(listState, state.posts.size, state.loading, state.hasMore, state.errors, state.sessionExpired) {
        if (!state.loading && state.hasMore && state.errors.isEmpty() && !state.sessionExpired) {
            snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= (state.posts.size - 3).coerceAtLeast(0) }
                .distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
        }
    }

    fun postLink(post: HomePost) = "https://mongosky.com/post/${post.id}"

    fun share(post: HomePost) {
        val content = when (post) { is HomePost.Text -> post.post.displayText; is HomePost.Media -> post.post.caption }
        val text = listOf(content.take(650).trim(), postLink(post)).filter { it.isNotBlank() }.joinToString("\n\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "${post.author.displayName.ifBlank { "Mongosky" }}'s post")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try { context.startActivity(Intent.createChooser(intent, "Share post")) }
        catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No sharing app is available.") } }
    }

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (state.sessionError != null) item(key = "session_notice", contentType = "notice") {
                    FeedNotice(state.sessionError, if (state.sessionExpired) "Sign in again" else "Retry",
                        if (state.sessionExpired) onSignInAgain else viewModel::startSession)
                }
                if (state.posts.isEmpty() && state.loading) item(key = "initial_loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { FeedSpinner() }
                }
                items(state.posts, key = { it.key }, contentType = { it.source.name }) { post ->
                    LaunchedEffect(post.key, state.refreshVersion) { viewModel.ensureReaction(post) }
                    val reaction = viewModel.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
                    val commentsCount = viewModel.commentCounts[post.key] ?: post.commentsCount
                    val actions = PostCardActions(
                        onReact = { viewModel.react(post, it) }, onComments = { viewModel.openComments(post) },
                        onShare = { share(post) }, onCopyLink = {
                            clipboard.setText(AnnotatedString(postLink(post)))
                            if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Link copied") }
                        }, onReactionPeople = { viewModel.openReactionPeople(post) },
                        onRetryReaction = { viewModel.ensureReaction(post) }
                    )
                    val enabled = !state.sessionExpired
                    when (post) {
                        is HomePost.Text -> TextPostCard(post.post, now, reaction, commentsCount, enabled, actions)
                        is HomePost.Media -> MediaPostCard(post.post, now, reaction, commentsCount, enabled, actions,
                            onOpenPhotos = { photos, index -> photoViewer = PhotoViewerState(photos, index) })
                    }
                    FeedDivider()
                }
                if (state.errors.isNotEmpty() && !state.sessionExpired) item(key = "feed_error", contentType = "notice") {
                    FeedNotice(state.errors.values.distinct().joinToString("\n"), "Retry", viewModel::retryFeed)
                }
                if (state.posts.isEmpty() && !state.loading && state.errors.isEmpty() && state.sessionError == null) {
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

    viewModel.commentsState?.let { CommentsSheet(it, userName, viewModel) }
    viewModel.peopleState?.let { ReactionPeopleSheet(it, viewModel::closeReactionPeople) }
    photoViewer?.let { PhotoViewer(it) { photoViewer = null } }
}

@Composable
internal fun FeedSpinner() { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = FeedColors.brand, strokeWidth = 2.dp) }

@Composable
internal fun FeedNotice(message: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = FeedColors.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = FeedColors.brand, contentColor = Color.White)) {
            Text(action, fontSize = 14.sp)
        }
    }
}
