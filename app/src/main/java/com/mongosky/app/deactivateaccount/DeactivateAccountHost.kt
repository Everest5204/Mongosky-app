package com.mongosky.app.deactivateaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue

internal data class DeactivateAccountHost(
    val sessionKey: String,
    val sessionRevision: Long,
    val busy: Boolean,
    val error: String?,
    val onLeaveAccount: () -> Unit
)

internal val LocalDeactivateAccountHost = compositionLocalOf<DeactivateAccountHost?> { null }

@Composable
internal fun DeactivateAccountSettingsHost(settings: @Composable (() -> Unit) -> Unit) {
    val host = LocalDeactivateAccountHost.current
    var open by rememberSaveable { mutableStateOf(false) }
    val saved = rememberSaveableStateHolder()
    if (open && host != null) {
        saved.SaveableStateProvider("mongosky-deactivate-account") {
            DeactivateAccountRoute(host = host, onBack = { open = false })
        }
    } else {
        saved.SaveableStateProvider("mongosky-deactivate-settings") {
            settings { if (host != null && !host.busy) open = true }
        }
    }
}
