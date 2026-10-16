package com.mongosky.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue

/** This section owns its page; returning preserves the Settings list's saved scroll position. */
@Composable
internal fun LanguageSettingsHost(content: @Composable (() -> Unit) -> Unit) {
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    if (languageOpen) {
        pages.SaveableStateProvider("settings-language") { LanguageScreen(onBack = { languageOpen = false }) }
    } else {
        pages.SaveableStateProvider("settings-menu") { content { languageOpen = true } }
    }
}
