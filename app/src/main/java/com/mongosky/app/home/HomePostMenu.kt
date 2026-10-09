@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.HomePost

/** A single native bottom sheet owned by Home, rather than a popup in every card. */
@Composable
internal fun HomePostMenu(post: HomePost, onDismiss: () -> Unit, onAction: (HomeMenuAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text("Post options", color = FeedColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
            homeMenuActions(post).forEach { action ->
                val tint = if (action == HomeMenuAction.DELETE) FeedColors.brand else FeedColors.text
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    .clickable(role = Role.Button, onClick = { onAction(action) })
                    .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Icon(HomeFeedIcons.forAction(action), null, tint = tint, modifier = Modifier.size(23.dp))
                    Text(action.label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun HomeDeleteDialog(state: HomeDeleteState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!state.busy) onDismiss() }, containerColor = Color.White,
        title = { Text("Delete post?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This post and its comments will be permanently removed.")
            state.error?.let { Text(it, color = FeedColors.brand, fontSize = 14.sp) }
        } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !state.busy) {
            Text(if (state.busy) "Deleting…" else if (state.uncertain) "Check again" else "Delete", color = FeedColors.brand)
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancel", color = FeedColors.muted) } })
}
