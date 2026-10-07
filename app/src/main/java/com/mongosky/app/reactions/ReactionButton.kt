@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.reactions

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
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.reactions.PostReactionState
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
internal fun ReactionButton(state: PostReactionState, enabled: Boolean, onReact: (PostReaction) -> Unit, modifier: Modifier) {
    val summary = state.summary
    var choicesOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        val active = summary.currentReaction
        Row(Modifier.fillMaxWidth().height(54.dp).combinedClickable(
            enabled = enabled && state.loaded && !state.saving,
            role = Role.Button,
            onLongClickLabel = "Choose reaction",
            onLongClick = { choicesOpen = true },
            onClick = { onReact(active ?: PostReaction.LOVE) }
        ).semantics { contentDescription = if (state.saving) "Saving reaction" else if (active == null) "Love. Hold to choose a reaction" else "${active.label} selected. Hold to choose a reaction" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (active == null) Icon(FeedIcons.heart, null, modifier = Modifier.size(22.dp), tint = FeedColors.muted)
            else Text(active.emoji, fontSize = 21.sp)
            Spacer(Modifier.width(6.dp))
            Text(active?.label ?: "Love", color = if (active != null) FeedColors.brand else FeedColors.muted,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        DropdownMenu(expanded = choicesOpen, onDismissRequest = { choicesOpen = false },
            offset = DpOffset(0.dp, (-118).dp), shape = RoundedCornerShape(28.dp),
            containerColor = Color.White, tonalElevation = 0.dp) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PostReaction.entries.forEach { reaction ->
                    Column(Modifier.size(50.dp).clip(CircleShape)
                        .background(if (active == reaction) FeedColors.line else Color.White)
                        .clickable(enabled = enabled && !state.saving, role = Role.Button) {
                            choicesOpen = false; onReact(reaction)
                        }.semantics { contentDescription = reaction.label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(reaction.emoji, fontSize = 24.sp)
                        Text(reaction.label, color = FeedColors.muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
