package com.mongosky.app.myactivity

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.mongosky.app.home.homeFeedPreviewUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val ActivityGreen = Color(0xFF07927C)
internal val ActivityBackground = Color(0xFFF4F7F6)
internal val ActivityText = Color(0xFF172B28)
internal val ActivityMuted = Color(0xFF647570)

@Composable internal fun ActivityRow(item: ActivityItem, enabled: Boolean, opening: Boolean,
    open: () -> Unit, remove: () -> Unit) {
    var menu by remember(item.key) { mutableStateOf(false) }
    val icon = when (item.category) {
        ActivityCategory.POSTS -> ActivityIcons.post
        ActivityCategory.REACTIONS -> ActivityIcons.heart
        ActivityCategory.COMMENTS -> ActivityIcons.comment
        else -> ActivityIcons.people
    }
    Surface(shape = RoundedCornerShape(18.dp), color = AppColors.surface(Color.White), border = BorderStroke(1.dp,AppColors.outline(Color(0xFFE6EEEA))),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(enabled = enabled && item.available && !opening, role = Role.Button,
            onClickLabel = if (item.kind == "follow") "View profile" else "View post", onClick = open)
            .padding(start = 14.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.surface(Color(0xFFE9F6F1))), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppColors.content(ActivityGreen), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = AppColors.content(ActivityText))
                item.author?.let { Text(it.displayName.ifBlank { "Mongosky User" }, fontSize = 13.sp, color = AppColors.content(ActivityMuted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (item.text.isNotBlank()) Text(item.text, fontSize = 13.sp, lineHeight = 19.sp, color = AppColors.content(ActivityMuted),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")), fontSize = 12.sp, color = AppColors.content(ActivityMuted))
                    if (opening) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp, color = AppColors.content(ActivityGreen))
                    else if (item.available) Text(if (item.kind == "follow") "View profile" else "View post", fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = AppColors.content(ActivityGreen))
                }
                item.thumbnail?.let { ActivityThumbnail(it) }
            }
            if (item.action != ActivityAction.NONE) Box {
                IconButton(onClick = { menu = true }, enabled = enabled && !opening, modifier = Modifier.size(48.dp)) {
                    Icon(ActivityIcons.more,"Options for ${item.title}",tint = AppColors.content(ActivityMuted))
                }
                DropdownMenu(menu, { menu = false }, containerColor = AppColors.surface(Color.White), shape = RoundedCornerShape(14.dp)) {
                    if (item.available) DropdownMenuItem(text = { Text("View post") }, onClick = { menu = false; open() })
                    DropdownMenuItem(text = { Text(item.action.label, color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; remove() })
                }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable internal fun ActivityMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(ActivityIcons.history,null,tint = AppColors.content(ActivityGreen),modifier = Modifier.size(38.dp))
        Text(title, color = AppColors.content(ActivityText), fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        Text(body, color = AppColors.content(ActivityMuted), fontSize = 14.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.let { Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(ActivityGreen))) { Text(it) } }
    }
}

@Composable internal fun ActivitySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) {
            Surface(shape = RoundedCornerShape(18.dp), color = AppColors.surface(Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.surface(Color(0xFFEAF0ED))))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.surface(Color(0xFFEAF0ED))))
                        Box(Modifier.fillMaxWidth(0.6f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.surface(Color(0xFFF0F4F2))))
                        Box(Modifier.width(80.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.surface(Color(0xFFF0F4F2))))
                    }
                }
            }
        }
    }
}

@Composable private fun ActivityThumbnail(url: String) {
    val context = LocalContext.current.applicationContext
    val pixels = with(LocalDensity.current) { 64.dp.roundToPx() }
    val preview = remember(url,pixels) { homeFeedPreviewUrl(url,pixels) }
    var fallback by remember(url) { mutableStateOf(false) }
    val image = if (fallback) url else preview
    val request = remember(context,image,pixels) { ImageRequest.Builder(context).data(image).size(pixels).crossfade(false).build() }
    AsyncImage(model = request,contentDescription = null,modifier = Modifier.padding(top = 3.dp).size(64.dp)
        .clip(RoundedCornerShape(9.dp)).background(AppColors.surface(ActivityBackground)),contentScale = ContentScale.Crop,
        onError = { if (image != url) fallback = true })
}
