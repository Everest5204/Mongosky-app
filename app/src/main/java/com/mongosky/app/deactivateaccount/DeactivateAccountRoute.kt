package com.mongosky.app.deactivateaccount

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.TokenStore

@Composable
internal fun DeactivateAccountRoute(host: DeactivateAccountHost, onBack: () -> Unit) {
    val activity = checkNotNull(LocalContext.current.deactivationActivity())
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val model = remember(owner, host.sessionKey, host.sessionRevision) {
        ViewModelProvider(owner, DeactivateAccountViewModel.Factory(TokenStore(activity.applicationContext)::read))
            .get("MongoskyDeactivate:${host.sessionKey}:${host.sessionRevision}", DeactivateAccountViewModel::class.java)
    }
    val autofill = LocalAutofillManager.current
    val leaveAccount by rememberUpdatedState(host.onLeaveAccount)
    DisposableEffect(model, activity) {
        model.enter()
        onDispose {
            runCatching { autofill?.cancel() }
            if (!activity.isChangingConfigurations) model.close()
        }
    }
    val state = model.state
    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) { runCatching { autofill?.cancel() }; leaveAccount() }
    }
    val goBack = { if (!host.busy && model.leave()) { runCatching { autofill?.cancel() }; onBack() } }
    DeactivateAccountScreen(state, host, model::updatePassword, model::toggleVisibility,
        onContinue = { if (!host.busy) { runCatching { autofill?.cancel() }; model.openConfirmation() } }, onBack = goBack)
    if (state.dialogOpen && !state.exitRequested) DeactivateAccountDialog(state, host.busy,
        onDismiss = model::dismissConfirmation, onConfirm = model::submit, onSignOut = model::requestSignOut)
}

private fun Context.deactivationActivity(): Activity? {
    var cursor = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        val next = cursor.baseContext
        if (next === cursor) break
        cursor = next
    }
    return cursor as? Activity
}
