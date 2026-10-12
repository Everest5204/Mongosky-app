package com.mongosky.app.helpsupport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue

/**
 * A nested Settings destination keeps the app's existing host routes intact.
 * Separate saveable buckets retain the Settings scroll position and support draft.
 * MainActivity's account key owns this subtree, so account switches reset it.
 */
@Composable
internal fun HelpSupportSettingsHost(settings: @Composable (() -> Unit) -> Unit) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val state = rememberSaveableStateHolder()
    if (helpOpen) {
        state.SaveableStateProvider("mongosky-help-support") {
            HelpSupportRoute(onBack = { helpOpen = false })
        }
    } else {
        state.SaveableStateProvider("mongosky-settings") {
            settings { helpOpen = true }
        }
    }
}
