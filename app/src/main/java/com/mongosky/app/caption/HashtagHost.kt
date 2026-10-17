@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.caption

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.appText
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.PhotoViewer
import com.mongosky.app.post.PhotoViewerState
import com.mongosky.app.post.PostCaption
import com.mongosky.app.profile.LocalProfileNavigator
import com.mongosky.app.profile.ProfileOpenRequest
import com.mongosky.app.shared.ProfileAvatar

internal val LocalHashtagController = staticCompositionLocalOf<HashtagController?> { null }

/** One feature host; no browser intent is ever used for a hashtag. */
@Composable
internal fun HashtagHost(sessionKey: String, enabled: Boolean, posts: List<HomePost>, onOpenProfile: (ProfileOpenRequest) -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val parent = rememberCoroutineScope()
    val store = remember(context) { TokenStore(context) }
    val controller = remember(sessionKey, enabled) { HashtagController(parent, store::read) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(controller, posts, enabled) { if (enabled) controller.seed(posts) }
    CompositionLocalProvider(LocalHashtagController provides controller.takeIf { enabled },
        LocalProfileNavigator provides onOpenProfile) {
        content()
        if (enabled) HashtagResults(controller)
    }
}

@Composable
private fun HashtagResults(controller: HashtagController) {
    val state by controller.state.collectAsState()
    val tag = state.tag ?: return
    val index = state.index
    val matches = remember(index, tag, state.posts) {
        val keys = index.matchingKeys(tag)
        state.posts.filter { it.key in keys }
    }
    val usage = index.usage(tag)
    val navigateProfile = LocalProfileNavigator.current
    var viewer by remember(tag) { mutableStateOf<PhotoViewerState?>(null) }
    ModalBottomSheet(
        onDismissRequest = controller::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.surface(Color.White)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                    Text("#$tag", color = AppColors.content(Color(0xFF2563EB)), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (usage != null) Text("${usage.people} people · ${usage.posts} posts", color = AppColors.content(Color(0xFF475569)), fontSize = 13.sp)
                    Text("Among loaded posts", color = AppColors.content(Color(0xFF64748B)), fontSize = 12.sp)
                }
                TextButton(onClick = { controller.loadMore(refresh = true) }, enabled = !state.loading) { Text(appText("Refresh")) }
                TextButton(onClick = controller::dismiss) { Text(appText("Close")) }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (matches.isEmpty()) item {
                    Text(if (state.loading) "Looking for matching posts…" else "No matches in loaded posts. Load more to search further.",
                        color = AppColors.content(Color(0xFF64748B)), modifier = Modifier.padding(16.dp))
                }
                items(matches, key = { it.key }) { post ->
                    HashtagPostPreview(post, onAuthor = {
                        controller.dismiss()
                        navigateProfile(ProfileOpenRequest(post.author.id, post.author))
                    }, onPhoto = { photos, selected -> viewer = PhotoViewerState(photos, selected) })
                }
                state.error?.let { error -> item {
                    Text(error, color = AppColors.content(Color(0xFFB91C1C)), modifier = Modifier.padding(horizontal = 16.dp))
                } }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                        if (state.loading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        else if (state.hasMore) TextButton(onClick = { controller.loadMore() }) { Text(appText("Load more")) }
                    }
                }
            }
        }
    }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}

@Composable
private fun HashtagPostPreview(post: HomePost, onAuthor: () -> Unit,
    onPhoto: (List<com.mongosky.app.post.FeedMedia>, Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.surface(Color(0xFFF8FAFC))).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onAuthor).padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileAvatar(post.author.profileImageUrl, Modifier.size(40.dp))
            Text(post.author.displayName.ifBlank { appText("Mongosky User") }, fontWeight = FontWeight.Bold,
                color = AppColors.content(Color(0xFF111827)), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PostCaption(post.captionContent())
        if (post is HomePost.Media) {
            val photos = post.post.media.filter { it.type == FeedMediaType.IMAGE }
            photos.firstOrNull()?.let { media ->
                AsyncImage(media.url, appText("Post image"), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clickable(role = Role.Button) { onPhoto(photos, 0) })
            }
            if (photos.isEmpty() && post.post.media.any { it.type == FeedMediaType.VIDEO }) {
                Text("Video post · open the author's profile to watch", color = AppColors.content(Color(0xFF64748B)),
                    fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}

/** Include real posts displayed on profile feeds in the same session snapshot. */
@Composable
internal fun ObserveHashtagPost(post: HomePost) {
    val controller = LocalHashtagController.current
    LaunchedEffect(controller, post) { controller?.observe(post) }
}
