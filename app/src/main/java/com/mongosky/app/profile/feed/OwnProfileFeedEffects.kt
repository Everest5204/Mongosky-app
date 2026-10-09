package com.mongosky.app.profile.feed

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.home.HomeFeedPolicy
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.profile.OwnProfileState
import com.mongosky.app.profile.OwnProfileViewModel
import com.mongosky.app.profile.toHomePost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun rememberOwnProfileFeed(profile: OwnProfileViewModel, actionsModel: HomeFeedViewModel): OwnProfileFeedController {
    val context = LocalContext.current.applicationContext
    val tokens = remember(context) { TokenStore(context) }
    return remember(profile, actionsModel, tokens) {
        profile.postFeed(tokens::read,
            { actionsModel.uiState.sessionExpired }, actionsModel::refresh)
    }
}

/** Optional metadata and late image geometry wait for this profile's list to settle. */
@Composable
internal fun OwnProfileFeedEffects(feed: OwnProfileFeedController, state: OwnProfileState,
    list: LazyListState, active: Boolean, signingOut: Boolean) {
    LaunchedEffect(feed, state.ownerId, signingOut) {
        if (signingOut) feed.endSession() else feed.enter(state.ownerId)
    }
    DisposableEffect(feed) { onDispose { feed.leave() } }
    LaunchedEffect(feed, active) { feed.controls.setForeground(active) }
    LaunchedEffect(feed, list) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect(feed.images::setScrolling)
    }
    LaunchedEffect(feed, list, active, feed.ready, state.posts, state.profile, state.postPageVersion) {
        if (active && feed.ready) snapshotFlow {
            list.isScrollInProgress to list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
        }.distinctUntilChanged().collectLatest { (scrolling, keys) ->
            if (!scrolling && keys.isNotEmpty()) {
                delay(HomeFeedPolicy.SETTLE_MILLIS)
                val visible = state.posts.filter { "post:${it.source}:${it.id}" in keys }
                    .map { it.toHomePost(state.profile?.author ?: it.author, isSelf = true) }
                visible.forEach(feed.controls::author)
                feed.controls.visible(visible)
            }
        }
    }
}
