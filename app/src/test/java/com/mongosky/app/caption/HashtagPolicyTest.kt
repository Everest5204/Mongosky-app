package com.mongosky.app.caption

import org.junit.Assert.*
import org.junit.Test

class HashtagPolicyTest {
    @Test fun activeTagUsesCursorAndReplacesWholeToken() {
        val text = "hello #travell world"
        val active = HashtagPolicy.active(text, 10)!!
        assertEquals("tra", active.query)
        assertEquals(HashtagInsertion("hello #travel world", 14), HashtagPolicy.insert(text, active, "travel"))
    }
    @Test fun loneHashAndBanglaTagsWork() {
        assertEquals("", HashtagPolicy.active("hello #", 7)?.query)
        val text = "😀 #বাংলা"
        assertEquals("বাংলা", HashtagPolicy.active(text, text.length)?.query)
    }
    @Test fun urlFragmentsEmailsAndSelectedTextDoNotSuggest() {
        val fragment = "https://example.com/#tag"
        assertNull(HashtagPolicy.active(fragment, fragment.length))
        val loneFragment = "https://example.com/#"
        assertNull(HashtagPolicy.active(loneFragment, loneFragment.length))
        assertNull(HashtagPolicy.active("word#tag", 8))
        assertNull(HashtagPolicy.active("#tag", 1, 4))
        assertNull(HashtagPolicy.active("name@#", 6))
    }
    @Test fun suggestionPreservesOtherTextAndEmoji() {
        val text = "😀 #ba, next #other"
        val active = HashtagPolicy.active(text, 6)!!
        assertEquals(HashtagInsertion("😀 #বাংলা , next #other", 10), HashtagPolicy.insert(text, active, "বাংলা"))
    }
    @Test fun insertionPlacesCursorAfterSpaceAtEnd() {
        val text = "#tr"
        assertEquals(HashtagInsertion("#travel ", 8), HashtagPolicy.insert(text, HashtagPolicy.active(text, text.length)!!, "travel"))
    }
    @Test fun tagMatchingUsesUnicodeAndLocaleIndependentCase() {
        assertEquals("travel", HashtagPolicy.key("#TRAVEL"))
        assertEquals(HashtagPolicy.key("#café"), HashtagPolicy.key("#cafe\u0301"))
    }
    @Test fun invalidCursorsAndPlainTextAreIgnored() {
        assertNull(HashtagPolicy.active("text", -1))
        assertNull(HashtagPolicy.active("text", 9))
        assertNull(HashtagPolicy.active("text", 4))
        assertNull(HashtagPolicy.active("#tag done", 9))
    }
}
