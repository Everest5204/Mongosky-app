package com.mongosky.app.caption

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mongosky.app.darkmode.AppColors

/** Native Compose links retain TalkBack, selection and per-link touch handling. */
@Composable
internal fun rememberCaptionText(text: String): AnnotatedString {
    val blue = AppColors.content(Color(0xFF2563EB))
    val hashtags = LocalHashtagController.current
    return remember(text, blue, hashtags) {
        annotateCaption(AnnotatedString(text), blue, clickable = true, onHashtag = { hashtags?.open(it) })
    }
}

/** Only paint text in editors: preserve cursor, selection, composing text and stored caption. */
@Composable
internal fun rememberCaptionTransformation(): VisualTransformation {
    val blue = AppColors.content(Color(0xFF2563EB))
    return remember(blue) {
        VisualTransformation { source ->
            TransformedText(annotateCaption(source, blue, clickable = false), OffsetMapping.Identity)
        }
    }
}
