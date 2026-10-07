@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.reactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.profile.profileLink

private val ReactionText = Color(0xFF111827)
private val ReactionMuted = Color(0xFF64748B)
private val ReactionLine = Color(0xFFEEF0F3)
private val ReactionActive = Color(0xFFFF1028)
private val ReactionScrim = Color(0xFF0F172A).copy(alpha = 0.42f)

/** Native presentation of web ReactionDetailsModal, including its mobile breakpoint. */
@Composable
internal fun ReactionPeopleSheet(
    state: ReactionPeopleState,
    onDismiss: () -> Unit,
    onFilter: (PostReaction?) -> Unit,
    onRetry: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val mobile = configuration.screenWidthDp <= 640
    val maxHeight = if (mobile) configuration.screenHeightDp.dp * 0.86f
        else minOf(720.dp, configuration.screenHeightDp.dp * 0.92f)
    val badgeSize = if (configuration.screenWidthDp <= 480) 13.dp else 14.dp
    if (mobile) {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = null, containerColor = Color.White, tonalElevation = 0.dp,
            scrimColor = ReactionScrim) {
            ReactionPeoplePanel(state, maxHeight, mobile, badgeSize, onDismiss, onFilter, onRetry)
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(0f) }
            Box(Modifier.fillMaxSize().background(ReactionScrim).clickable(onClick = onDismiss)
                .padding(18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().shadow(24.dp, RoundedCornerShape(14.dp), clip = false).clip(RoundedCornerShape(14.dp))
                    .background(Color.White).pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
                    ReactionPeoplePanel(state, maxHeight, mobile, badgeSize, onDismiss, onFilter, onRetry)
                }
            }
        }
    }
}

@Composable
private fun ReactionPeoplePanel(
    state: ReactionPeopleState, maxHeight: Dp, mobile: Boolean, badgeSize: Dp,
    onDismiss: () -> Unit, onFilter: (PostReaction?) -> Unit, onRetry: () -> Unit
) {
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (mobile) 52.dp else 54.dp)
            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reactions", color = ReactionText, fontSize = 18.sp, fontWeight = FontWeight(850))
            IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                Icon(FeedIcons.close, "Close reactions", tint = ReactionText, modifier = Modifier.size(24.dp))
            }
        }
        ReactionDivider()
        val tabHeight = if (mobile) 54.dp else 56.dp
        Row(Modifier.fillMaxWidth().height(tabHeight).horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).semantics { contentDescription = "Reaction filters" },
            horizontalArrangement = Arrangement.spacedBy(if (mobile) 16.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (state.total > 0) ReactionFilterTab(null, state.total, state.filter == null, tabHeight, onFilter)
            PostReaction.entries.forEach { reaction ->
                val count = state.counts[reaction] ?: 0
                if (count > 0) ReactionFilterTab(reaction, count, state.filter == reaction, tabHeight, onFilter)
            }
        }
        ReactionDivider()
        when {
            state.loading -> ReactionListNotice("Loading reactions...")
            state.error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                ReactionListNotice(state.error)
                TextButton(onClick = onRetry) { Text("Retry", color = ReactionActive) }
            }
            state.people.isEmpty() -> ReactionListNotice("No reactions found.")
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.people, key = { it.id }, contentType = { "reactor" }) { person ->
                    ReactionPersonRow(person, person.author.id.lowercase() in state.verifiedAuthors,
                        mobile, badgeSize, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ReactionFilterTab(
    reaction: PostReaction?, count: Long, selected: Boolean, height: Dp,
    onSelect: (PostReaction?) -> Unit
) {
    val color = if (selected) ReactionActive else ReactionMuted
    Row(Modifier.height(height)
        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(reaction) })
        .semantics { contentDescription = "${reaction?.label ?: "All"}, $count reactions" }
        .drawBehind {
            if (selected) drawRect(ReactionActive, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(reaction?.emoji ?: "All", color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(count.toString(), color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ReactionPersonRow(
    person: ReactionPerson, verified: Boolean, mobile: Boolean, badgeSize: Dp, onDismiss: () -> Unit
) {
    val name = person.author.displayName.ifBlank { "Mongosky User" }
    Row(Modifier.fillMaxWidth().heightIn(min = if (mobile) 66.dp else 68.dp)
        .profileLink(person.author.id, person.author, beforeOpen = onDismiss)
        .semantics { contentDescription = "Open $name's profile, ${person.reaction.label} reaction" }
        .padding(horizontal = if (mobile) 14.dp else 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(46.dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF07927C)),
                contentAlignment = Alignment.Center) {
                Icon(ReactionPeopleIcons.user, null, tint = Color(0xFFEAFFF7), modifier = Modifier.size(22.dp))
                person.author.profileImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(21.dp)
                .drawBehind { drawCircle(Color.White, radius = size.minDimension / 2 + 2.dp.toPx()) }
                .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(person.reaction.emoji, fontSize = 14.sp, lineHeight = 17.sp)
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.6.dp)) {
            Text(name, modifier = Modifier.weight(1f, fill = false), color = ReactionText,
                fontSize = if (mobile) 15.sp else 16.sp, fontWeight = FontWeight(850),
                lineHeight = if (mobile) 18.sp else 19.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (verified) Icon(ReactionPeopleIcons.seal, "Verified account", tint = Color.Unspecified,
                modifier = Modifier.size(badgeSize))
        }
    }
}

@Composable
private fun ReactionListNotice(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 36.dp),
        color = ReactionMuted, fontSize = 14.sp, fontWeight = FontWeight(650),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun ReactionDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(ReactionLine)) }

private object ReactionPeopleIcons {
    val user by lazy { ImageVector.Builder("User", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M20 21v-2a7 7 0 0 0-14 0v2 M16 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0").toNodes(),
            stroke = SolidColor(Color.White), strokeLineWidth = 3.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build() }
    val seal by lazy { ImageVector.Builder("Verified", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.51L3.44 9.2L1 12L3.44 14.79L3.1 18.48L6.71 19.3L8.6 22.5L12 21.04L15.4 22.5L17.29 19.31L20.9 18.49L20.56 14.8Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
        addPath(PathParser().parsePathString("M10.09 16.17L6.5 12.58L7.91 11.17L10.09 13.34L16.09 7.34L17.5 8.76Z").toNodes(), fill = SolidColor(Color.White))
    }.build() }
}
