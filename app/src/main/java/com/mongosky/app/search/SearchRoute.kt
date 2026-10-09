package com.mongosky.app.search

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Rendered only in the visible Search destination; the host owns native profile navigation. */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onSignInAgain: () -> Unit,
    onOpenProfile: (SearchUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = checkNotNull(context.searchActivity()) { "SearchRoute needs an Activity context" }
    val lifecycleOwner = activity as LifecycleOwner
    DisposableEffect(viewModel, lifecycleOwner) {
        fun update() = viewModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycleOwner.lifecycle.addObserver(observer)
        update()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.setForeground(false) }
    }
    SearchScreen(
        state = viewModel.uiState, onQueryChange = viewModel::changeQuery,
        onClear = viewModel::clear, onRefresh = viewModel::refresh,
        onOpenProfile = onOpenProfile,
        onSignInAgain = onSignInAgain, modifier = modifier
    )
}

private fun Context.searchActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
