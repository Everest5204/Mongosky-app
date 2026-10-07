package com.mongosky.app.textpost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.TextPostBackground
import com.mongosky.app.textpost.TextPostLimits
import com.mongosky.app.textpost.TextPostPreset

@Composable
fun TextPostEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    preset: TextPostPreset,
    placeholder: String,
    enabled: Boolean,
    height: Dp,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val length = TextPostLimits.length(value.text)
    val fontSize = when { length > 260 -> 20; length > 150 -> 28; else -> 36 }.sp
    val color = if (preset.textColor == "#ffffff") Color.White else Color(0xFF111827)
    val style = TextStyle(color = color, fontSize = fontSize, lineHeight = fontSize * 1.25f,
        fontWeight = FontWeight.ExtraBold, textAlign = if (length > 260) TextAlign.Start else TextAlign.Center)
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().height(height).shadow(8.dp, shape).clip(shape), Alignment.Center) {
        TextPostBackground(preset.background.kind, preset.background.value, Modifier.matchParentSize())
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = style,
            cursorBrush = SolidColor(color),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(24.dp)
                .heightIn(max = height - 48.dp).focusRequester(focusRequester),
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty()) Text(placeholder, style = style.copy(color = color.copy(alpha = 0.55f)))
                    field()
                }
            }
        )
    }
}
