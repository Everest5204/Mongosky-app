package com.mongosky.app.home

import com.mongosky.app.language.appText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.post.HomePost
import com.mongosky.app.post.postTimeAgo
import com.mongosky.app.profile.profileLink
import com.mongosky.app.shared.ProfileAvatar
import java.time.Instant
import kotlinx.coroutines.flow.first

@Composable
internal fun HomePostHeader(post: HomePost, now: Instant, controls: HomeFeedController,
    images: HomeFeedImageState, enabled: Boolean,
    authorStateOverride: HomeAuthorState? = null, onFollow: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null) {
    val authorState = remember(controls, controls.authorGeneration, post.author.id) { controls.author(post) }
    // A public profile already reads its owner's badge and relationship once for the screen.
    val state = authorStateOverride ?: authorState.value
    var shown by remember(controls, controls.authorGeneration, post.author.id) { mutableStateOf(state) }
    LaunchedEffect(controls.authorGeneration, post.author.id, state, images) {
        // Resolving a badge or relationship must not change the header height mid-fling.
        snapshotFlow { images.moving }.first { !it }
        shown = state
    }
    val showFollow = !post.isOwn && (!shown.following || shown.confirmation) && HomeFeedPolicy.validId(post.author.id)
    val name = post.author.displayName.ifBlank { appText("Mongosky User") }
    val hasControls = shown.verified || showFollow
    val label = appText(if (shown.following) "Following" else "Follow")
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelWidthEm = remember(label, textMeasurer, density) {
        val width = textMeasurer.measure(label,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)).size.width
        with(density) { width / 15.sp.toPx() } + 10f / 15f
    }
    val controlWidthEm = (if (shown.verified) 17f / 15f else 0f) +
        (if (showFollow) labelWidthEm + (if (shown.verified) 4f / 15f else 0f) else 0f)
    val badgeSize = with(density) { 17.sp.toDp() }
    val controlGap = with(density) { 4.sp.toDp() }
    val title = remember(name, hasControls) {
        buildAnnotatedString {
            append(name)
            if (hasControls) {
                append("\u00a0")
                appendInlineContent("author-controls")
            }
        }
    }
    val inline = if (!hasControls) emptyMap() else mapOf("author-controls" to InlineTextContent(
        // Relative units keep room for the complete controls with Android's nonlinear font scaling.
        Placeholder(controlWidthEm.em, (20f / 15f).em, PlaceholderVerticalAlign.TextCenter)
    ) {
        // One inline unit: neither the badge nor the Follow label can split across lines.
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(controlGap)) {
            if (shown.verified) Icon(HomeFeedIcons.verified, appDescription("Verified account"), tint = Color.Unspecified,
                modifier = Modifier.size(badgeSize))
            if (showFollow) Box(Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.surface(if (shown.following) Color(0xFFF1F5F9) else Color.Transparent))
                .clickable(enabled = enabled && !shown.pending && !shown.following,
                    role = Role.Button, onClickLabel = appText("Follow $name")) {
                    if (onFollow != null) onFollow() else controls.follow(post)
                }
                .semantics { stateDescription = appText(if (shown.pending) "Following in progress" else
                    if (shown.following) "Following" else "Not following") },
                contentAlignment = Alignment.Center) {
                Text(label, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false,
                    fontWeight = FontWeight.Bold, color = AppColors.content(if (shown.following) Color(0xFF475569)
                        else Color(0xFFFF1028).copy(alpha = if (shown.pending) 0.65f else 1f)),
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
    })
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val avatar = remember(post.author.profileImageUrl) { post.author.profileImageUrl?.let { homeFeedPreviewUrl(it, 192) } }
        ProfileAvatar(avatar, Modifier.size(44.dp).profileLink(post.author.id, post.author))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, inlineContent = inline, color = AppColors.content(FeedColors.text), fontSize = 15.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.fillMaxWidth().profileLink(post.author.id, post.author))
            Text("${appText(post.updateLabel)} · ${appText(postTimeAgo(post.createdAt, now))}", color = AppColors.content(FeedColors.muted),
                fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        IconButton(onClick = { if (onMenu != null) onMenu() else controls.openMenu(post) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(FeedIcons.more, appDescription("Post options"), tint = AppColors.content(FeedColors.muted), modifier = Modifier.size(22.dp))
        }
    }
}
