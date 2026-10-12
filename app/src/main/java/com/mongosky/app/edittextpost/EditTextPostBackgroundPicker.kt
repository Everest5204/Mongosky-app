package com.mongosky.app.edittextpost

import com.mongosky.app.editpost.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset

@Composable
internal fun EditTextPostBackgroundPicker(state: EditTextPostState, onPreset: (TextPostPreset) -> Unit, onBack: () -> Unit) {
    var all by rememberSaveable(state.post.id) { mutableStateOf(false) }
    val presets = remember(all) { if (all) TextPostPreset.entries.toList() else TextPostPreset.entries.take(7) }
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(EditPostIcons.palette, null, tint = EditPostColors.text, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Background", color = EditPostColors.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f))
            Text("${state.limit - state.length}", color = if (state.limit - state.length < 60) EditPostColors.error else EditPostColors.muted,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item("back") { IconButton(onBack, enabled = !state.saving, modifier = Modifier.size(48.dp)) {
                Icon(FeedIcons.back, "Go back", tint = EditPostColors.muted, modifier = Modifier.size(20.dp))
            } }
            items(presets, key = { it.id }) { preset ->
                val chosen = state.background == preset.background
                val shape = RoundedCornerShape(13.dp)
                Box(Modifier.size(48.dp).clip(shape)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) EditPostColors.mediaGreen else EditPostColors.line, shape)
                    .clickable(enabled = state.editable, role = Role.RadioButton, onClickLabel = preset.label) { onPreset(preset) }
                    .semantics { contentDescription = preset.label; selected = chosen }, contentAlignment = Alignment.Center) {
                    TextPostBackground(preset.background.kind, preset.background.value, Modifier.matchParentSize().padding(3.dp).clip(RoundedCornerShape(9.dp)))
                    if (preset == TextPostPreset.NONE) Icon(EditPostIcons.none, null, tint = EditPostColors.muted, modifier = Modifier.size(20.dp))
                    else if (chosen) Icon(EditPostIcons.check, null, tint = cssColor(preset.textColor, Color.White), modifier = Modifier.size(20.dp))
                }
            }
            item("more") { IconButton({ all = !all }, enabled = state.editable, modifier = Modifier.size(48.dp)) {
                Icon(EditPostIcons.grid, if (all) "Show fewer backgrounds" else "Show more backgrounds",
                    tint = if (all) EditPostColors.green else EditPostColors.muted, modifier = Modifier.size(20.dp))
            } }
        }
    }
}
