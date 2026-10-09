package com.mongosky.app.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import java.util.Locale

internal object FriendsColors {
    val brand = Color(0xFFE6003D)
    val text = Color(0xFF111827)
    val secondary = Color(0xFF6B7280)
    val border = Color(0xFFD7DCE4)
    val canvas = Color(0xFFEEF1F5)
    val avatar = Color(0xFF07927C)
    val skeleton = Color(0xFFF1F3F6)
}

internal fun friendsCount(value: Long): String {
    val units = listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    val unit = units.firstOrNull { value >= it.first } ?: return value.toString()
    return String.format(Locale.ROOT, "%.1f", value.toDouble() / unit.first).removeSuffix(".0") + unit.second
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun FriendsPersonRow(
    user: FriendsUser, pending: Boolean, uncertain: Boolean, actionEnabled: Boolean,
    horizontalPadding: Dp, onProfile: () -> Unit, onFollow: () -> Unit
) {
    // Mongosky Friends row v1.1.4: complete names and a compact 80 dp button.
    val name = remember(user.fullName, user.verified) {
        buildAnnotatedString {
            append(user.fullName)
            if (user.verified) {
                append("\u00a0")
                appendInlineContent("verified", "Verified account")
            }
        }
    }
    val badge = remember {
        mapOf("verified" to InlineTextContent(
            Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(FriendsIcons.verified, "Verified account", tint = Color.Unspecified,
                modifier = Modifier.fillMaxWidth())
        })
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = (horizontalPadding - 2.dp).coerceAtLeast(0.dp), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${user.fullName}'s profile", onClick = onProfile),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FriendsAvatar(user)
            Text(name, inlineContent = badge, modifier = Modifier.weight(1f), color = FriendsColors.text,
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                softWrap = true,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        if (!user.isSelf) {
            val foreground = if (user.isFollowing) Color(0xFF253047) else Color.White
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(onClick = onFollow, enabled = actionEnabled && !pending && !uncertain,
                        modifier = Modifier.width(80.dp).heightIn(min = 32.dp).semantics {
                            contentDescription = when {
                                pending -> "Updating follow state for ${user.fullName}"
                                uncertain -> "Refresh to confirm follow state for ${user.fullName}"
                                user.isFollowing -> "Unfollow ${user.fullName}"
                                else -> "${user.actionLabel} ${user.fullName}"
                            }
                        }, shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (user.isFollowing) Color.White else FriendsColors.brand,
                            contentColor = foreground,
                            disabledContainerColor = if (user.isFollowing) Color.White else FriendsColors.brand.copy(alpha = .65f),
                            disabledContentColor = foreground.copy(alpha = .7f)),
                        border = if (user.isFollowing) BorderStroke(1.dp, FriendsColors.border) else null,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(user.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsAvatar(user: FriendsUser) {
    val context = LocalContext.current
    var failed by remember(user.id, user.imageUrl) { mutableStateOf(false) }
    val request = remember(user.imageUrl, context) {
        user.imageUrl?.let { ImageRequest.Builder(context).data(it).crossfade(false).build() }
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(FriendsColors.avatar), contentAlignment = Alignment.Center) {
        Icon(FriendsIcons.person, null, tint = Color.White, modifier = Modifier.size(22.dp))
        if (request != null && !failed) AsyncImage(model = request, contentDescription = null,
            modifier = Modifier.size(40.dp), contentScale = ContentScale.Crop, onError = { failed = true })
    }
}

@Composable
internal fun FriendsSkeleton(padding: Dp) {
    Column(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 8.dp)) {
        repeat(7) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(FriendsColors.skeleton))
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(.72f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(FriendsColors.skeleton))
                }
                Box(Modifier.width(104.dp).height(48.dp).clip(RoundedCornerShape(12.dp)).background(FriendsColors.skeleton))
            }
        }
    }
}

@Composable
internal fun FriendsStatePanel(title: String, message: String, icon: ImageVector, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.padding(horizontal = 28.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(FriendsColors.brand.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = FriendsColors.brand)
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FriendsColors.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(message, fontSize = 14.sp, color = FriendsColors.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel, color = FriendsColors.brand, fontWeight = FontWeight.SemiBold) }
    }
}
