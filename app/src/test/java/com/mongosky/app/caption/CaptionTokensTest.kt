package com.mongosky.app.caption

import org.junit.Assert.*
import org.junit.Test

class CaptionTokensTest {
    @Test fun unicodeHashtagsAndEmojiOffsets() {
        val text = "😀 #বাংলাদেশ #hello_world #২০২৬"
        val tokens = CaptionTokens.parse(text)
        assertEquals(listOf("#বাংলাদেশ", "#hello_world", "#২০২৬"), tokens.map { text.substring(it.start, it.end) })
        assertEquals(3, tokens.first().start)
        assertEquals("বাংলাদেশ", tokens.first().hashtag)
        assertEquals("", tokens.first().url)
    }
    @Test fun domainsAndSchemesAreBrowserUrls() {
        val tokens = CaptionTokens.parse("example.com www.example.org/a?b=1 https://example.net/p#section HTTP://example.com")
        assertEquals(listOf("https://example.com", "https://www.example.org/a?b=1", "https://example.net/p#section", "HTTP://example.com"), tokens.map { it.url })
    }
    @Test fun trimsSentencePunctuationButKeepsBalancedParentheses() {
        val text = "(https://example.com/wiki/Test_(one)). example.org! #tag,"
        assertEquals(listOf("https://example.com/wiki/Test_(one)", "example.org", "#tag"),
            CaptionTokens.parse(text).map { text.substring(it.start, it.end) })
    }
    @Test fun doesNotLinkEmailsEmbeddedTagsOrUnsupportedSchemes() {
        assertTrue(CaptionTokens.parse("me@example.com word#tag # javascript:alert(1) data:text/plain,hello").isEmpty())
    }
    @Test fun fragmentIsPartOfUrlAndBanglaCombiningMarksStayInTag() {
        val text = "https://example.com/#hello #বাংলা"
        val tokens = CaptionTokens.parse(text)
        assertEquals(2, tokens.size)
        assertEquals("https://example.com/#hello", tokens[0].url)
        assertEquals("#বাংলা", text.substring(tokens[1].start, tokens[1].end))
    }
    @Test fun rejectsCredentialLinksAndInvalidPorts() {
        assertTrue(CaptionTokens.parse("https://user:pass@example.com https://example.com:99999/x").isEmpty())
    }
    @Test fun plainTextAndWhitespaceNeedNoAnnotations() {
        assertTrue(CaptionTokens.parse("Just a caption\nসুন্দর একটি দিন").isEmpty())
    }
}
