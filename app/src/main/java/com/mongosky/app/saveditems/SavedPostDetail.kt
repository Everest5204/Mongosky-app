@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")
package com.mongosky.app.saveditems

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mongosky.app.comments.CommentsSheet
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.post.*
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionPeopleSheet
import com.mongosky.app.reactions.ReactionSummary
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
internal fun SavedPostDetail(
    target: SavedTarget,
    feed: HomeFeedViewModel,
    userName: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onSignInAgain: () -> Unit,
) {
    val colors = rememberSavedUiColors()
    val post = target.post ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var viewer by remember(post.key) { mutableStateOf<PhotoViewerState?>(null) }
    val expired = feed.uiState.sessionExpired
    val canAct = enabled && !expired && feed.uiState.sessionReady

    LaunchedEffect(post.key, feed.uiState.sessionReady) {
        if (!expired) {
            if (!feed.uiState.sessionReady) feed.startSession() else feed.ensureReaction(post)
        }
    }
    DisposableEffect(post.key) {
        onDispose { feed.closeOverlays() }
    }
    BackHandler(feed.commentsState == null && feed.peopleState == null && viewer == null) {
        if (enabled) onBack()
    }
    val actions = PostCardActions(
        onReact = { feed.react(post, it) },
        onComments = { feed.openComments(post) },
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://mongosky.com/post/${post.id}")
            }
            try {
                context.startActivity(Intent.createChooser(intent, appText("Share post")))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(appText("No sharing app is available.")) }
            }
        },
        onCopyLink = { clipboard.setText(AnnotatedString("https://mongosky.com/post/${post.id}")) },
        onReactionPeople = { feed.openReactionPeople(post) },
        onRetryReaction = { feed.ensureReaction(post) },
    )
    val reaction = feed.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val count = feed.commentCounts[post.key] ?: post.commentsCount

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(appText("Saved post")) },
                navigationIcon = {
                    IconButton(onBack, enabled = enabled) {
                        Icon(SavedIcons.back, appDescription("Back to saved items"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 680.dp).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (expired) {
                    item("session") {
                        SavedMessage("Sign in again", "Your session has expired.", "Sign in", onSignInAgain)
                    }
                } else {
                    item(post.key) {
                        when (post) {
                            is HomePost.Text -> TextPostCard(post.post, Instant.now(), reaction, count, canAct, actions)
                            is HomePost.Media -> MediaPostCard(
                                post.post, Instant.now(), reaction, count, canAct, actions,
                                onOpenPhotos = { photos, index -> viewer = PhotoViewerState(photos, index) },
                            )
                        }
                    }
                }
            }
        }
    }
    feed.commentsState?.let { CommentsSheet(it,userName,feed.comments) }
    feed.peopleState?.let { ReactionPeopleSheet(it,feed::closeReactionPeople,feed::selectReactionPeopleFilter,feed::retryReactionPeople) }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}
