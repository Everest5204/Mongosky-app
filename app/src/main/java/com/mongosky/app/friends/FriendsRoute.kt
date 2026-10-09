package com.mongosky.app.friends

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

@Composable
fun FriendsRoute(
    viewModel: FriendsViewModel, onBack: () -> Unit, onSignInAgain: () -> Unit,
    onOpenProfile: (FriendsUser) -> Unit, modifier: Modifier = Modifier
) {
    val activity = checkNotNull(LocalContext.current.friendsActivity()) { "FriendsRoute needs an Activity context" }
    val owner = activity as LifecycleOwner
    DisposableEffect(viewModel, owner) {
        fun update() = viewModel.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.setForeground(false) }
    }
    FriendsScreen(state = viewModel.uiState, onTab = viewModel::selectTab, onBack = onBack,
        onRefresh = viewModel::refresh, onLoadMore = viewModel::loadMore, onFollow = viewModel::toggleFollow,
        onOpenProfile = onOpenProfile, onVisibleUsers = viewModel::resolveVisibleBadges,
        onSignInAgain = onSignInAgain, onDismissMessage = viewModel::dismissMessage, modifier = modifier)
}

private fun Context.friendsActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
