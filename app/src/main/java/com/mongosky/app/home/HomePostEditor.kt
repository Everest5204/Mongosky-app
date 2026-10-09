@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.mediapost.MediaPostFileStore
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset

@Composable
internal fun HomePostEditor(state: HomeEditState, controls: HomeFeedController) {
    val app = LocalContext.current.applicationContext
    val files = remember(app) { MediaPostFileStore(app) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        controls.addPhotos(uris, files)
    }
    val enabled = !state.busy && !state.needsReload
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(onDismissRequest = controls::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !state.saving }),
        containerColor = Color.White, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Edit post", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FeedColors.text,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = controls::closeEditor, enabled = !state.saving) { Text("Cancel", color = FeedColors.muted) }
                Button(onClick = { controls.saveEditor(files) }, enabled = state.valid,
                    colors = ButtonDefaults.buttonColors(containerColor = FeedColors.brand)) {
                    Text(if (state.saving) "Saving…" else "Save")
                }
            }
            if (state.loading) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FeedColors.brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                if (state.post is HomePost.Text) {
                    val centered = state.textStyle == FeedTextStyle.BOLD_CENTER || state.textStyle == FeedTextStyle.NORMAL_CENTER
                    val bold = state.textStyle == FeedTextStyle.BOLD_CENTER || state.textStyle == FeedTextStyle.BOLD_LEFT
                    val color = remember(state.textColor) { cssColor(state.textColor, FeedColors.text) }
                    Box(Modifier.fillMaxWidth().heightIn(min = 170.dp).clip(RoundedCornerShape(14.dp)), Alignment.Center) {
                        TextPostBackground(state.background.kind, state.background.value, Modifier.matchParentSize())
                        BasicTextField(state.text, controls::editText, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(22.dp),
                            textStyle = TextStyle(color = color, fontSize = 22.sp, lineHeight = 30.sp,
                                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                                textAlign = if (centered) TextAlign.Center else TextAlign.Start),
                            cursorBrush = SolidColor(color), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    }
                    Text("Background", color = FeedColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TextPostPreset.entries, key = { it.id }) { preset ->
                            val chosen = state.background == preset.background && state.textColor == preset.textColor
                            val shape = RoundedCornerShape(10.dp)
                            Box(Modifier.size(48.dp).clip(shape)
                                .border(if (chosen) 2.dp else 1.dp, if (chosen) FeedColors.brand else FeedColors.line, shape)
                                .clickable(enabled, role = Role.RadioButton, onClickLabel = preset.label) { controls.editPreset(preset) },
                                contentAlignment = Alignment.Center) {
                                TextPostBackground(preset.background.kind, preset.background.value, Modifier.matchParentSize())
                                Text(if (chosen) "✓" else "Aa", fontWeight = FontWeight.Bold,
                                    color = cssColor(preset.textColor, Color.White))
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FeedTextStyle.entries, key = { it.name }) { style ->
                            FilterChip(selected = state.textStyle == style, enabled = enabled,
                                onClick = { controls.editStyle(style) },
                                label = { Text(when (style) {
                                    FeedTextStyle.BOLD_CENTER -> "Bold center"
                                    FeedTextStyle.NORMAL_CENTER -> "Center"
                                    FeedTextStyle.BOLD_LEFT -> "Bold left"
                                    FeedTextStyle.NORMAL_LEFT -> "Left"
                                }) })
                        }
                    }
                } else {
                    OutlinedTextField(state.text, controls::editText, enabled = enabled,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Caption") }, minLines = 3, maxLines = 7,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.keptMedia, key = { it.id ?: it.url }) { media ->
                            EditorPhoto(media.url, enabled) { controls.removeMedia(media.id ?: media.url) }
                        }
                        items(state.selectedMedia, key = { it.id }) { media ->
                            EditorPhoto(media.uri, enabled) { controls.removeMedia(media.id) }
                        }
                    }
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = enabled && state.keptMedia.size + state.selectedMedia.size < 10) {
                        Text(if (state.selecting) "Reading photos…" else "Add photos")
                    }
                }
                Text("${state.length} / ${state.limit}", fontSize = 12.sp,
                    color = if (state.length > state.limit) FeedColors.brand else FeedColors.muted,
                    modifier = Modifier.align(Alignment.End))
            }
            state.error?.let { error ->
                Text(error, color = FeedColors.brand, fontSize = 14.sp, lineHeight = 20.sp)
                if (state.needsReload) TextButton(onClick = controls::reloadEditor, enabled = !state.busy) {
                    Text("Reload latest post", color = FeedColors.brand)
                }
            }
        }
    }
}

@Composable
private fun EditorPhoto(url: String, enabled: Boolean, onRemove: () -> Unit) {
    Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(FeedColors.soft)) {
        AsyncImage(model = url, contentDescription = "Post photo", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd)
            .size(48.dp).padding(6.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))) {
            Icon(FeedIcons.close, "Remove photo", tint = FeedColors.text, modifier = Modifier.size(18.dp))
        }
    }
}
