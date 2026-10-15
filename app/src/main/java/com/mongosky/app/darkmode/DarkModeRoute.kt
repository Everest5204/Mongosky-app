package com.mongosky.app.darkmode

import androidx.compose.runtime.Composable

/** Owns appearance UI only; opening it performs no server or account request. */
@Composable
internal fun DarkModeRoute(onBack: () -> Unit) {
    DarkModeTheme { DarkModeScreen(onBack = onBack) }
}
