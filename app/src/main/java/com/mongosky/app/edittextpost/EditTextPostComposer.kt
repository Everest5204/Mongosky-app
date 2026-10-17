package com.mongosky.app.edittextpost

import com.mongosky.app.caption.rememberCaptionTransformation
import com.mongosky.app.caption.rememberHashtagEditor
import com.mongosky.app.caption.HashtagSuggestions
import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

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
internal fun EditTextPostComposer(state: EditTextPostState, onText: (String) -> Unit) {
    val captionEditor = rememberHashtagEditor(state.text)
    val centered = state.textStyle == FeedTextStyle.BOLD_CENTER || state.textStyle == FeedTextStyle.NORMAL_CENTER
    val bold = state.textStyle == FeedTextStyle.BOLD_CENTER || state.textStyle == FeedTextStyle.BOLD_LEFT
    val color = AppColors.plainPostText(cssColor(state.textColor, EditPostColors.text),state.background.kind == FeedTextBackgroundKind.PLAIN)
    Column(Modifier.fillMaxWidth()) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val size = when { state.length > 260 -> (maxWidth.value * 0.046f).coerceIn(18f, 25f)
            state.length > 150 -> (maxWidth.value * 0.065f).coerceIn(24f, 34f)
            else -> (maxWidth.value * 0.09f).coerceIn(28f, 43f) }.sp
        val height = maxWidth.coerceIn(260.dp, 448.dp)
        val shape = RoundedCornerShape(18.dp)
        val style = TextStyle(color = color, fontSize = size, lineHeight = size * if (state.length > 260) 1.3f else 1.18f,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Box(Modifier.fillMaxWidth().height(height).clip(shape)
            .then(if (state.background.kind == FeedTextBackgroundKind.PLAIN) Modifier.border(1.dp, AppColors.outline(EditPostColors.line), shape) else Modifier),
            contentAlignment = Alignment.Center) {
            TextPostBackground(state.background.kind, state.background.value, Modifier.matchParentSize())
            BasicTextField(captionEditor.value, { next -> captionEditor.value = next; if (next.text != state.text) onText(next.text) }, enabled = state.editable,
                visualTransformation = rememberCaptionTransformation(),
                textStyle = style, cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
                    .heightIn(max = height - 48.dp).semantics { contentDescription = appText("Edit text post") },
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.text.isEmpty()) Text(appText("Write something..."), style = style.copy(color = color.copy(alpha = 0.5f)))
                        field()
                    }
                })
        }
    }
    HashtagSuggestions(captionEditor.value, state.editable, onChange = { next -> captionEditor.value = next; onText(next.text) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

