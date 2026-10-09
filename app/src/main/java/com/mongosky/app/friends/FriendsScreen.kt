@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)

package com.mongosky.app.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FriendsScreen(
    state: FriendsUiState, onTab: (FriendsTab) -> Unit, onBack: () -> Unit,
    onRefresh: () -> Unit, onLoadMore: () -> Unit, onFollow: (String) -> Unit,
    onOpenProfile: (FriendsUser) -> Unit, onVisibleUsers: (List<String>) -> Unit,
    onSignInAgain: () -> Unit, onDismissMessage: () -> Unit, modifier: Modifier = Modifier
) {
    val followersScroll = rememberLazyListState()
    val followingScroll = rememberLazyListState()
    val list = if (state.activeTab == FriendsTab.FOLLOWERS) followersScroll else followingScroll
    val page = state.activeList
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onDismissMessage()
    }
    LaunchedEffect(list, state.activeTab, page.users.size, page.nextCursor, page.hasMore, page.refreshing, page.loadingMore, page.moreError, page.error) {
        if (page.hasMore && !page.refreshing && !page.loadingMore && page.moreError == null && page.error == null) {
            snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= page.users.size - 6 }
                .distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
        }
    }
    LaunchedEffect(list, state.activeTab, page.users) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { (it.key as? String)?.removePrefix("person:")?.takeIf { id -> FriendsPolicy.userId(id) == id } } }
            .debounce(150).distinctUntilChanged().collect(onVisibleUsers)
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val padding = if (wide) 20.dp else 14.dp
        val stackTabs = fontScale > 1.3f
        val showTabIcons = maxWidth >= 380.dp && fontScale <= 1.25f
        Box(Modifier.fillMaxSize().background(if (wide) FriendsColors.canvas else Color.White)
            .padding(horizontal = if (wide) 16.dp else 0.dp, vertical = if (wide) 16.dp else 0.dp), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight()
                .clip(RoundedCornerShape(if (wide) 22.dp else 0.dp)).background(Color.White)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text("Connections", modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 56.dp),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FriendsColors.text,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(FriendsIcons.back, "Go back", tint = FriendsColors.text)
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.align(Alignment.CenterEnd),
                        enabled = !page.refreshing && !state.sessionExpired && state.pendingIds.isEmpty()) {
                        Icon(FriendsIcons.refresh, "Refresh connections", tint = FriendsColors.secondary, modifier = Modifier.size(21.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    FriendsTab.entries.forEach { tab ->
                        val selected = tab == state.activeTab
                        val count = if (tab == FriendsTab.FOLLOWERS) state.followersCount else state.followingCount
                        val foreground = if (selected) FriendsColors.brand else FriendsColors.secondary
                        Column(Modifier.weight(1f).selectable(selected, enabled = !state.sessionExpired,
                            role = Role.Tab, onClick = { onTab(tab) }).semantics {
                                contentDescription = if (state.countsLoaded) "${tab.label}, $count" else tab.label
                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                            if (stackTabs) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tab.label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    FriendsCountPill(count, state.countsLoaded, selected)
                                }
                            } else {
                                Row(Modifier.heightIn(min = 54.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (showTabIcons) Icon(if (tab == FriendsTab.FOLLOWERS) FriendsIcons.people else FriendsIcons.following,
                                        null, tint = foreground, modifier = Modifier.size(19.dp))
                                    Text(tab.label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    FriendsCountPill(count, state.countsLoaded, selected)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) FriendsColors.brand else Color.Transparent))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (page.refreshing && page.loaded) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FriendsColors.brand,
                        trackColor = FriendsColors.brand.copy(alpha = .06f))
                }
                PullToRefreshBox(isRefreshing = page.refreshing && page.loaded,
                    onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        when {
                            state.sessionExpired -> item(key = "session", contentType = "state") {
                                FriendsStatePanel("Sign in again", "Your session has expired. Sign in to see your connections.", FriendsIcons.person,
                                    Modifier.fillParentMaxHeight().fillMaxWidth(), "Sign in again", onSignInAgain)
                            }
                            !page.loaded && page.error != null -> item(key = "error", contentType = "state") {
                                FriendsStatePanel("Connections unavailable", page.error, FriendsIcons.people,
                                    Modifier.fillParentMaxHeight().fillMaxWidth(), "Try again", onRefresh)
                            }
                            !page.loaded -> item(key = "loading", contentType = "skeleton") { FriendsSkeleton(padding) }
                            else -> {
                                if (page.error != null) item(key = "refresh-error", contentType = "notice") {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(page.error, Modifier.weight(1f), fontSize = 12.sp, color = FriendsColors.secondary)
                                        TextButton(onClick = onRefresh) { Text("Retry", color = FriendsColors.brand) }
                                    }
                                }
                                if (page.users.isEmpty() && !page.hasMore) item(key = "empty", contentType = "state") {
                                    FriendsStatePanel(if (state.activeTab == FriendsTab.FOLLOWERS) "No followers yet" else "Not following anyone yet",
                                        if (state.activeTab == FriendsTab.FOLLOWERS) "People who follow you will appear here." else "People you follow will appear here.",
                                        if (state.activeTab == FriendsTab.FOLLOWERS) FriendsIcons.people else FriendsIcons.following,
                                        Modifier.fillParentMaxHeight().fillMaxWidth())
                                }
                                items(page.users, key = { "person:${it.id}" }, contentType = { "person" }) { user ->
                                    FriendsPersonRow(user, user.id in state.pendingIds, user.id in state.uncertainIds,
                                        actionEnabled = !page.refreshing, horizontalPadding = padding,
                                        onProfile = { onOpenProfile(user) }, onFollow = { onFollow(user.id) })
                                }
                                if (page.moreError != null) item(key = "more-error", contentType = "notice") {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(page.moreError, fontSize = 12.sp, color = FriendsColors.secondary)
                                        TextButton(onClick = onLoadMore) { Text("Try again", color = FriendsColors.brand) }
                                    }
                                } else if (page.hasMore) item(key = "load-more", contentType = "loader") {
                                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                        if (page.loadingMore) CircularProgressIndicator(Modifier.size(23.dp), color = FriendsColors.brand, strokeWidth = 2.dp)
                                        else TextButton(onClick = onLoadMore) { Text("Load more", color = FriendsColors.brand) }
                                    }
                                }
                                item(key = "space", contentType = "space") { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable
private fun FriendsCountPill(count: Long, loaded: Boolean, selected: Boolean) {
    Text(if (loaded) friendsCount(count) else "—", color = if (selected) FriendsColors.brand else FriendsColors.secondary,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) FriendsColors.brand.copy(alpha = .07f) else Color(0xFFF3F4F6))
            .padding(horizontal = 7.dp, vertical = 3.dp).semantics { liveRegion = LiveRegionMode.Polite })
}
