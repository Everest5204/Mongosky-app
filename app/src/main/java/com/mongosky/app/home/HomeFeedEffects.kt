package com.mongosky.app.home

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

private fun Context.homeLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.homeLifecycleOwner() else null
    else -> null
}

/** Optional reads wait until the list settles; pagination remains active during a fling. */
@Composable
internal fun HomeFeedEffects(viewModel: HomeFeedViewModel, list: LazyListState) {
    val context = LocalContext.current
    val owner = remember(context) { context.homeLifecycleOwner() }
    var resumed by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    DisposableEffect(viewModel, owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
            viewModel.setForeground(resumed)
        }
        owner?.lifecycle?.addObserver(observer)
        viewModel.setForeground(resumed)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            viewModel.setForeground(false); viewModel.setScrolling(false)
        }
    }
    LaunchedEffect(list, viewModel) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect(viewModel::setScrolling)
    }
    LaunchedEffect(list, viewModel, resumed, viewModel.uiState.refreshVersion) {
        if (resumed) snapshotFlow {
            Triple(list.isScrollInProgress,
                list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet(),
                viewModel.uiState.loading || viewModel.uiState.refreshing)
        }.distinctUntilChanged().collectLatest { (scrolling, keys, loading) ->
            if (!scrolling && !loading && keys.isNotEmpty()) {
                delay(HomeFeedPolicy.SETTLE_MILLIS)
                viewModel.visiblePosts(keys)
                // LazyColumn already precomposes the next card. Do not start a second,
                // independent set of large bitmap decodes while visible photos load.
            }
        }
    }
    LaunchedEffect(viewModel, resumed, viewModel.uiState.sessionReady, viewModel.uiState.sessionExpired) {
        if (resumed && viewModel.uiState.sessionReady && !viewModel.uiState.sessionExpired) {
            viewModel.checkForUpdates()
            while (isActive) {
                delay(HomeFeedPolicy.POLL_MILLIS)
                viewModel.checkForUpdates()
            }
        }
    }
}
