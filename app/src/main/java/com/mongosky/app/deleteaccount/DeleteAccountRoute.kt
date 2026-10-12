package com.mongosky.app.deleteaccount

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mongosky.app.auth.TokenStore

@Composable
internal fun DeleteAccountRoute(
    sessionKey: String, sessionRevision: Long, onBack: () -> Unit, onLeaveAccount: () -> Unit,
    hostBusy: Boolean = false, hostError: String? = null
) {
    val activity = checkNotNull(LocalContext.current.deletionActivity())
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val model = remember(owner, sessionKey, sessionRevision) {
        ViewModelProvider(owner, DeleteAccountViewModel.Factory(TokenStore(activity.applicationContext)::read))
            .get("MongoskyDeleteAccount:$sessionKey:$sessionRevision", DeleteAccountViewModel::class.java)
    }
    val autofill = LocalAutofillManager.current
    val leaveAccount by rememberUpdatedState(onLeaveAccount)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycleOwner) {
        model.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ ->
            model.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); model.setForeground(false) }
    }
    DisposableEffect(model, activity, autofill) {
        onDispose {
            runCatching { autofill?.cancel() }
            if (!activity.isChangingConfigurations) model.leave()
        }
    }
    val state = model.state
    LaunchedEffect(state.exitRevision) {
        if (state.exitRequested && state.exitRevision > 0) {
            runCatching { autofill?.cancel() }
            leaveAccount()
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress = animateFloatAsState(if (entered) 1f else 0f,
        tween(140, easing = FastOutSlowInEasing), label = "Delete account entrance")
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; translationX = distance * (1f - progress.value) }) {
            DeleteAccountScreen(state, model::updatePassword,
                onConfirm = { if (!hostBusy) { runCatching { autofill?.cancel() }; model.openConfirmation() } },
                onBack = { if (!hostBusy && model.back()) onBack() }, hostBusy = hostBusy)
        }
    }
    if (state.dialogOpen) {
        DeleteAccountDialog(state, onDiscard = { if (!hostBusy) model.dismiss() },
            onDelete = { if (!hostBusy) model.submit() }, onFinish = { if (!hostBusy) model.requestExit() },
            hostBusy = hostBusy, hostError = hostError.takeIf { state.exitRequested })
    }
}

private fun Context.deletionActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val next = context.baseContext
        if (next === context) break
        context = next
    }
    return context as? Activity
}
