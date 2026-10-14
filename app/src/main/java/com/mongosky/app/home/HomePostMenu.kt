@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.home

import com.mongosky.app.darkmode.AppColors

import com.mongosky.app.saveditems.SavedMenuAction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.HomePost
import com.mongosky.app.pinpost.PinPostController
import kotlinx.coroutines.launch

/** A single native bottom sheet owned by Home, rather than a popup in every card. */
@Composable
internal fun HomePostMenu(post: HomePost, onDismiss: () -> Unit, pins: PinPostController? = null, onAction: (HomeMenuAction) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var openingReport by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = AppColors.surface(Color.White), tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text("Post options", color = AppColors.content(FeedColors.text), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
            homeMenuActions(post).forEach { action ->
                if (action == HomeMenuAction.SAVE) {
                    SavedMenuAction(post.id,onDismiss,sheet)
                    return@forEach
                }
                val tint = if (action == HomeMenuAction.DELETE) FeedColors.brand else FeedColors.text
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .clickable(enabled = !openingReport, role = Role.Button, onClick = {
                        if (action == HomeMenuAction.REPORT || action == HomeMenuAction.PIN || action == HomeMenuAction.DOWNLOAD) {
                            openingReport = true
                            scope.launch { sheet.hide(); onAction(action) }
                        } else onAction(action)
                    })
                    .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Icon(HomeFeedIcons.forAction(action), null, tint = AppColors.content(tint), modifier = Modifier.size(23.dp))
                    Text(if (action == HomeMenuAction.PIN) pins?.label(post) ?: "Pin to profile" else action.label, color = AppColors.content(tint), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun HomeDeleteDialog(state: HomeDeleteState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!state.busy) onDismiss() }, containerColor = AppColors.surface(Color.White),
        title = { Text("Delete post?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This post and its comments will be permanently removed.")
            state.error?.let { Text(it, color = AppColors.content(FeedColors.brand), fontSize = 14.sp) }
        } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !state.busy) {
            Text(if (state.busy) "Deleting…" else if (state.uncertain) "Check again" else "Delete", color = AppColors.content(FeedColors.brand))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel", color = AppColors.content(FeedColors.muted)) } })
}
