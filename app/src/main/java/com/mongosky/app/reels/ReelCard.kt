@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.mongosky.app.reels

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.post.compactCount
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.shared.ProfileAvatar

@Composable
internal fun ReelCard(
    reel: Reel, playback: ReelPlayback?, active: Boolean, verified: Boolean,
    love: ReelLoveState?, commentCount: Long, onLove: () -> Unit, onDoubleLove: () -> Unit,
    onComment: () -> Unit, onShare: () -> Unit, onProfile: () -> Unit, onClearError: () -> Unit
) {
    var expanded by rememberSaveable(reel.id) { mutableStateOf(false) }
    var heartKey by remember(reel.id) { mutableIntStateOf(0) }
    val heart = remember(reel.id) { Animatable(0f) }
    LaunchedEffect(heartKey) {
        if (heartKey > 0) {
            heart.snapTo(0f)
            heart.animateTo(0f, keyframes { durationMillis = 900; 0f at 0; 1.22f at 230; 1f at 400; 1f at 650; 0f at 900 })
        }
    }
    val loved = (if (love != null) love.reaction else reel.reaction) == PostReaction.LOVE
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(if (active) Color.Transparent else Color.Black))) {
        val captionHeight = maxHeight * 0.38f
        val landscape = maxHeight < 480.dp
        val side = if (landscape) 10.dp else 12.dp
        if ((!active || playback?.firstFrame != true) && reel.posterUrl != null) AsyncImage(
            model = reel.posterUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().pointerInput(reel.id, active, playback) {
            detectTapGestures(onTap = { if (active && playback?.error != true) playback?.toggle() },
                onDoubleTap = { if (active) { heartKey++; onDoubleLove() } })
        }.semantics { contentDescription = appText("Video. Tap to pause or play, double tap to love.") })
        if (active && (playback == null || playback.buffering) && playback?.error != true) CircularProgressIndicator(
            Modifier.align(Alignment.Center).size(38.dp), color = AppColors.content(Color.White), strokeWidth = 3.dp
        )
        if (active && playback?.manuallyPaused == true && !playback.error) ReelRoundButton(
            ReelsIcons.play, "Play video", onClick = playback::toggle,
            modifier = Modifier.align(Alignment.Center).size(60.dp), iconSize = 28
        )
        if (active && playback?.error == true) Column(
            Modifier.align(Alignment.Center).padding(24.dp).background(AppColors.surface(Color.Black.copy(alpha = 0.85f)), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(appText("Could not play this video."), color = AppColors.content(Color.White), fontSize = 14.sp)
            TextButton(playback::retry) { Text(appText("Retry"), color = AppColors.content(Color.White)) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f),
            0.18f to Color.Transparent, 0.42f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.2f),
            0.82f to Color.Black.copy(alpha = 0.62f), 1f to Color.Black.copy(alpha = 0.86f))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = side, end = 72.dp, bottom = if (landscape) 10.dp else 16.dp)
            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onProfile),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAvatar(reel.author.profileImageUrl, Modifier.size(38.dp).border(2.dp, AppColors.outline(Color.White.copy(alpha = 0.92f)), CircleShape))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reel.author.displayName.ifBlank { appText("Mongosky User") }, color = AppColors.content(Color.White),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (verified) Icon(ReelsIcons.seal, appDescription("Verified"), tint = Color.Unspecified,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp))
                    }
                    if (reel.username.isNotBlank()) Text("@${reel.username}", color = AppColors.content(Color.White.copy(alpha = 0.78f)),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (reel.caption.isNotBlank()) {
                val captionModifier = if (expanded) Modifier.heightIn(max = captionHeight).verticalScroll(rememberScrollState()) else Modifier
                Text(reel.caption, color = AppColors.content(Color.White), fontSize = 13.sp, lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    modifier = captionModifier)
                if (reel.caption.length > 120 || reel.caption.count { it == '\n' } >= 2) Text(
                    appText(if (expanded) "less" else "more"), color = AppColors.content(Color.White.copy(alpha = 0.86f)), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 32.dp).clickable { expanded = !expanded }.padding(top = 4.dp)
                )
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = side, bottom = if (landscape) 50.dp else 86.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 15.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    ReelRoundButton(if (loved) ReelsIcons.filledHeart else ReelsIcons.heart,
                        if (loved) "Remove love" else "Love reel", onLove, enabled = love?.saving != true,
                        filled = loved, tint = AppColors.content(if (loved) Color(0xFFFF3158) else Color.White))
                    if (love?.saving == true) CircularProgressIndicator(Modifier.size(48.dp), color = AppColors.content(Color.White), strokeWidth = 2.dp)
                }
                Text(compactCount(love?.count ?: reel.likesCount), color = AppColors.content(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            ReelAction(ReelsIcons.comment, "Open comments", compactCount(commentCount), onComment)
            ReelAction(ReelsIcons.share, "Share reel", "Share", onShare)
        }
        if (heart.value > 0f) Icon(ReelsIcons.filledHeart, null, tint = AppColors.content(Color(0xFFFF3158)),
            modifier = Modifier.align(Alignment.Center).size(104.dp).graphicsLayer {
                scaleX = heart.value; scaleY = heart.value; alpha = heart.value.coerceAtMost(1f)
            })
        if (active && love?.error != null) Row(Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 20.dp, end = 20.dp)
            .background(AppColors.surface(Color(0xFFAE1C36)), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(onClick = onClearError)
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(appText(love.error), color = AppColors.content(Color.White), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(" ×", color = AppColors.content(Color.White), fontSize = 22.sp)
        }
    }
}

@Composable
private fun ReelAction(icon: ImageVector, description: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ReelRoundButton(icon, description, onClick)
        Text(appText(label), color = AppColors.content(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ReelRoundButton(icon: ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp), enabled: Boolean = true, filled: Boolean = false,
    tint: Color = Color.White, iconSize: Int = 25) {
    Box(modifier.clip(CircleShape).background(AppColors.surface(if (filled) Color.White.copy(alpha = 0.94f) else Color(0xFF1C1C1C).copy(alpha = 0.64f)))
        .border(1.dp, AppColors.outline(Color.White.copy(alpha = if (filled) 0.96f else 0.24f)), CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = appText(description), onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, appDescription(description), tint = AppColors.content(tint), modifier = Modifier.size(iconSize.dp))
    }
}
