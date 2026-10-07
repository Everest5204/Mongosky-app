@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.mongosky.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.shared.ProfileAvatar

@Composable
internal fun UserProfileHeader(state: UserProfileState, onBack: () -> Unit,
    onConnections: (ProfileConnectionKind) -> Unit, onFollow: () -> Unit, onMessage: () -> Unit, onOptions: () -> Unit) {
    val profile = state.profile
    val enabled = profile != null && !state.unavailable && !state.sessionExpired
    val name = (profile?.displayName ?: state.hint?.displayName).orEmpty().ifBlank { "Mongosky User" }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth <= 420.dp
        val coverHeight = if (narrow) 210.dp else if (maxWidth <= 680.dp) 235.dp else 270.dp
        val avatarSize = if (narrow) 118.dp else 132.dp
        val overlap = if (narrow) 65.dp else 74.dp
        val inset = if (narrow) 14.dp else 16.dp
        Column {
            Box(Modifier.fillMaxWidth().height(coverHeight).clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8))))) {
                profile?.coverUrl?.let { AsyncImage(it, "$name's cover photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                IconButton(onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = inset, top = 12.dp)
                    .size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.38f))) {
                    Icon(OwnProfileIcons.back, "Back", Modifier.size(25.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(avatarSize - overlap + 22.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = inset)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    BasicText(name.replace(Regex("\\s+"), " "), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(color = Color(0xFF05070A), fontWeight = FontWeight.Black,
                            fontSize = if (narrow) 29.sp else 32.sp, lineHeight = 1.2.em, letterSpacing = (-0.8).sp),
                        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = if (narrow) 29.sp else 32.sp, stepSize = 0.5.sp),
                        modifier = Modifier.weight(1f, fill = false))
                    if (state.verified) Icon(OwnProfileIcons.verified, "Verified account", Modifier.size(21.dp), tint = Color.Unspecified)
                }
                profile?.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 7.dp))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    UserProfileStat(profile?.followersCount, "Followers", enabled) { onConnections(ProfileConnectionKind.FOLLOWERS) }
                    UserProfileStat(profile?.followingCount, "Following", enabled) { onConnections(ProfileConnectionKind.FOLLOWING) }
                    UserProfileStat(profile?.postsCount, "Posts", false) {}
                }
                profile?.bio?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFF334155), fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 7.dp))
                }
                Spacer(Modifier.height(21.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onFollow, enabled = enabled && !state.followingBusy && !state.followingUncertain && profile?.isSelf != true,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.buttonColors(
                            containerColor = if (profile?.isFollowing == true) Color(0xFFEEF1F5) else Color(0xFFF0142F),
                            contentColor = if (profile?.isFollowing == true) Color(0xFF0F172A) else Color.White)) {
                        if (state.followingBusy) OwnProfileSpinner(color = if (profile?.isFollowing == true) OwnProfileColors.brand else Color.White)
                        else Text(if (profile?.isFollowing == true) "Following" else "Follow", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onMessage, enabled = enabled && profile?.canMessage == true,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEF1F5), contentColor = Color(0xFF0F172A))) {
                        Text("Message", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onOptions, enabled = enabled,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFEEF1F5))) {
                        Icon(FeedIcons.more, "Profile options", tint = Color(0xFF0F172A))
                    }
                }
                Spacer(Modifier.height(31.dp))
            }
        }
        Box(Modifier.padding(start = inset).offset(y = coverHeight - overlap).size(avatarSize)
            .clip(CircleShape).background(Color.White).border(5.dp, Color.White, CircleShape).padding(5.dp)) {
            ProfileAvatar(if (profile != null) profile.imageUrl else state.hint?.profileImageUrl, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun UserProfileStat(count: Long?, label: String, enabled: Boolean, onClick: () -> Unit) {
    val base = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp))
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(count?.toString() ?: "—") }
        append(" $label")
    }, color = Color(0xFF0F172A), fontSize = 14.sp,
        modifier = (if (enabled) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 2.dp, vertical = 14.dp))
}
