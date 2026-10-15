package com.mongosky.app.saveditems

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.mongosky.app.darkmode.AppAppearance

internal val SavedGreen = Color(0xFF07927C)
internal val SavedBackground = Color(0xFFF4F7F6)
internal val SavedText = Color(0xFF172B28)
internal val SavedMuted = Color(0xFF647570)

/** One palette for the Saved page, original-post detail and Save/Unsave controls. */
@Immutable
internal data class SavedUiColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val border: Color,
    val searchBorder: Color,
    val selectedContainer: Color,
    val selectedText: Color,
    val thumbnail: Color,
    val skeleton: Color,
    val skeletonSecondary: Color,
)

private val LightSavedColors = SavedUiColors(
    background = SavedBackground,
    surface = Color.White,
    text = SavedText,
    muted = SavedMuted,
    accent = SavedGreen,
    onAccent = Color.White,
    border = Color(0xFFE4ECE8),
    searchBorder = Color(0xFFDDE8E2),
    selectedContainer = Color(0xFFE0F3EB),
    selectedText = SavedGreen,
    thumbnail = Color(0xFFEAF4EF),
    skeleton = Color(0xFFE8EEEB),
    skeletonSecondary = Color(0xFFF0F4F2),
)

internal fun savedUiColors(dark: Boolean, scheme: ColorScheme): SavedUiColors =
    if (!dark) LightSavedColors else SavedUiColors(
        background = scheme.surfaceContainerLow,
        surface = scheme.surface,
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.secondary,
        onAccent = scheme.onSecondary,
        border = scheme.outlineVariant,
        searchBorder = scheme.outline,
        selectedContainer = scheme.secondaryContainer,
        selectedText = scheme.onSecondaryContainer,
        thumbnail = scheme.surfaceVariant,
        skeleton = scheme.surfaceContainerHighest,
        skeletonSecondary = scheme.surfaceVariant,
    )

@Composable
internal fun rememberSavedUiColors(): SavedUiColors {
    val dark = AppAppearance.isDark
    val scheme = MaterialTheme.colorScheme
    return remember(dark, scheme) { savedUiColors(dark, scheme) }
}
