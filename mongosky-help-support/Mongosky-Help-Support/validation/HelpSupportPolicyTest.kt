package com.mongosky.app.helpsupport

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class HelpSupportPolicyTest {
    @Test fun searchCombinesWordsAndTopics() {
        assertTrue(HelpSupportPolicy.search("RESET   password", HelpTopic.ACCOUNT).any { it.id == "reset-password" })
        assertTrue(HelpSupportPolicy.search("password", HelpTopic.POSTS).isEmpty())
        assertEquals(HelpSupportContent.articles.size, HelpSupportPolicy.search(" \n ", null).size)
        assertTrue(HelpSupportPolicy.search("unfindableword1234", null).isEmpty())
    }
    @Test fun allTopicsHaveUniqueArticleKeys() {
        assertEquals(HelpSupportContent.articles.size, HelpSupportContent.articles.map { it.id }.toSet().size)
        HelpTopic.entries.forEach { assertTrue(HelpSupportPolicy.search("", it).isNotEmpty()) }
    }
    @Test fun validationRejectsEmptyAndOverLimitMessages() {
        assertFalse(HelpSupportPolicy.validate(SupportDraft()).isValid)
        assertFalse(HelpSupportPolicy.validate(SupportDraft(summary = "Issue", description = "    short ")).isValid)
        assertFalse(HelpSupportPolicy.validate(SupportDraft(summary = "x".repeat(101), description = "valid details")).isValid)
        assertFalse(HelpSupportPolicy.validate(SupportDraft(summary = "Issue", description = "x".repeat(4001))).isValid)
        assertTrue(HelpSupportPolicy.validate(SupportDraft(summary = "Issue", description = "1234567890")).isValid)
    }
    @Test fun emailRoundTripsUnicodeAndDelimiters() {
        val draft = SupportDraft(SupportReason.TECHNICAL, "Upload & image?", "বাংলা details & + # ? \nSecond line 😀")
        val email = HelpSupportPolicy.prepareEmail(draft, null)
        assertEquals("mongosky0@gmail.com", email.recipient)
        val fields = email.mailto.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals(email.subject, fields["subject"])
        assertEquals(email.body, fields["body"])
        assertEquals(2, fields.size)
        assertTrue(email.body.contains(draft.description))
    }
    @Test fun metadataIsIncludedOnlyByChoice() {
        val draft = SupportDraft(summary = "Crash", description = "The feed closed suddenly.")
        val info = SupportAppInfo("1.0", "16")
        assertFalse(HelpSupportPolicy.prepareEmail(draft, info).body.contains("Android version"))
        assertTrue(HelpSupportPolicy.prepareEmail(draft.copy(includeAppInfo = true), info).body.contains("Android version: 16"))
        assertFalse(HelpSupportPolicy.prepareEmail(draft.copy(includeAppInfo = true), null).body.contains("Android version"))
    }
    @Test fun summaryCannotInjectEmailHeaders() {
        val draft = SupportDraft(summary = "Bug\r\nBcc: nobody@example.test", description = "This is a valid description.")
        val email = HelpSupportPolicy.prepareEmail(draft, null)
        assertFalse(email.subject.contains('\n'))
        assertFalse(email.subject.contains('\r'))
        assertEquals(100, HelpSupportPolicy.summary("x".repeat(200)).length)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDraftCannotBecomeAnEmail() {
        HelpSupportPolicy.prepareEmail(SupportDraft(), null)
    }
}
