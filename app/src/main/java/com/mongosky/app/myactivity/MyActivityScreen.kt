@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.myactivity

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

/** Bounded native LazyColumn; date headings and rows have stable identities. */
@Composable internal fun ActivityScreen(viewModel: ActivityViewModel, enabled: Boolean, onBack: () -> Unit, onSignInAgain: () -> Unit) {
    val state = viewModel.state; val list = rememberLazyListState(); val snackbar = remember { SnackbarHostState() }
    val canAct = enabled && !state.saving && !state.sessionExpired
    val zone = remember { ZoneId.systemDefault() }; val today = LocalDate.now(zone)
    BackHandler(state.confirmation == null && state.target == null) { if (enabled && !state.saving) onBack() }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress || list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 }
            .distinctUntilChanged().collect(viewModel::setBrowsing)
    }
    LaunchedEffect(state.filter) { list.scrollToItem(0) }
    LaunchedEffect(state.notice) { state.notice?.let { viewModel.consumeNotice(); snackbar.showSnackbar(it) } }
    Scaffold(containerColor = AppColors.surface(ActivityBackground), snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("My Activity",fontWeight = FontWeight.SemiBold,fontSize = 21.sp) },
            navigationIcon = { IconButton(onBack, enabled = enabled && !state.saving) { Icon(ActivityIcons.back,"Back") } },
            actions = { IconButton(viewModel::refresh, enabled = canAct && !state.loading && !state.refreshing) { Icon(ActivityIcons.refresh,"Refresh activity") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.surface(Color.White), titleContentColor = AppColors.content(ActivityText))) }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().background(AppColors.surface(Color.White)).padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(ActivityIcons.lock,null,tint = AppColors.content(ActivityMuted),modifier = Modifier.size(14.dp))
                        Text("Only you can see this activity",fontSize = 12.sp,color = AppColors.content(ActivityMuted))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityCategory.entries.forEach { c ->
                            FilterChip(state.filter.category == c,{ viewModel.select(c) },enabled = canAct,
                                label = { Text(c.label) },colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.surface(Color(0xFFE0F3EB)),selectedLabelColor = AppColors.content(ActivityGreen)))
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityRange.entries.forEach { r ->
                            FilterChip(state.filter.range == r,{viewModel.select(r)},enabled = canAct,label = { Text(r.label,fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.surface(ActivityBackground),selectedLabelColor = AppColors.content(ActivityText)))
                        }
                    }
                }
                if (state.updatesAvailable && !state.sessionExpired) TextButton(viewModel::refresh,enabled = canAct,modifier = Modifier.fillMaxWidth()) {
                    Text("Activity updated · Tap to refresh",color = AppColors.content(ActivityGreen))
                }
                PullToRefreshBox(state.refreshing,onRefresh = { if (canAct) viewModel.refresh() },modifier = Modifier.weight(1f)) {
                    LazyColumn(state = list,contentPadding = PaddingValues(start = 16.dp,end = 16.dp,top = 16.dp,bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),modifier = Modifier.fillMaxSize().testTag("activity-list")) {
                        item("summary") {
                            if (!state.loading && !state.sessionExpired && state.error == null) {
                                val count = if (state.filter.category == ActivityCategory.ALL) state.counts.values.sum() else state.counts[state.filter.category] ?: 0
                                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),horizontalArrangement = Arrangement.SpaceBetween,verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (state.filter.category == ActivityCategory.ALL) "Recent activity" else state.filter.category.label,
                                        fontSize = 15.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(ActivityText),modifier = Modifier.semantics { heading() })
                                    Text("$count ${if (count == 1L) "item" else "items"}",fontSize = 12.sp,color = AppColors.content(ActivityMuted))
                                }
                            }
                        }
                        if (state.sessionExpired) item("session") { ActivityMessage("Sign in again",state.error.orEmpty(),"Sign in",onSignInAgain) }
                        else if (state.loading && state.items.isEmpty()) item("loading") { ActivitySkeleton() }
                        else {
                            state.error?.let { error -> item("error") { ActivityMessage("Couldn't load activity",error,"Try again",viewModel::refresh) } }
                            if (state.items.isEmpty() && state.error == null) item("empty") { ActivityMessage("No activity yet",
                                if (state.filter.range == ActivityRange.ALL) "Your posts, reactions, comments and connections will appear here."
                                else "There is no activity in this category for the selected dates.") }
                            itemsIndexed(state.items,key = { _, row -> row.key },contentType = { _, _ -> "activity" }) { index, row ->
                                val date = row.createdAt.atZone(zone).toLocalDate()
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (index == 0 || state.items[index - 1].createdAt.atZone(zone).toLocalDate() != date) {
                                        val title = when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) }
                                        Text(title,fontSize = 12.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(ActivityMuted),
                                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp).semantics { heading() })
                                    }
                                    ActivityRow(row,canAct,state.targetLoading == row.key,{viewModel.open(row)},{viewModel.askToRemove(row)})
                                }
                            }
                            item("pagination") {
                                when {
                                    state.loadingMore -> Box(Modifier.fillMaxWidth().padding(18.dp),contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp),color = AppColors.content(ActivityGreen),strokeWidth = 2.dp) }
                                    state.pageError != null -> ActivityMessage("Couldn't load more",state.pageError,
                                        if (state.hasMore) "Try again" else "Refresh",if (state.hasMore) viewModel::loadMore else viewModel::refresh)
                                    state.hasMore -> { LaunchedEffect(state.items.lastOrNull()?.key,state.filter,canAct) { if (canAct) viewModel.loadMore() }; TextButton(viewModel::loadMore,enabled = canAct,modifier = Modifier.fillMaxWidth()) { Text("Load more",color = AppColors.content(ActivityGreen)) } }
                                    state.items.isNotEmpty() -> Text("You're all caught up",fontSize = 12.sp,color = AppColors.content(ActivityMuted),modifier = Modifier.fillMaxWidth().padding(18.dp),textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.confirmation?.let { item -> AlertDialog(onDismissRequest = viewModel::dismissConfirmation,
        title = { Text("${item.action.label}?") },text = { Text(item.action.confirmation,lineHeight = 23.sp) },
        confirmButton = { TextButton(viewModel::confirmRemove,enabled = enabled && !state.saving) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth = 2.dp)
            else Text(item.action.label,color = MaterialTheme.colorScheme.error)
        } },dismissButton = { TextButton(viewModel::dismissConfirmation,enabled = !state.saving) { Text("Cancel") } },containerColor = AppColors.surface(Color.White),shape = RoundedCornerShape(22.dp)) }
}
