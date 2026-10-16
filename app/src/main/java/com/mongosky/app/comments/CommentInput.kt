package com.mongosky.app.comments

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Native multiline composer: keyboard/selection/IME stay under Android's control. */
@Composable
internal fun CommentInput(value: String, onValueChange: (String) -> Unit, placeholder: String,
    submitLabel: String, onSend: () -> Unit, enabled: Boolean, sending: Boolean,
    canSubmit: Boolean = true, onCancel: (() -> Unit)? = null, autoFocus: Boolean = false,
    compact: Boolean = false, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val canSend = enabled && !sending && canSubmit && CommentPolicy.canSend(value)
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        BasicTextField(value = value, onValueChange = onValueChange,
            enabled = enabled && !sending, maxLines = 5,
            textStyle = TextStyle(fontSize = if (compact) 14.sp else 15.sp, lineHeight = if (compact) 19.sp else 21.sp, color = AppColors.content(CommentColors.text)),
            cursorBrush = SolidColor(CommentColors.active),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp).focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && !it.isShiftPressed) {
                        if (canSend) onSend()
                        true
                    } else false
                },
            decorationBox = { input ->
                Box(Modifier.fillMaxWidth().border(1.dp, AppColors.outline(if (focused) Color(0xFFC9C9C9) else CommentColors.inputBorder),
                    RoundedCornerShape(if (compact) 14.dp else 24.dp))
                    .background(AppColors.surface(if (focused || compact) Color.White else CommentColors.input), RoundedCornerShape(if (compact) 14.dp else 24.dp))
                    .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 11.dp)) {
                    if (value.isEmpty()) Text(appText(placeholder), color = AppColors.content(CommentColors.placeholder),
                        fontSize = if (compact) 14.sp else 15.sp, lineHeight = 20.sp)
                    input()
                }
            })
        onCancel?.let { cancel ->
            IconButton(onClick = cancel, enabled = !sending, modifier = Modifier.size(48.dp)) {
                Icon(CommentIcons.close, appDescription("Cancel $submitLabel"), Modifier.size(18.dp), tint = AppColors.content(CommentColors.muted))
            }
        }
        IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp)) {
            if (sending) CircularProgressIndicator(Modifier.size(21.dp), color = AppColors.content(CommentColors.active), strokeWidth = 2.dp)
            else Icon(CommentIcons.send, appDescription(submitLabel), Modifier.size(if (compact) 22.dp else 25.dp),
                tint = AppColors.content(if (canSend) CommentColors.active else Color(0xFFD8DCE2)))
        }
    }
}
