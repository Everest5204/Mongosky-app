package com.mongosky.app.deactivateaccount

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DeactivateAccountDialog(state: DeactivateAccountState, hostBusy: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit, onSignOut: () -> Unit) {
    val busy = state.submitting || hostBusy
    AlertDialog(onDismissRequest = { if (!busy && !state.uncertain) onDismiss() },
        title = { Text(if (state.uncertain) "Confirm deactivation" else "Deactivate your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Your profile and posts will be hidden, and you will be signed out on all devices. Your data will be kept. Sign in again to reactivate.")
                if (state.error != null) Text(state.error, color = DeactivateAccountColors.Red)
                if (state.submitting) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = DeactivateAccountColors.Red)
                    Text("Deactivating…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (state.uncertain) "Retry same request" else "Deactivate", color = DeactivateAccountColors.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = if (state.uncertain) onSignOut else onDismiss, enabled = !busy) {
                Text(if (state.uncertain) "Sign out" else "Cancel", color = DeactivateAccountColors.Ink)
            }
        }, containerColor = androidx.compose.ui.graphics.Color.White)
}
