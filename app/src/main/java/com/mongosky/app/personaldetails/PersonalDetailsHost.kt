package com.mongosky.app.personaldetails

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

internal data class PersonalDetailsHost(val sessionKey: String, val sessionRevision: Long, val busy: Boolean,
    val error: String?, val onSignIn: () -> Unit, val onNameUpdated: () -> Unit)
internal val LocalPersonalDetailsHost = compositionLocalOf<PersonalDetailsHost?> { null }

@Composable
internal fun PersonalDetailsSettingsHost(settings: @Composable (() -> Unit) -> Unit) {
    val host = LocalPersonalDetailsHost.current
    var open by rememberSaveable(host?.sessionKey, host?.sessionRevision) { mutableStateOf(false) }
    val saved = rememberSaveableStateHolder()
    if (open && host != null) {
        saved.SaveableStateProvider("mongosky-personal-details") { PersonalDetailsRoute(host, onBack = { open = false }) }
    } else {
        saved.SaveableStateProvider("mongosky-personal-settings") { settings { if (host != null && !host.busy) open = true } }
    }
}
