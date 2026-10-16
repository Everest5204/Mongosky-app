package com.mongosky.app.editmediapost

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import com.mongosky.app.editpost.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.post.*
import com.mongosky.app.mediapost.PostMediaKind

private data class EditMediaPreview(val key: String, val removeKey: String, val url: String, val video: Boolean)

@Composable
internal fun EditMediaPostEditor(state: EditMediaPostState, onText: (String) -> Unit,
                                 onRemove: (String) -> Unit, onAdd: () -> Unit) {
    val items = remember(state.keptMedia, state.selectedMedia) {
        state.keptMedia.map { media -> val key = EditPostPolicy.mediaKey(media)
            EditMediaPreview("existing:$key", key, media.url, media.type == FeedMediaType.VIDEO) } +
            state.selectedMedia.map { EditMediaPreview("new:${it.id}", it.id, it.uri, it.kind == PostMediaKind.VIDEO) }
    }
    var activeIndex by rememberSaveable(state.post.id) { mutableIntStateOf(0) }
    var previousSelectedCount by rememberSaveable(state.post.id) { mutableIntStateOf(state.selectedMedia.size) }
    LaunchedEffect(state.selectedMedia.size) {
        if (state.selectedMedia.size > previousSelectedCount) activeIndex = state.keptMedia.size + previousSelectedCount
        previousSelectedCount = state.selectedMedia.size
    }
    val index = activeIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val active = items.getOrNull(index)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            BasicTextField(state.text, onText, enabled = state.editable,
                textStyle = TextStyle(color = AppColors.content(EditPostColors.text), fontSize = 18.sp, lineHeight = 28.sp),
                cursorBrush = SolidColor(EditPostColors.mediaGreen), minLines = 3, maxLines = 7,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(bottom = 24.dp)
                    .semantics { contentDescription = appText("Post caption") },
                decorationBox = { field ->
                    Box { if (state.text.isEmpty()) Text(appText("Write a caption…"), color = AppColors.content(Color(0xFF9CA3AF)), fontSize = 18.sp); field() }
                })
            if (state.length >= 2_000) Text("${state.length}/${state.limit}", color = AppColors.content(EditPostColors.muted), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
        }
        if (active != null) {
            val height = (LocalConfiguration.current.screenHeightDp.dp * 0.55f).coerceIn(240.dp, 440.dp)
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(height).clip(RoundedCornerShape(4.dp))
                .background(AppColors.surface(EditPostColors.soft)).border(1.dp, AppColors.outline(EditPostColors.line), RoundedCornerShape(4.dp))) {
                key(active.key) {
                    if (active.video) EditMediaPostVideoPreview(active.url, enabled = state.editable)
                    else AsyncImage(active.url, appDescription("Post image ${index + 1}"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                if (items.size > 1) Text("${index + 1}/${items.size}", color = AppColors.content(Color.White), fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(AppColors.surface(Color(0xC414171C)), RoundedCornerShape(30.dp)).padding(horizontal = 11.dp, vertical = 7.dp))
                IconButton({ onRemove(active.removeKey); activeIndex = index.coerceAtMost((items.size - 2).coerceAtLeast(0)) },
                    enabled = state.editable, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)
                        .background(AppColors.surface(Color(0xC414171C)), CircleShape)) {
                    Icon(FeedIcons.close, appDescription("Remove selected media"), tint = AppColors.content(Color.White), modifier = Modifier.size(24.dp))
                }
            }
            if (items.size > 1) LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                itemsIndexed(items, key = { _, media -> media.key }) { position, media ->
                    val shape = RoundedCornerShape(10.dp)
                    Box(Modifier.size(64.dp).clip(shape).background(AppColors.surface(EditPostColors.soft))
                        .border(2.dp, AppColors.outline(if (position == index) EditPostColors.mediaGreen else Color.Transparent), shape)
                        .clickable(enabled = !state.saving, role = Role.RadioButton) { activeIndex = position }
                        .semantics { contentDescription = appText("Preview image ${position + 1}"); selected = position == index }) {
                        AsyncImage(media.url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(2.dp).clip(shape))
                    }
                }
            }
            if (state.canAddMedia) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(12.dp)).clickable(enabled = state.editable, role = Role.Button, onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(AppColors.surface(Color(0xFFE9EDF1)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EditPostIcons.addMedia, null, tint = AppColors.content(Color(0xFF465166)), modifier = Modifier.size(24.dp))
                }
                Column { Text(appText("Add more"), fontWeight = FontWeight.Bold, color = AppColors.content(EditPostColors.text), fontSize = 15.sp)
                    Text(appText("${items.size}/10 selected"), color = AppColors.content(EditPostColors.muted), fontSize = 12.sp) }
            }
        } else Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 260.dp)
            .clip(RoundedCornerShape(14.dp)).background(AppColors.surface(Color(0xFFF8FAFC)))
            .border(1.dp, AppColors.outline(Color(0xFFCBD5E1)), RoundedCornerShape(14.dp))
            .clickable(enabled = state.canAddMedia, role = Role.Button, onClick = onAdd).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)) {
            Icon(EditPostIcons.addMedia, null, tint = AppColors.content(Color(0xFF465166)), modifier = Modifier.size(31.dp))
            Text(appText("Add an image or video"), color = AppColors.content(Color(0xFF475569)), fontWeight = FontWeight.Bold)
            Text(appText("A media post needs at least one file"), color = AppColors.content(EditPostColors.muted), fontSize = 12.sp)
        }
    }
}
