package com.mongosky.app.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mongosky.app.shared.ProfileAvatar
import java.util.Locale

@Composable
internal fun DrawerHeader(state: DrawerState, userName: String, profileImageUrl: String?,
    enabled: Boolean, onProfile: () -> Unit, onClose: () -> Unit) {
    val profile = state.profile
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: userName.ifBlank { "Mongosky" }
    val initial = remember(name) { String(Character.toChars(name.codePointAt(0))).uppercase(Locale.ROOT) }
    val verified = state.badgeKnown && state.verified && !state.sessionExpired
    val title = remember(name, verified) { buildAnnotatedString {
        append(name)
        if (verified) { append("\u00a0"); appendInlineContent("verified") }
    } }
    val badgeSize = with(LocalDensity.current) { 16.sp.toDp() }
    val inline = if (!verified) emptyMap() else mapOf("verified" to InlineTextContent(
        Placeholder(0.8.em, 0.8.em, PlaceholderVerticalAlign.TextCenter)
    ) { Icon(DrawerIcons.verified, "Verified account", tint = Color.Unspecified, modifier = Modifier.size(badgeSize)) })
    Row(Modifier.fillMaxWidth().heightIn(min = 90.dp).padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button,
            onClickLabel = "Open your profile", onClick = onProfile),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileAvatar(if (profile == null) profileImageUrl else profile.imageUrl, Modifier.size(54.dp), initial)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, inlineContent = inline, modifier = Modifier.fillMaxWidth(), color = DrawerColors.text,
                    fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                val counts = if (profile == null) "— Following • — Followers"
                    else "${profile.followingCount} Following • ${profile.followersCount} Followers"
                Text(counts, color = DrawerColors.muted, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(DrawerIcons.close, "Close menu", tint = DrawerColors.text, modifier = Modifier.size(30.dp))
        }
    }
}
