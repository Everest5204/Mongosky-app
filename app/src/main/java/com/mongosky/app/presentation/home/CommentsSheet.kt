@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.domain.model.feed.PostComment
import com.mongosky.app.presentation.home.cards.FeedColors
import com.mongosky.app.presentation.home.cards.FeedDivider
import com.mongosky.app.presentation.home.cards.FeedIcons
import com.mongosky.app.presentation.home.cards.PostAvatar
import com.mongosky.app.presentation.home.cards.compactCount
import com.mongosky.app.presentation.home.cards.postTimeAgo
import java.time.Instant

@Composable
internal fun CommentsSheet(state: CommentsState, userName: String, viewModel: HomeFeedViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var deleteTarget by remember { mutableStateOf<PostComment?>(null) }
    val enabled = !viewModel.uiState.sessionExpired
    val thread = state.current
    LaunchedEffect(thread.parent?.id) { listState.scrollToItem(0) }

    ModalBottomSheet(onDismissRequest = viewModel::closeComments, sheetState = sheetState,
        containerColor = Color.White, tonalElevation = 0.dp) {
        BackHandler(enabled = state.threads.size > 1) { viewModel.backComments() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::backComments, modifier = Modifier.size(48.dp)) {
                    Icon(if (state.threads.size > 1) FeedIcons.back else FeedIcons.close,
                        if (state.threads.size > 1) "Back to comments" else "Close comments", tint = FeedColors.text)
                }
                Text(if (thread.parent == null) "Comments" else "Replies", color = FeedColors.text,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::retryComments, enabled = enabled && !thread.loading && !state.sending) {
                    Text("Refresh", color = FeedColors.muted, fontSize = 13.sp)
                }
            }
            FeedDivider()
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                thread.parent?.let { parent -> item(key = "parent:${parent.id}") {
                    Column(Modifier.fillMaxWidth().background(FeedColors.soft).padding(16.dp)) {
                        Text(parent.author.displayName.ifBlank { "Mongosky User" }, color = FeedColors.text,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(if (parent.isDeleted) "Comment deleted" else parent.text, color = FeedColors.muted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                } }
                items(thread.comments, key = { it.id }, contentType = { "comment" }) { comment ->
                    CommentRow(comment, busy = comment.id in state.busyIds, enabled = enabled && !state.sending,
                        onLove = { viewModel.loveComment(comment) }, onReply = { viewModel.openReplies(comment) },
                        onEdit = { viewModel.editComment(comment) }, onDelete = { deleteTarget = comment })
                }
                if (thread.loading) item(key = "comments_loading") {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { FeedSpinner() }
                }
                if (thread.error != null) item(key = "comments_error") {
                    FeedNotice(thread.error, "Retry", viewModel::retryComments)
                }
                if (!thread.loading && thread.initialized && thread.comments.isEmpty()) item(key = "no_comments") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(if (thread.parent == null) "Be the first to comment" else "No replies yet", color = FeedColors.muted, fontSize = 14.sp)
                    }
                }
                if (thread.initialized && thread.hasMore && !thread.loading && thread.error == null) {
                    item(key = "more_comments") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = viewModel::loadComments, enabled = enabled) { Text("Load more", fontSize = 14.sp) }
                        }
                    }
                }
            }
            if (state.error != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.error, color = FeedColors.brand, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::retryComments, enabled = enabled && !thread.loading && !state.sending) { Text("Refresh", fontSize = 12.sp) }
            }
            FeedDivider()
            if (state.editing != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Editing comment", color = FeedColors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::cancelEdit, enabled = !state.sending) { Text("Cancel", fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.draft, onValueChange = viewModel::setCommentDraft,
                    modifier = Modifier.weight(1f), enabled = enabled && !state.sending,
                    placeholder = { Text(if (state.editing != null) "Edit your comment…" else if (thread.parent != null)
                        "Write a reply…" else "Write a comment…", fontSize = 14.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = FeedColors.text),
                    minLines = 1, maxLines = 4, shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White, focusedBorderColor = FeedColors.line, unfocusedBorderColor = FeedColors.line,
                        cursorColor = FeedColors.brand),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendComment() }))
                val canSend = enabled && !state.sending && state.draft.isNotBlank()
                IconButton(onClick = viewModel::sendComment, enabled = canSend, modifier = Modifier.size(48.dp)
                    .clip(CircleShape).background(if (canSend || state.sending) FeedColors.brand else FeedColors.line)) {
                    if (state.sending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(FeedIcons.share, if (state.editing == null) "Send comment as $userName" else "Save comment", tint = if (canSend) Color.White else FeedColors.muted,
                        modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    deleteTarget?.let { comment ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, containerColor = Color.White,
            title = { Text("Delete comment?", fontSize = 18.sp) },
            text = { Text("This comment will be removed.", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { deleteTarget = null; viewModel.deleteComment(comment) }) { Text("Delete", color = FeedColors.brand) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = FeedColors.muted) } })
    }
}

@Composable
private fun CommentRow(comment: PostComment, busy: Boolean, enabled: Boolean,
    onLove: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember(comment.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        PostAvatar(comment.author, Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FeedColors.soft)
                .padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(comment.author.displayName.ifBlank { "Mongosky User" }, color = FeedColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (comment.isDeleted) "Comment deleted" else comment.text, color = if (comment.isDeleted) FeedColors.muted else FeedColors.text,
                    fontSize = 15.sp, lineHeight = 21.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(postTimeAgo(comment.createdAt, Instant.now()), color = FeedColors.muted, fontSize = 11.sp)
                if (!comment.isDeleted) {
                    TextButton(onClick = onLove, enabled = enabled && !busy) {
                        Text(if (comment.isLovedByMe) "❤️ ${if (comment.lovesCount > 0) compactCount(comment.lovesCount) else "Love"}"
                            else if (comment.lovesCount > 0) "Love ${compactCount(comment.lovesCount)}" else "Love",
                            color = if (comment.isLovedByMe) FeedColors.brand else FeedColors.muted,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onReply, enabled = enabled && !busy) { Text("Reply", color = FeedColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = FeedColors.muted, strokeWidth = 1.5.dp)
            }
            if (comment.repliesCount > 0) Text("View ${compactCount(comment.repliesCount)} ${if (comment.repliesCount == 1L) "reply" else "replies"}",
                color = FeedColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = enabled && !busy, onClick = onReply).padding(bottom = 10.dp, top = 3.dp))
        }
        if (comment.canModify && !comment.isDeleted) Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled && !busy, modifier = Modifier.size(48.dp)) {
                Icon(FeedIcons.more, "Comment options", tint = FeedColors.muted, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                containerColor = Color.White, shape = RoundedCornerShape(14.dp), tonalElevation = 0.dp) {
                DropdownMenuItem(text = { Text("Edit", fontSize = 14.sp) }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete", color = FeedColors.brand, fontSize = 14.sp) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}
