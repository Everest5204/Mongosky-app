package com.mongosky.app.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun AiHost(viewModel:AiViewModel,identity:String,signingOut:Boolean,active:Boolean) {
    val lifecycleOwner=LocalLifecycleOwner.current
    val activity=LocalContext.current.aiActivity()
    LaunchedEffect(viewModel,identity,signingOut) {
        if(signingOut) viewModel.endSession() else viewModel.controller.bindSession(identity)
    }
    LaunchedEffect(viewModel,active,signingOut) { viewModel.controller.setActive(active && !signingOut) }
    DisposableEffect(viewModel,lifecycleOwner,signingOut) {
        val lifecycle=lifecycleOwner.lifecycle
        val observer=LifecycleEventObserver { _,_ ->
            viewModel.controller.setForeground(!signingOut && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        viewModel.controller.setForeground(!signingOut && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            // Rotation retains the Activity-owned ViewModel and chat state.
            if(activity?.isChangingConfigurations!=true) viewModel.controller.setForeground(false)
        }
    }
}

@Composable
internal fun AiKeyboardWindow() {
    val activity=LocalContext.current.aiActivity()
    DisposableEffect(activity) {
        val window=activity?.window
        val previous=window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if(previous!=null) window?.setSoftInputMode(previous) }
    }
}
private fun Context.aiActivity():Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.aiActivity()
    else -> null
}
