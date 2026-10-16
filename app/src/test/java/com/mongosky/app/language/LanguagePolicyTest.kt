package com.mongosky.app.language

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguagePolicyTest {
    @Test fun missingOrUnsupportedPreferenceFallsBackToEnglish() {
        listOf(null, "", "fr", "invalid").forEach { assertEquals(AppLocale.ENGLISH, AppLocale.parse(it)) }
        assertEquals(AppLocale.BANGLA, AppLocale.parse("bn"))
        assertEquals(AppLocale.ENGLISH, AppLocale.parse("en"))
    }

    @Test fun turningTheSelectedSwitchOffSelectsTheOtherLanguage() {
        AppLocale.entries.forEach { locale ->
            assertEquals(locale, locale.other().other())
            assertEquals(1, AppLocale.entries.count { it == locale.other() })
        }
    }

    @Test fun interpolationPreservesNamesIncludingWordsThatAreAlsoUiLabels() {
        val template = LanguageTemplate("Welcome, %1\$s", 1)
        assertArrayEquals(arrayOf<Any>("Home"), template.arguments("Welcome, Home"))
        assertArrayEquals(arrayOf<Any>("Language 50% বাংলা"), template.arguments("Welcome, Language 50% বাংলা"))
        assertNull(template.arguments("Goodbye, Home"))
    }

    @Test fun templatesKeepArgumentOrderAndMultilineContent() {
        val template = LanguageTemplate("Photo %1\$s of %2\$s", 1)
        assertArrayEquals(arrayOf<Any>("2", "10"), template.arguments("Photo 2 of 10"))
        assertNull(template.arguments("Video 2 of 10"))
        assertArrayEquals(arrayOf<Any>("first\nsecond"),
            LanguageTemplate("Reply to %1\$s...", 1).arguments("Reply to first\nsecond..."))
    }
}
