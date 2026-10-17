package com.mongosky.app.caption

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.darkmode.AppColors

/** Preserve selection and composing text even when the original composer model stores a String. */
@Composable
internal fun rememberHashtagEditor(text: String): MutableState<TextFieldValue> {
    val editor = remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (editor.value.text != text) {
        val old = editor.value
        editor.value = TextFieldValue(text, TextRange(old.selection.start.coerceIn(0, text.length), old.selection.end.coerceIn(0, text.length)))
    }
    return editor
}

@Composable
internal fun HashtagSuggestions(value: TextFieldValue, enabled: Boolean, onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier) {
    if (!enabled || value.composition != null) return
    val controller = LocalHashtagController.current ?: return
    val active = remember(value.text, value.selection) {
        HashtagPolicy.active(value.text, value.selection.start, value.selection.end)
    } ?: return
    val state by controller.state.collectAsState()
    val suggestions = remember(state.index, active.query) { state.index.suggestions(active.query) }
    val blue = AppColors.content(Color(0xFF2563EB))
    fun choose(tag: String) {
        val result = HashtagPolicy.insert(value.text, active, tag)
        onChange(TextFieldValue(result.text, TextRange(result.cursor)))
    }
    Column(modifier.fillMaxWidth().background(AppColors.surface(Color(0xFFF1F5F9))).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("Hashtags · from loaded posts", color = AppColors.content(Color(0xFF64748B)), fontSize = 11.sp)
        Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
            for (suggestion in suggestions) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).focusProperties { canFocus = false }
                    .clickable(role = Role.Button) { choose(suggestion.tag) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${suggestion.tag}", color = blue, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${suggestion.people} people · ${suggestion.posts} posts", fontSize = 12.sp,
                        color = AppColors.content(Color(0xFF475569)))
                }
            }
            if (active.query.isNotEmpty() && suggestions.none { HashtagPolicy.key(it.tag) == HashtagPolicy.key(active.query) }) {
                Text("Use #${active.query}", color = blue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .focusProperties { canFocus = false }.clickable(role = Role.Button) { choose(active.query) }.padding(vertical = 12.dp))
            }
        }
        if (suggestions.isEmpty()) {
            TextButton(onClick = { controller.loadMore() }, enabled = !state.loading && state.hasMore,
                modifier = Modifier.focusProperties { canFocus = false }, contentPadding = PaddingValues(0.dp)) {
                Text(if (state.loading) "Loading…" else "Load more suggestions", color = blue, fontSize = 12.sp)
            }
            state.error?.let { Text(it, fontSize = 12.sp, color = AppColors.content(Color(0xFFB91C1C))) }
        }
    }
}
