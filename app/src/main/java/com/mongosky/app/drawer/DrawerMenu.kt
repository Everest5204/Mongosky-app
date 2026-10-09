package com.mongosky.app.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DrawerColors {
    val text = Color(0xFF111827)
    val muted = Color(0xFF667085)
    val selected = Color(0xFFF6F7F9)
    val border = Color(0xFFECECEC)
    val signOut = Color(0xFFFF3040)
}

@Composable
internal fun DrawerMenuItem(item: DrawerDestination, selectedRouteName: String, enabled: Boolean, onClick: () -> Unit) {
    val selected = item.routeName != null && item.routeName == selectedRouteName
    val color = if (item == DrawerDestination.SIGN_OUT) DrawerColors.signOut else DrawerColors.text
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
        .background(if (selected) DrawerColors.selected else Color.White)
        .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(DrawerIcons.forDestination(item), null, tint = color, modifier = Modifier.size(24.dp))
        Text(item.label, color = color, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
    }
}
