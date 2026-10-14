package com.mongosky.app.saveditems

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.reels.Reel

@Composable internal fun SavedItemsRoute(model: SavedItemsViewModel,feed: HomeFeedViewModel,ownerId: String,sessionRevision: Long,
    userName: String,hostBusy: Boolean,onBack: () -> Unit,onSignInAgain: () -> Unit,onOpenReel: (Reel) -> Unit) {
    LaunchedEffect(ownerId,sessionRevision) { if (!hostBusy) model.bind(ownerId,sessionRevision) }
    DisposableEffect(model,model.generation,hostBusy) {
        if (!hostBusy) model.observe(SavedItemsViewModel.PAGE)
        onDispose { model.stopObserving(SavedItemsViewModel.PAGE);feed.closeOverlays() }
    }
    val target = model.state.target
    LaunchedEffect(target?.reel?.id) { if (!hostBusy) target?.reel?.let { model.closeTarget();onOpenReel(it) } }
    Box(Modifier.fillMaxSize()) {
        SavedItemsScreen(model,!hostBusy && target == null,onBack,onSignInAgain)
        if (target?.post != null && !model.sessionExpired) SavedPostDetail(target,feed,userName,!hostBusy,model::closeTarget,onSignInAgain)
    }
}
