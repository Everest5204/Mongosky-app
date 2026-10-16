@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.saveditems

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/** Small pages in a bounded LazyColumn; original media is loaded only after opening a post. */
@Composable internal fun SavedItemsScreen(model: SavedItemsViewModel,enabled: Boolean,onBack: () -> Unit,onSignInAgain: () -> Unit) {
    val colors = rememberSavedUiColors()
    val epoch = model.generation;val state = model.state;val list = rememberLazyListState();val keyboard = LocalSoftwareKeyboardController.current
    val canAct = enabled && model.ready && !model.sessionExpired
    BackHandler(state.target == null) { if (enabled) onBack() }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress || list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 }
        .distinctUntilChanged().collect(model::setBrowsing) }
    LaunchedEffect(state.filter) { list.scrollToItem(0) }
    val itemIds = remember(state.items) { state.items.map { it.postId }.toSet() }
    LaunchedEffect(list,itemIds) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.filter { it in itemIds }.take(12).toSet() }
            .distinctUntilChanged().collect(model::visible)
    }
    Scaffold(containerColor = colors.background,topBar = {
        TopAppBar(title = { Text(appText("Saved Items"),fontSize = 21.sp,fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onBack,enabled = enabled) { Icon(SavedIcons.back,appDescription("Back")) } },
            actions = { IconButton(model::refresh,enabled = canAct && !state.loading && !state.refreshing) { Icon(SavedIcons.refresh,appDescription("Refresh saved items")) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface,titleContentColor = colors.text))
    }) { inset -> Box(Modifier.fillMaxSize().padding(inset).imePadding(),contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(SavedIcons.lock,null,tint = colors.muted,modifier = Modifier.size(14.dp))
                    Text(appText("Your saves are private"),fontSize = 12.sp,color = colors.muted)
                }
                OutlinedTextField(state.searchDraft,model::search,enabled = canAct,modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("saved-search"),
                    singleLine = true,placeholder = { Text(appText("Search saved posts"),fontSize = 14.sp) },leadingIcon = { Icon(SavedIcons.search,null,modifier = Modifier.size(21.dp)) },
                    trailingIcon = { if (state.searchDraft.isNotEmpty()) IconButton({ model.search("") }) { Icon(SavedIcons.close,appDescription("Clear search"),modifier = Modifier.size(20.dp)) } },
                    shape = RoundedCornerShape(15.dp),keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent,unfocusedBorderColor = colors.searchBorder))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedKind.entries.forEach { kind -> FilterChip(state.filter.kind == kind,{ keyboard?.hide();model.select(kind) },enabled = canAct,label = { Text(appText(kind.label)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.selectedContainer,selectedLabelColor = colors.selectedText)) }
                }
            }
            if (state.updatesAvailable && !model.sessionExpired) TextButton(model::refresh,enabled = canAct,modifier = Modifier.fillMaxWidth()) { Text(appText("Saved items updated · Tap to refresh"),color = colors.accent) }
            PullToRefreshBox(state.refreshing,onRefresh = { if (canAct) model.refresh() },modifier = Modifier.weight(1f)) {
                LazyColumn(state = list,modifier = Modifier.fillMaxSize().testTag("saved-list"),contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        model.sessionExpired -> item("session") { SavedMessage("Sign in again",state.error.orEmpty(),"Sign in",onSignInAgain) }
                        state.loading && state.items.isEmpty() -> item("loading") { SavedSkeleton() }
                        else -> {
                            state.error?.let { message -> item("error") { SavedMessage("Couldn't load saved items",message,"Try again",model::refresh) } }
                            if (state.items.isEmpty() && state.error == null) item("empty") {
                                val filtered = state.filter.kind != SavedKind.ALL || state.filter.search.isNotEmpty()
                                SavedMessage(if (filtered) "No matching saved items" else "Save something for later",
                                    if (filtered) "Try another search or choose a different type." else "Save posts and videos from their options. You can find them here anytime.",
                                    if (filtered) "Clear filters" else null,model::clearFilters)
                            }
                            items(state.items,key = { it.postId },contentType = { "saved-item" }) { item ->
                                SavedItemRow(item,canAct,state.targetLoading == item.postId,model.cell(item.postId).saving,{ model.open(item,epoch) },{ model.remove(item,epoch) })
                            }
                            item("pagination") { when {
                                state.loadingMore -> Box(Modifier.fillMaxWidth().padding(18.dp),contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp),color = colors.accent,strokeWidth = 2.dp) }
                                state.pageError != null -> SavedMessage("Couldn't load more",state.pageError,if (state.hasMore) "Try again" else "Refresh",if (state.hasMore) model::loadMore else model::refresh)
                                state.hasMore -> { LaunchedEffect(state.items.lastOrNull()?.postId,state.filter,canAct) { if (canAct) model.loadMore() };TextButton(model::loadMore,enabled = canAct,modifier = Modifier.fillMaxWidth()) { Text(appText("Load more"),color = colors.accent) } }
                                state.items.isNotEmpty() -> Text(appText("No more saved items"),fontSize = 12.sp,color = colors.muted,textAlign = androidx.compose.ui.text.style.TextAlign.Center,modifier = Modifier.fillMaxWidth().padding(18.dp))
                            } }
                        }
                    }
                }
            }
        }
    } }
}
