package com.mongosky.app.caption

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.*
import org.junit.Test

class CaptionAnnotationsTest {
    @Test fun hashtagClickUsesNativeCallbackAndNeverAUrl() {
        var opened: String? = null
        val source = "#বাংলা example.com"
        val result = annotateCaption(AnnotatedString(source), Color.Blue, true) { opened = it }
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(2, links.size)
        val hashtag = links[0].item as LinkAnnotation.Clickable
        assertEquals("hashtag:বাংলা", hashtag.tag)
        hashtag.linkInteractionListener?.onClick(hashtag)
        assertEquals("বাংলা", opened)
        assertEquals("https://example.com", (links[1].item as LinkAnnotation.Url).url)
        assertEquals(source, result.text)
    }
    @Test fun editorOnlyColorsTextAndAddsNoClickTargets() {
        val source = "😀 #travel example.com"
        val result = annotateCaption(AnnotatedString(source), Color.Blue, false)
        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
        assertEquals(source, result.text)
        assertEquals(2, result.spanStyles.size)
        assertTrue(result.spanStyles.all { it.item.color == Color.Blue })
    }
}
