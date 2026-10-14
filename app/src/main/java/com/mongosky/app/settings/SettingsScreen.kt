package com.mongosky.app.settings

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Heading = Color(0xFF111827)
private val Danger = Color(0xFFC62828)

/** UI and route availability are separate; future sections enable only their completed menu IDs. */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    enabledMenus: Set<SettingsMenuId> = emptySet(),
    onMenuSelected: ((SettingsMenuId) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val scroll = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth <= 600.dp
        val compact = maxWidth <= 360.dp
        val tablet = maxWidth >= 768.dp
        Column(Modifier.fillMaxSize().background(AppColors.surface(if (phone) Color.White else Color(0xFFF5F7F6)))) {
            SettingsHeader(onBack, tablet, compact)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Surface(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                        .padding(start = if (phone) 0.dp else if (tablet) 24.dp else 18.dp,
                            end = if (phone) 0.dp else if (tablet) 24.dp else 18.dp,
                            top = if (phone) 0.dp else if (tablet) 32.dp else 24.dp,
                            bottom = if (phone) 0.dp else if (tablet) 48.dp else 40.dp),
                    color = AppColors.surface(Color.White), shape = RoundedCornerShape(if (phone) 0.dp else 18.dp),
                    border = if (phone) null else BorderStroke(1.dp, AppColors.outline(Color(0xFFE5E7EB))),
                    shadowElevation = if (phone) 0.dp else 5.dp
                ) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = if (phone) 30.dp else 0.dp)) {
                        itemsIndexed(SettingsContent.items, key = { _, item -> item.id.webId }) { index, item ->
                            val enabled = onMenuSelected != null && item.id in enabledMenus
                            Box {
                                SettingsMenuRow(item, enabled, compact, tablet, phone,
                                    onClick = { if (enabled) onMenuSelected?.invoke(item.id) })
                                // Menu rows are separated by their existing spacing.
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SettingsHeader(onBack: () -> Unit, tablet: Boolean, compact: Boolean) {
    Surface(color = AppColors.surface(Color.White), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (tablet) 68.dp else 64.dp)
            .padding(horizontal = if (compact) 10.dp else if (tablet) 24.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(SettingsIcons.back, "Return to previous page", tint = AppColors.content(Heading), modifier = Modifier.size(22.dp))
            }
            Text("Settings", color = AppColors.content(Heading), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
                fontSize = if (tablet) 20.sp else 18.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() })
            Spacer(Modifier.width(48.dp))
        }
    }
}
@Composable
private fun SettingsMenuRow(item: SettingsMenuItem, enabled: Boolean, compact: Boolean, tablet: Boolean, phone: Boolean, onClick: () -> Unit) {
    val color = if (item.danger) Danger else Heading
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (phone) 64.dp else if (tablet) 72.dp else 68.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (!enabled) stateDescription = "Not available yet" }
            .padding(horizontal = if (compact) 16.dp else if (tablet) 24.dp else 20.dp,
                vertical = if (phone) 11.dp else if (tablet) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (tablet) 18.dp else if (phone) 16.dp else 17.dp)
    ) {
        Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
            Icon(SettingsIcons.forMenu(item.id), null, tint = AppColors.content(color), modifier = Modifier.size(23.dp))
        }
        Text(item.label, color = AppColors.content(color), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = if (phone) 16.sp else if (tablet) 18.sp else 17.sp,
            lineHeight = if (phone) 22.sp else 24.sp, modifier = Modifier.weight(1f))
        Icon(SettingsIcons.chevron, null, tint = AppColors.content(Color(0xFF667085)), modifier = Modifier.size(21.dp))
    }
}
