package com.mongosky.app.darkmode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Owns only the appearance page, keeping Settings and every other route intact. */
@Composable internal fun DarkModeSettingsHost(content: @Composable (() -> Unit) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) DarkModeScreen(onBack = { open = false }) else content { open = true }
}
