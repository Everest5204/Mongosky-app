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
import com.mongosky.app.profile.OwnProfileState
import com.mongosky.app.shared.ProfileAvatar

@Composable
internal fun OwnProfileHeader(
    state: OwnProfileState, userName: String, cachedAvatar: String?,
    onImage: (ProfileImageKind) -> Unit, onConnections: (ProfileConnectionKind) -> Unit,
    onStory: () -> Unit, onEdit: () -> Unit, onOptions: () -> Unit,
    onBack: () -> Unit, backEnabled: Boolean
) {
    val profile = state.profile
    val enabled = profile != null && !state.sessionExpired
    val imageEnabled = enabled && !state.busy && !state.uploadUncertain
    val avatar = if (profile != null) profile.profileImageUrl else cachedAvatar
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val small = maxWidth < 480.dp
        val coverHeight = if (small) 215.dp else 255.dp
        val avatarSize = if (small) 118.dp else 142.dp
        val overlap = if (small) 72.dp else 88.dp
        val inset = if (small) 14.dp else 16.dp
        Column {
            Box(Modifier.fillMaxWidth().height(coverHeight).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFDDDDDD))) {
                profile?.coverImageUrl?.let { url ->
                    AsyncImage(url, "Your cover photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (state.uploading == ProfileImageKind.COVER) UploadOverlay()
                IconButton(onBack, enabled = backEnabled,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = inset, top = 14.dp)
                        .size(48.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.38f))) {
                    Icon(OwnProfileIcons.back, "Back", Modifier.size(25.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
                CameraButton("Change cover photo", imageEnabled, { onImage(ProfileImageKind.COVER) },
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 16.dp))
            }
            Spacer(Modifier.height(avatarSize - overlap + 28.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = inset)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    BasicText((profile?.displayName?.ifBlank { userName } ?: userName)
                        .replace(Regex("\\s+"), " ").trim(),
                        style = LocalTextStyle.current.copy(color = OwnProfileColors.text,
                            fontSize = if (small) 29.sp else 34.sp, fontWeight = FontWeight.Black,
                            lineHeight = 1.2.em, letterSpacing = (-0.5).sp),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp,
                            maxFontSize = if (small) 29.sp else 34.sp, stepSize = 0.5.sp),
                        modifier = Modifier.weight(1f, fill = false))
                    if (state.verified) Icon(OwnProfileIcons.verified, "Verified account",
                        Modifier.size(if (small) 19.dp else 21.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    ProfileStat(profile?.followersCount, "followers", enabled) { onConnections(ProfileConnectionKind.FOLLOWERS) }
                    ProfileStat(profile?.followingCount, "following", enabled) { onConnections(ProfileConnectionKind.FOLLOWING) }
                    ProfileStat(profile?.postsCount?.coerceAtLeast(state.posts.size.toLong()), "posts", false) {}
                }
                profile?.bio?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = OwnProfileColors.text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onStory, enabled = !state.sessionExpired, modifier = Modifier.weight(1.05f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OwnProfileColors.brand, contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Icon(OwnProfileIcons.plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text("Add Story", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onEdit, enabled = enabled && !state.busy, modifier = Modifier.weight(1.08f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OwnProfileColors.soft, contentColor = OwnProfileColors.text)) {
                        Icon(FeedIcons.user, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text("Edit Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onOptions, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(5.dp)).background(OwnProfileColors.soft)) {
                        Icon(FeedIcons.more, "Profile options", tint = OwnProfileColors.text)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        Box(Modifier.padding(start = inset).offset(y = coverHeight - overlap).size(avatarSize)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(androidx.compose.ui.graphics.Color.White).border(5.dp, androidx.compose.ui.graphics.Color.White, CircleShape).padding(5.dp)) {
                ProfileAvatar(avatar, Modifier.fillMaxSize())
                if (state.uploading == ProfileImageKind.AVATAR) UploadOverlay()
            }
            CameraButton("Change profile photo", imageEnabled, { onImage(ProfileImageKind.AVATAR) }, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun ProfileStat(count: Long?, label: String, enabled: Boolean, onClick: () -> Unit) {
    val base = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp))
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(count?.toString() ?: "—") }
        append(" $label")
    }, color = OwnProfileColors.text, fontSize = 14.sp,
        modifier = (if (enabled) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(vertical = 14.dp, horizontal = 3.dp))
}

@Composable
private fun CameraButton(description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    IconButton(onClick, enabled = enabled, modifier = modifier.size(48.dp).clip(CircleShape)
        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f)).border(1.dp, OwnProfileColors.line, CircleShape)) {
        Icon(OwnProfileIcons.camera, description, Modifier.size(21.dp), tint = if (enabled) OwnProfileColors.text else OwnProfileColors.muted)
    }
}

@Composable
private fun UploadOverlay() {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        OwnProfileSpinner(color = androidx.compose.ui.graphics.Color.White)
    }
}
