package com.mongosky.app.saveditems

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mongosky.app.home.homeFeedPreviewUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun SavedItemRow(item: SavedItem,enabled: Boolean,opening: Boolean,saving: Boolean,open: () -> Unit,remove: () -> Unit) {
    val colors = rememberSavedUiColors()
    var menu by remember(item.postId) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp),color = colors.surface,border = BorderStroke(1.dp,colors.border),modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled && item.available && !opening && !saving,role = Role.Button,onClickLabel = "Open saved post",onClick = open)
            .padding(start = 14.dp,top = 16.dp,bottom = 16.dp,end = 4.dp),verticalAlignment = Alignment.Top) {
            SavedThumbnail(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title,fontSize = 13.sp,fontWeight = FontWeight.SemiBold,color = colors.accent)
                item.author?.let { Text(it.displayName.ifBlank { "Mongosky User" },fontWeight = FontWeight.SemiBold,fontSize = 15.sp,color = colors.text,
                    maxLines = 1,overflow = TextOverflow.Ellipsis) }
                if (item.text.isNotBlank()) Text(item.text,fontSize = 14.sp,lineHeight = 20.sp,color = if (item.available) colors.text else colors.muted,maxLines = 3,overflow = TextOverflow.Ellipsis)
                Text("Saved ${item.savedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · h:mm a"))}",fontSize = 11.sp,color = colors.muted)
                if (opening || saving) Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp),strokeWidth = 1.8.dp,color = colors.accent)
                    Text(if (saving) "Updating…" else "Opening…",fontSize = 12.sp,color = colors.muted)
                }
            }
            Box {
                IconButton({ menu = true },enabled = enabled && !saving && !opening,modifier = Modifier.size(48.dp)) { Icon(SavedIcons.more,"Options for saved ${item.title}",tint = colors.muted) }
                DropdownMenu(menu,{ menu = false },containerColor = colors.surface,shape = RoundedCornerShape(14.dp)) {
                    if (item.available) DropdownMenuItem(text = { Text("View post") },onClick = { menu = false;open() })
                    DropdownMenuItem(text = { Text("Remove from saved") },leadingIcon = { Icon(SavedIcons.bookmark,null) },onClick = { menu = false;remove() })
                }
            }
        }
    }
}
@Composable private fun SavedThumbnail(item: SavedItem) {
    val colors = rememberSavedUiColors()
    val context = LocalContext.current.applicationContext;val pixels = with(LocalDensity.current) { 84.dp.roundToPx() }
    val url = item.thumbnail;val preview = remember(url,pixels) { url?.let { homeFeedPreviewUrl(it,pixels) } }
    var fallback by remember(url) { mutableStateOf(false) };val image = if (fallback) url else preview
    val request = remember(context,image,pixels) { image?.let { ImageRequest.Builder(context).data(it).size(pixels).crossfade(false).build() } }
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(13.dp)).background(colors.thumbnail),contentAlignment = Alignment.Center) {
        val icon = if (!item.available) SavedIcons.bookmark else when (item.kind) { SavedKind.TEXT -> SavedIcons.text;SavedKind.VIDEOS -> SavedIcons.video;else -> SavedIcons.photo }
        Icon(icon,null,tint = colors.accent,modifier = Modifier.size(30.dp))
        if (request != null) AsyncImage(model = request,contentDescription = null,modifier = Modifier.fillMaxSize(),contentScale = ContentScale.Crop,
            onError = { if (image != url) fallback = true })
        if (item.available && item.kind == SavedKind.VIDEOS) Surface(color = Color(0xAA123B30),shape = RoundedCornerShape(20.dp),modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(SavedIcons.video,null,tint = Color.White,modifier = Modifier.size(18.dp)) }
        }
    }
}
@Composable internal fun SavedMessage(title: String,body: String,action: String? = null,onAction: () -> Unit = {}) {
    val colors = rememberSavedUiColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp,vertical = 42.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(SavedIcons.bookmark,null,tint = colors.accent,modifier = Modifier.size(40.dp))
        Text(title,color = colors.text,fontSize = 19.sp,fontWeight = FontWeight.SemiBold)
        Text(body,color = colors.muted,fontSize = 14.sp,lineHeight = 21.sp,textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.let { Button(onAction,colors = ButtonDefaults.buttonColors(containerColor = colors.accent,contentColor = colors.onAccent)) { Text(it) } }
    }
}
@Composable internal fun SavedSkeleton() {
    val colors = rememberSavedUiColors()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(5) {
        Surface(color = colors.surface,shape = RoundedCornerShape(18.dp),modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(colors.skeleton))
                Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(colors.skeleton))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(colors.skeletonSecondary))
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(colors.skeletonSecondary))
                }
            }
        }
    } }
}
