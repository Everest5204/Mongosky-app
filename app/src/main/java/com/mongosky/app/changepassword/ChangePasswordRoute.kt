package com.mongosky.app.changepassword

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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.TokenStore

@Composable
internal fun ChangePasswordRoute(
    sessionKey: String, onBack: () -> Unit, onForgotPassword: () -> Unit,
    onSignInAgain: () -> Unit, hostBusy: Boolean = false
) {
    val activity = checkNotNull(LocalContext.current.passwordActivity())
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val model = remember(owner, sessionKey) {
        ViewModelProvider(owner, ChangePasswordViewModel.Factory(TokenStore(activity.applicationContext)::read))
            .get("MongoskyChangePassword:$sessionKey", ChangePasswordViewModel::class.java)
    }
    val autofill = LocalAutofillManager.current
    val signInAgain by rememberUpdatedState(onSignInAgain)
    val state = model.state
    LaunchedEffect(state.successRevision, state.awaitingAutofill) {
        if (state.awaitingAutofill) {
            runCatching { autofill?.commit() }
            model.acknowledgeSuccess(state.successRevision)
        }
    }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) { runCatching { autofill?.cancel() }; signInAgain() }
    }
    DisposableEffect(model, activity, autofill) {
        onDispose {
            runCatching { autofill?.cancel() }
            if (!activity.isChangingConfigurations) model.leave()
        }
    }
    PasswordPageTransition {
        ChangePasswordScreen(state, model::update, model::submit, onBack, onForgotPassword, hostBusy)
    }
}

@Composable
internal fun PasswordPageTransition(content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress = animateFloatAsState(if (entered) 1f else 0f,
        tween(140, easing = FastOutSlowInEasing), label = "Password page entrance")
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; translationX = distance * (1f - progress.value) }) {
            content()
        }
    }
}

internal fun Context.passwordActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val next = context.baseContext
        if (next === context) break
        context = next
    }
    return context as? Activity
}
