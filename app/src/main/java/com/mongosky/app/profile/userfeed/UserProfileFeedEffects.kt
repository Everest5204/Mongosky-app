package com.mongosky.app.profile.userfeed

import android.app.Activity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.profile.UserProfileState
import com.mongosky.app.profile.UserProfileViewModel
import com.mongosky.app.profile.ownProfileLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun rememberUserProfileFeed(profile: UserProfileViewModel, actionsModel: HomeFeedViewModel): UserProfileFeedController {
    val context = LocalContext.current.applicationContext
    val tokens = remember(context) { TokenStore(context) }
    return remember(profile, actionsModel, tokens) {
        profile.postFeed(tokens::read, { actionsModel.uiState.sessionExpired }, actionsModel::refresh)
    }
}

@Composable
internal fun UserProfileFeedEffects(feed: UserProfileFeedController, state: UserProfileState,
    list: LazyListState, active: Boolean, homeExpired: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.ownProfileLifecycleOwner() as? Activity }
    LaunchedEffect(feed, state.viewerId, state.userId, state.sessionExpired, homeExpired) {
        if (state.sessionExpired || homeExpired) feed.endSession() else feed.enter(state.viewerId, state.userId)
    }
    DisposableEffect(feed, state.userId, activity) {
        onDispose { feed.leave(preservePicker = activity?.isChangingConfigurations == true) }
    }
    LaunchedEffect(feed, active) { feed.controls.setForeground(active) }
    LaunchedEffect(feed, list) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect(feed.images::setScrolling)
    }
    // The existing UserProfileViewModel supplies one badge/follow read for all rows.
}
