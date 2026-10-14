package com.mongosky.app.myactivity

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.profile.LocalProfileNavigator
import com.mongosky.app.profile.ProfileOpenRequest
import com.mongosky.app.reels.Reel

@Composable internal fun ActivityRoute(viewModel: ActivityViewModel, feed: HomeFeedViewModel,
    ownerId: String, sessionRevision: Long, userName: String, hostBusy: Boolean,
    onBack: () -> Unit, onSignInAgain: () -> Unit, onOpenReel: (Reel) -> Unit, onChanged: () -> Unit = feed::refresh) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val navigateProfile = LocalProfileNavigator.current
    LaunchedEffect(ownerId,sessionRevision) { viewModel.enter(ownerId,sessionRevision) }
    val latestChange by rememberUpdatedState(onChanged)
    LaunchedEffect(viewModel,ownerId,sessionRevision) { viewModel.mutations.collect { latestChange() } }
    DisposableEffect(viewModel,lifecycle,hostBusy) {
        val observer = LifecycleEventObserver { _, _ -> viewModel.setForeground(!hostBusy && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        viewModel.setForeground(!hostBusy && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); viewModel.setForeground(false); feed.closeOverlays() }
    }
    val target = viewModel.state.target
    LaunchedEffect(target?.profile?.id,target?.reel?.id) {
        if (!hostBusy) {
            target?.profile?.let { viewModel.closeTarget(); navigateProfile(ProfileOpenRequest(it.id,it)) }
            target?.reel?.let { viewModel.closeTarget(); onOpenReel(it) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Retain the list and its scroll position while opening a native post.
        ActivityScreen(viewModel,!hostBusy && target == null,onBack,onSignInAgain)
        if (target?.post != null && !viewModel.state.sessionExpired) {
            ActivityPostDetail(target,feed,userName,!hostBusy,{viewModel.closeTarget()},onSignInAgain)
        }
    }
}
