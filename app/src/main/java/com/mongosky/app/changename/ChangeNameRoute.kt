package com.mongosky.app.changename

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.TokenStore

@Composable
internal fun ChangeNameRoute(sessionKey: String, onBack: () -> Unit, onSaved: (String) -> Unit, onSignInAgain: () -> Unit) {
    val activity = checkNotNull(LocalContext.current.nameActivity())
    val model = remember(activity, sessionKey) {
        ViewModelProvider(activity as ViewModelStoreOwner, ChangeNameViewModel.Factory(TokenStore(activity.applicationContext)::read))
            .get("MongoskyChangeName:$sessionKey", ChangeNameViewModel::class.java)
    }
    val lifecycle = (activity as LifecycleOwner).lifecycle
    DisposableEffect(model, lifecycle) {
        fun update() = model.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose {
            lifecycle.removeObserver(observer); model.foreground(false)
            if (!activity.isChangingConfigurations) model.reset()
        }
    }
    var deliveredRevision by rememberSaveable(sessionKey) { mutableStateOf(0) }
    val savedCallback by rememberUpdatedState(onSaved)
    LaunchedEffect(model.state.revision) {
        if (model.state.revision > deliveredRevision) {
            deliveredRevision = model.state.revision
            savedCallback("${model.state.first} ${model.state.last}")
        }
    }
    BackHandler { if (!model.state.saving) onBack() }
    ChangeNameScreen(model.state, model::first, model::last, model::save, onBack, model::refresh, onSignInAgain)
}
private fun Context.nameActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val next = context.baseContext
        if (next === context) return null
        context = next
    }
    return context as? Activity
}
