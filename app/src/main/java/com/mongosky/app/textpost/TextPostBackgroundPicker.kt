package com.mongosky.app.textpost

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.TextPostBackground
import com.mongosky.app.textpost.TextPostPreset

@Composable
fun TextPostBackgroundPicker(
    selected: TextPostPreset,
    remaining: Int,
    enabled: Boolean,
    onSelect: (TextPostPreset) -> Unit,
    onBack: () -> Unit,
    onEmoji: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Background", color = Color(0xFF020617), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(remaining.toString(), color = if (remaining < 60) Color(0xFFE11D48) else Color(0xFF64748B),
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.semantics { contentDescription = "$remaining characters remaining" })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "back") {
                IconButton(onClick = onBack, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close composer") }
            }
            items(if (expanded) TextPostPreset.entries else TextPostPreset.entries.take(9), key = { it.id }) { preset ->
                val active = preset == selected
                val shape = RoundedCornerShape(13.dp)
                Box(Modifier.size(48.dp).clip(shape)
                    .border(if (active) 3.dp else 1.dp, if (active) Color(0xFF16A34A) else Color(0xFFE5E7EB), shape)
                    .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(preset) })
                    .semantics { contentDescription = preset.label }, Alignment.Center) {
                    TextPostBackground(preset.background.kind, preset.background.value, Modifier.matchParentSize())
                    if (active) Icon(Icons.Default.Check, null,
                        tint = if (preset.textColor == "#ffffff") Color.White else Color(0xFF111827))
                    else if (preset == TextPostPreset.NONE) Text("Aa", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                }
            }
            item(key = "more") {
                IconButton(onClick = { expanded = !expanded }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, if (expanded) "Show fewer backgrounds" else "Show all backgrounds")
                }
            }
            item(key = "emoji") {
                IconButton(onClick = onEmoji, enabled = enabled) { Icon(Icons.Default.Face, "Add emoji", tint = Color(0xFFF97316)) }
            }
        }
    }
}
