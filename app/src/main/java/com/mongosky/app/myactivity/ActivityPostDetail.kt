@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")
package com.mongosky.app.myactivity

import com.mongosky.app.shared.PublicLinks
import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

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
import androidx.compose.ui.unit.sp
import com.mongosky.app.comments.CommentsSheet
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.post.*
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionPeopleSheet
import com.mongosky.app.reactions.ReactionSummary
import java.time.Instant
import kotlinx.coroutines.launch

/** Reuses the app's native styled text/media cards and live comment controls. */
@Composable internal fun ActivityPostDetail(target: ActivityTarget,feed: HomeFeedViewModel,userName: String,
    enabled: Boolean,onBack: () -> Unit,onSignInAgain: () -> Unit) {
    val post = target.post ?: return; val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()
    var viewer by remember(post.key) { mutableStateOf<PhotoViewerState?>(null) }
    val expired = feed.uiState.sessionExpired
    val canAct = enabled && !expired && feed.uiState.sessionReady
    LaunchedEffect(post.key,feed.uiState.sessionReady) { if (!expired) { if (!feed.uiState.sessionReady) feed.startSession() else feed.ensureReaction(post) } }
    DisposableEffect(post.key) { onDispose { feed.closeOverlays() } }
    BackHandler(feed.commentsState == null && feed.peopleState == null && viewer == null) { if (enabled) onBack() }
    val actions = PostCardActions(onReact = { feed.react(post,it) },onComments = { feed.openComments(post) },
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT,PublicLinks.post(post.id)) }
            try { context.startActivity(Intent.createChooser(intent,appText("Share post"))) }
            catch (_: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar(appText("No sharing app is available.")) } }
        },onCopyLink = { clipboard.setText(AnnotatedString(PublicLinks.post(post.id))) },
        onReactionPeople = { feed.openReactionPeople(post) },onRetryReaction = { feed.ensureReaction(post) })
    val reaction = feed.reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
    val count = feed.commentCounts[post.key] ?: post.commentsCount
    Scaffold(containerColor = AppColors.surface(ActivityBackground),snackbarHost = { SnackbarHost(snackbar) },topBar = {
        TopAppBar(title = { Text(appText("Activity post")) },navigationIcon = { IconButton(onBack,enabled = enabled) { Icon(ActivityIcons.back,appDescription("Back to My Activity")) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.surface(Color.White)))
    }) { inset -> Box(Modifier.fillMaxSize().padding(inset),contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize(),contentPadding = PaddingValues(bottom = 24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("context") { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(target.item.title,color = AppColors.content(ActivityGreen),fontSize = 14.sp)
                if (target.item.kind == "comment" || target.item.kind == "comment_love") Text(target.item.text,color = AppColors.content(ActivityMuted),fontSize = 14.sp)
            } }
            if (expired) item("session") { ActivityMessage("Sign in again","Your session has expired.","Sign in",onSignInAgain) }
            else item(post.key) { when (post) {
                is HomePost.Text -> TextPostCard(post.post,Instant.now(),reaction,count,canAct,actions)
                is HomePost.Media -> MediaPostCard(post.post,Instant.now(),reaction,count,canAct,actions,
                    onOpenPhotos = { photos,index -> viewer = PhotoViewerState(photos,index) })
            } }
            if (target.item.kind == "comment_love") item("hint") { Text(appText("Open comments to view and manage your comment likes."),
                modifier = Modifier.padding(horizontal = 16.dp),fontSize = 13.sp,color = AppColors.content(ActivityMuted)) }
        }
    } }
    feed.commentsState?.let { CommentsSheet(it,userName,feed.comments) }
    feed.peopleState?.let { ReactionPeopleSheet(it,feed::closeReactionPeople,feed::selectReactionPeopleFilter,feed::retryReactionPeople) }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}
