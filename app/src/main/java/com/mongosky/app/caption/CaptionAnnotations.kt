package com.mongosky.app.caption

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString

internal fun annotateCaption(source: AnnotatedString, blue: Color, clickable: Boolean, onHashtag: (String) -> Unit = {}): AnnotatedString =
    buildAnnotatedString {
        append(source)
        val style = SpanStyle(color = blue)
        for (token in CaptionTokens.parse(source.text)) {
            addStyle(style, token.start, token.end)
            if (clickable) {
                val tag = token.hashtag
                if (tag != null) {
                    addLink(LinkAnnotation.Clickable("hashtag:${HashtagPolicy.key(tag)}", TextLinkStyles(style = style)) { onHashtag(tag) }, token.start, token.end)
                } else {
                    addLink(LinkAnnotation.Url(token.url, TextLinkStyles(style = style)), token.start, token.end)
                }
            }
        }
    }
