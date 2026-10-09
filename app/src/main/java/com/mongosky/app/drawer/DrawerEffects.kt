package com.mongosky.app.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mongosky.app.profile.OwnProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

/** Delay reads until the opening animation settles; poll only while this drawer is visible. */
@Composable
internal fun DrawerEffects(viewModel: DrawerViewModel, ownProfile: OwnProfileViewModel,
    userId: String?, visible: Boolean, signingOut: Boolean) {
    val owner = LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel, userId, signingOut) {
        if (signingOut) viewModel.endSession() else viewModel.startSession(userId)
    }
    LaunchedEffect(viewModel, ownProfile, userId, signingOut) {
        if (!signingOut) ownProfile.state.map { it.profile?.let(DrawerProfile::from) }
            .distinctUntilChanged().collect(viewModel::adoptProfile)
    }
    val active = visible && started && !signingOut
    LaunchedEffect(viewModel, active, userId) {
        viewModel.setActive(active)
        if (active) {
            delay(DrawerPolicy.SETTLE_MILLIS)
            while (isActive) { viewModel.refresh(); delay(DrawerPolicy.PROFILE_INTERVAL_MILLIS) }
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.setActive(false) } }
}
