@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.profile.OwnProfileConnectionsController
import com.mongosky.app.shared.ProfileAvatar
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun OwnProfileConnectionsSheet(controller: OwnProfileConnectionsController, onProfile: (String) -> Unit) {
    val state by controller.state.collectAsState()
    val kind = state.kind ?: return
    val list = rememberLazyListState()
    val lastKeys = remember(state.items) { state.items.takeLast(3).map { it.id }.toSet() }
    LaunchedEffect(list, lastKeys, state.loading, state.error, state.hasMore) {
        if (lastKeys.isNotEmpty() && !state.loading && state.error == null && state.hasMore)
            snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key in lastKeys } }.distinctUntilChanged()
                .collect { if (it) controller.loadMore() }
    }
    ModalBottomSheet(onDismissRequest = controller::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(controller::close) { Icon(FeedIcons.close, "Close list", tint = OwnProfileColors.text) }
                Text(kind.label, color = OwnProfileColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            OwnProfileDivider()
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }, contentType = { "connection" }) { person ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileAvatar(person.imageUrl, Modifier.size(44.dp).clickable { onProfile(person.id) })
                        Column(Modifier.weight(1f).clickable { onProfile(person.id) }.padding(vertical = 8.dp)) {
                            Text(person.displayName.ifBlank { "Mongosky User" }, color = OwnProfileColors.text, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("View profile", color = OwnProfileColors.muted, fontSize = 12.sp)
                        }
                        if (!person.isSelf) TextButton({ controller.toggleFollowing(person.id) }, enabled = person.id !in state.busyIds) {
                            if (person.id in state.busyIds) OwnProfileSpinner() else Text(
                                if (person.isFollowing) "Following" else if (person.followsYou) "Follow Back" else "Follow",
                                color = OwnProfileColors.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (state.loading) item("loading") { Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { OwnProfileSpinner() } }
                state.error?.let { error -> item("error") { OwnProfileNotice(error, "Retry", controller::retry) } }
                if (state.items.isEmpty() && !state.loading && state.error == null) item("empty") {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("No ${kind.path} yet", color = OwnProfileColors.muted) }
                }
                if (state.hasMore && !state.loading && state.error == null && state.items.isNotEmpty()) item("more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(controller::loadMore) { Text("Load more", color = OwnProfileColors.brand) } }
                }
            }
        }
    }
}
