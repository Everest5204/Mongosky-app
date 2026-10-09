@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stateless, full-page native UI; the host hides app navigation while Search is open. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: (SearchUser) -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    var lastQuery by rememberSaveable { mutableStateOf(state.normalizedQuery) }
    LaunchedEffect(Unit) {
        if (!focusedOnce && !state.sessionExpired) {
            focusRequester.requestFocus()
            focusedOnce = true
        }
    }
    LaunchedEffect(state.normalizedQuery) {
        if (lastQuery != state.normalizedQuery) {
            listState.scrollToItem(0)
            lastQuery = state.normalizedQuery
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White).imePadding(), contentAlignment = Alignment.TopCenter) {
        val horizontalPadding = if (maxWidth >= 600.dp) 28.dp else 20.dp
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            Text("Search", color = SearchColors.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp,
                modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 22.dp, bottom = 16.dp))
            SearchInput(
                query = state.query, busy = state.status == SearchStatus.LOADING,
                focusRequester = focusRequester, onChange = onQueryChange, onClear = onClear,
                onSubmit = { keyboard?.hide(); onRefresh() },
                modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 22.dp)
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (state.error != null && state.status == SearchStatus.RESULTS) {
                        item(key = "refresh-error", contentType = "notice") {
                            SearchRefreshError(state.error, horizontalPadding, onRefresh)
                        }
                    }
                    when {
                        state.sessionExpired -> item(key = "session", contentType = "state") {
                            SearchStatePanel("Sign in again", state.error.orEmpty(), SearchIcons.person,
                                modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                                actionLabel = "Sign in again", onAction = onSignInAgain)
                        }
                        state.status == SearchStatus.IDLE -> item(key = "idle", contentType = "state") {
                            SearchStatePanel("Search for people", "Enter a first name or last name to get started.",
                                SearchIcons.peopleSearch, modifier = Modifier.fillParentMaxHeight().fillMaxWidth())
                        }
                        state.status == SearchStatus.LOADING -> item(key = "loading", contentType = "skeleton") {
                            SearchSkeletonList(horizontalPadding)
                        }
                        state.status == SearchStatus.ERROR -> item(key = "error", contentType = "state") {
                            SearchStatePanel("Search unavailable", state.error ?: "Please check your connection and try again.",
                                SearchIcons.search, modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                                error = true, actionLabel = "Try again", onAction = onRefresh)
                        }
                        state.users.isEmpty() -> item(key = "empty", contentType = "state") {
                            SearchStatePanel("No users found", "No account matches “${state.normalizedQuery}”. Try another name.",
                                SearchIcons.person, modifier = Modifier.fillParentMaxHeight().fillMaxWidth())
                        }
                        else -> {
                            item(key = "summary", contentType = "heading") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("People", color = SearchColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${state.users.size} ${if (state.users.size == 1) "result" else "results"}",
                                        color = SearchColors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                            }
                            items(state.users, key = { user -> "person:${user.id}" }, contentType = { "person" }) { user ->
                                SearchPersonRow(user, horizontalPadding) { keyboard?.hide(); onOpenProfile(user) }
                            }
                            if (state.users.size == SearchPolicy.RESULT_LIMIT) item(key = "limit", contentType = "notice") {
                                Text("Refine the name for more specific results.", color = SearchColors.muted, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
