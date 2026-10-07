@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.profile.profileLink
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.reactions.ReactionButton
import com.mongosky.app.reactions.ReactionSummaryRow
import java.time.Duration
import java.time.Instant
import java.util.Locale

internal object FeedColors {
    val brand = Color(0xFFE60023)
    val text = Color(0xFF111827)
    val muted = Color(0xFF6B7280)
    val line = Color(0xFFEEF0F3)
    val soft = Color(0xFFF8F9FA)
}

data class PostCardActions(
    val onReact: (PostReaction) -> Unit,
    val onComments: () -> Unit,
    val onShare: () -> Unit,
    val onCopyLink: () -> Unit,
    val onReactionPeople: () -> Unit,
    val onRetryReaction: () -> Unit
)

@Composable
internal fun PostHeader(author: FeedAuthor, createdAt: Instant, now: Instant, actions: PostCardActions, action: String = "shared a post") {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PostAvatar(author, Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(author.displayName.ifBlank { "Mongosky User" }, color = FeedColors.text, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.profileLink(author.id, author))
            Text("$action · ${postTimeAgo(createdAt, now)}", color = FeedColors.muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(FeedIcons.more, "Post options", tint = FeedColors.muted, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp), containerColor = Color.White, tonalElevation = 0.dp) {
                DropdownMenuItem(text = { Text("Copy link", fontSize = 15.sp) }, onClick = { menuOpen = false; actions.onCopyLink() })
                DropdownMenuItem(text = { Text("Share", fontSize = 15.sp) }, onClick = { menuOpen = false; actions.onShare() })
            }
        }
    }
}

@Composable
internal fun PostAvatar(author: FeedAuthor, modifier: Modifier = Modifier.size(40.dp)) {
    Box(modifier.clip(CircleShape).profileLink(author.id, author).background(Color(0xFF07927C)), contentAlignment = Alignment.Center) {
        Icon(FeedIcons.user, null, tint = Color(0xFFEAFFF7), modifier = Modifier.size(24.dp))
        author.profileImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun PostFooter(state: PostReactionState, commentsCount: Long, enabled: Boolean, actions: PostCardActions) {
    val summary = state.summary
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        FeedDivider()
        if (summary.total > 0) {
            ReactionSummaryRow(summary, enabled, actions.onReactionPeople)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReactionButton(state, enabled, actions.onReact, Modifier.weight(1f))
            FooterButton(FeedIcons.comment, if (commentsCount > 0) "${compactCount(commentsCount)} Comments" else "Comment",
                "Open comments, $commentsCount total", enabled, actions.onComments, Modifier.weight(1f))
            FooterButton(FeedIcons.share, "Share", "Share post", enabled, actions.onShare, Modifier.weight(1f))
        }
        if (state.error != null) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error, color = FeedColors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onRetryReaction, enabled = enabled && !state.loading && !state.saving) { Text("Retry", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun FooterButton(icon: ImageVector, label: String, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.height(54.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = FeedColors.muted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = FeedColors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun FeedDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(FeedColors.line)) }

@Composable
internal fun PostCaption(caption: String) {
    if (caption.isBlank()) return
    var expanded by rememberSaveable(caption) { mutableStateOf(false) }
    var overflow by remember(caption) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
        Text(caption, color = FeedColors.text, fontSize = 15.sp, lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
        if (expanded || overflow) {
            Text(if (expanded) "See less" else "See more", color = FeedColors.muted, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }.padding(vertical = 8.dp))
        }
    }
}

internal fun compactCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
    else -> count.coerceAtLeast(0).toString()
}

internal fun postTimeAgo(time: Instant, now: Instant): String {
    val seconds = Duration.between(time, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "Just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        seconds < 2_592_000 -> "${seconds / 86400}d ago"
        seconds < 31_536_000 -> "${seconds / 2_592_000}mo ago"
        else -> "${seconds / 31_536_000}y ago"
    }
}

/** Small local vectors, shared by the feed and its sheets. */
internal object FeedIcons {
    val heart by lazy { icon("Love", "M20.8 4.6A5.5 5.5 0 0 0 13 4.6L12 5.6L11 4.6A5.5 5.5 0 0 0 3.2 12.4L12 21L20.8 12.4A5.5 5.5 0 0 0 20.8 4.6Z") }
    val comment by lazy { icon("Comments", "M21 11.5A9 9 0 0 1 7.1 19.1L3 21L4.9 16.9A9 9 0 1 1 21 11.5Z") }
    val share by lazy { icon("Share", "M22 2L9 15M22 2L15 22L9 15L2 9Z") }
    val more by lazy { icon("Options", "M5 12H5.02M12 12H12.02M19 12H19.02", 3.5f) }
    val close by lazy { icon("Close", "M6 6L18 18M18 6L6 18") }
    val back by lazy { icon("Back", "M19 12H5M12 5L5 12L12 19") }
    val user by lazy { icon("User", "M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7M4 21V19A6 6 0 0 1 10 13H14A6 6 0 0 1 20 19V21") }
    val image by lazy { icon("Image", "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3ZM10 8A2 2 0 1 1 6 8A2 2 0 1 1 10 8M21 15L16 10L5 21") }
    private fun icon(name: String, data: String, stroke: Float = 1.9f) = ImageVector.Builder(
        name = "Feed$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        addPath(pathData = PathParser().parsePathString(data).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}
