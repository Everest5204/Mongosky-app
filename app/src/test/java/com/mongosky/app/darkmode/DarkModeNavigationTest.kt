package com.mongosky.app.darkmode

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkModeNavigationTest {
    @Test fun settingsOpensAnIndependentPageAndBackRestoresSettings() {
        val history = listOf("HOME", "PROFILE", "SETTINGS")
        val opened = DarkModeNavigation.open(history)
        assertEquals(history + "DARK_MODE", opened)
        assertEquals(history, DarkModeNavigation.back(opened, "DARK_MODE"))
        assertEquals(listOf("HOME", "PROFILE", "SETTINGS"), history)
    }

    @Test fun repeatedTapsCannotStackDuplicateAppearancePages() {
        val first = DarkModeNavigation.open(listOf("HOME", "SETTINGS"))
        var stack = first
        repeat(30) { stack = DarkModeNavigation.open(stack) }
        assertEquals(first, stack)
    }

    @Test fun staleSettingsTapCannotInterruptAnotherSection() {
        val stack = listOf("HOME", "SETTINGS", "HELP_AND_SUPPORT")
        assertEquals(stack, DarkModeNavigation.open(stack))
    }

    @Test fun staleBackCannotPopAReplacedOrUnrelatedPage() {
        val stack = listOf("HOME", "PROFILE")
        assertEquals(stack, DarkModeNavigation.back(stack, "DARK_MODE"))
        assertEquals(stack, DarkModeNavigation.back(stack, "PROFILE"))
    }

    @Test fun restoredOrphanAppearancePageReturnsToSettingsWithoutFinishingApp() {
        assertEquals(listOf("HOME", "SETTINGS"), DarkModeNavigation.back(listOf("DARK_MODE"), "DARK_MODE"))
        assertEquals(emptyList<String>(), DarkModeNavigation.open(emptyList()))
    }
}
