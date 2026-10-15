package com.mongosky.app.saveditems

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.mongosky.app.darkmode.AppColors
import org.junit.Assert.*
import org.junit.Test

class SavedItemsThemeTest {
    @Test fun lightModeKeepsExistingSavedPageAndMenuColors() {
        val colors = savedUiColors(false, lightColorScheme())
        assertEquals(Color(0xFFF4F7F6), colors.background)
        assertEquals(Color.White, colors.surface)
        assertEquals(Color(0xFF172B28), colors.text)
        assertEquals(Color(0xFF647570), colors.muted)
        assertEquals(Color(0xFF07927C), colors.accent)
        assertEquals(Color(0xFFE0F3EB), colors.selectedContainer)
        assertEquals(Color(0xFFE8EEEB), colors.skeleton)
        assertEquals(Color(0xFFF0F4F2), colors.skeletonSecondary)
    }

    @Test fun darkSavedTextAndActionsAreReadable() {
        val colors = savedUiColors(true, AppColors.DarkScheme)
        for (foreground in listOf(colors.text, colors.muted, colors.accent)) {
            assertTrue(AppColors.contrast(foreground, colors.surface) >= 4.5)
            assertTrue(AppColors.contrast(foreground, colors.background) >= 4.5)
        }
        assertTrue(AppColors.contrast(colors.selectedText, colors.selectedContainer) >= 4.5)
        assertTrue(AppColors.contrast(colors.onAccent, colors.accent) >= 4.5)
    }

    @Test fun savedUiUsesTheCurrentMaterialThemeInsteadOfAStaleColorHelper() {
        val custom = darkColorScheme(surface = Color(0xFF101920), onSurface = Color(0xFFFBFCFD),
            secondary = Color(0xFF9EDCCC), secondaryContainer = Color(0xFF183B35), onSecondaryContainer = Color(0xFFC9FFEF))
        val colors = savedUiColors(true, custom)
        assertEquals(custom.surface, colors.surface)
        assertEquals(custom.onSurface, colors.text)
        assertEquals(custom.secondary, colors.accent)
        assertEquals(custom.onSecondaryContainer, colors.selectedText)
    }

    @Test fun switchingBackToLightRestoresTheOriginalPalette() {
        val before = savedUiColors(false, lightColorScheme())
        val dark = savedUiColors(true, AppColors.DarkScheme)
        val after = savedUiColors(false, AppColors.DarkScheme)
        assertNotEquals(before.surface, dark.surface)
        assertSame(before, after)
    }

    @Test fun aNewThemeIsReflectedInSavedUiWithoutChangingItsLightAppearance() {
        val first = darkColorScheme(secondary = Color(0xFF88DDCC))
        val second = darkColorScheme(secondary = Color(0xFFBBEEDD))
        assertNotEquals(savedUiColors(true, first).accent, savedUiColors(true, second).accent)
        assertEquals(savedUiColors(false, first), savedUiColors(false, second))
    }
}
