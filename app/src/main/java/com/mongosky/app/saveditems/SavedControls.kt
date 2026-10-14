@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.saveditems

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal val LocalSavedItems = staticCompositionLocalOf<SavedItemsViewModel?> { null }
internal val SavedGreen = Color(0xFF07927C)
internal val SavedBackground = Color(0xFFF4F7F6)
internal val SavedText = Color(0xFF172B28)
internal val SavedMuted = Color(0xFF647570)

@Composable private fun savedControl(postId: String,tag: String,revalidate: Boolean = true): Pair<SavedItemsViewModel?,SavedCell> {
    val model = LocalSavedItems.current
    val generation = model?.generation
    DisposableEffect(model,postId,generation,revalidate) {
        model?.observe("$tag:$postId",postId,revalidate)
        onDispose { model?.stopObserving("$tag:$postId") }
    }
    LaunchedEffect(model,postId,model?.ready) { if (model?.ready == true) model.ensureStatus(postId,false) }
    return model to (model?.cell(postId) ?: SavedCell())
}
private fun label(model: SavedItemsViewModel?,cell: SavedCell): String = when {
    model?.sessionExpired == true -> "Sign in to save posts"
    model?.ready == false && model.state.error != null -> "Retry saved status"
    cell.saving -> if (cell.status?.saved == true) "Removing from saved…" else "Saving post…"
    cell.loading || cell.status == null && cell.error == null -> "Checking saved status…"
    cell.error != null -> "Retry saved status"
    cell.status?.saved == true -> "Remove from saved"
    else -> "Save post"
}

private fun canUse(model: SavedItemsViewModel?,cell: SavedCell): Boolean = model != null && !model.sessionExpired &&
    (!model.ready && model.state.error != null || model.ready && !cell.loading && !cell.saving && (cell.status != null || cell.error != null))
private fun waiting(model: SavedItemsViewModel?,cell: SavedCell) = cell.loading || cell.saving || model?.ready != true && model?.state?.error == null && model?.sessionExpired != true

/** Prefetch only composed post cards; the menu reuses this account-bound cache. */
@Composable internal fun WarmSavedPostStatus(postId: String) {
    savedControl(postId,"post-preview",revalidate = false)
}

internal fun savedPostMenuLabel(cell: SavedCell) = if (cell.status?.saved == true) "Unsave post" else "Save post"
private fun canOpenSavedMenu(model: SavedItemsViewModel?,cell: SavedCell) = model != null && !model.sessionExpired && !cell.saving &&
    (model.ready || model.state.error != null)

/** One save action shared by Home and both profile menus. Background I/O has no menu loading UI. */
@Composable internal fun SavedMenuAction(postId: String,onDismiss: () -> Unit,sheet: SheetState? = null) {
    val (model,cell) = savedControl(postId,"post-menu",revalidate = false)
    val epoch = model?.generation ?: -1
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val enabled = !closing && canOpenSavedMenu(model,cell)
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = enabled,role = Role.Button,onClick = {
        closing = true
        if (model?.ready == false) model.retrySession()
        else model?.setFromMenu(postId,cell.status?.saved != true,epoch)
        scope.launch { sheet?.hide();onDismiss() }
    }).padding(horizontal = 20.dp,vertical = 12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(if (cell.status?.saved == true) SavedIcons.saved else SavedIcons.bookmark,null,
            tint = AppColors.content(if (cell.status?.saved == true) SavedGreen else SavedText),modifier = Modifier.size(23.dp))
        Text(savedPostMenuLabel(cell),fontSize = 16.sp,color = AppColors.content(if (enabled) SavedText else SavedMuted))
    }
}

/** Default styled post cards, including the My Activity and Saved post detail. */
@Composable internal fun SavedDropdownAction(postId: String,onDismiss: () -> Unit) {
    val (model,cell) = savedControl(postId,"post-dropdown",revalidate = false)
    val epoch = model?.generation ?: -1
    DropdownMenuItem(text = { Text(savedPostMenuLabel(cell)) },leadingIcon = {
        Icon(if (cell.status?.saved == true) SavedIcons.saved else SavedIcons.bookmark,null,tint = AppColors.content(SavedGreen))
    },enabled = canOpenSavedMenu(model,cell),onClick = {
        if (model?.ready == false) model.retrySession()
        else model?.setFromMenu(postId,cell.status?.saved != true,epoch)
        onDismiss()
    })
}

@Composable internal fun SavedReelButton(postId: String) {
    val (model,cell) = savedControl(postId,"reel")
    val epoch = model?.generation ?: -1
    IconButton(onClick = { if (model?.ready == false) model.retrySession() else if (cell.error != null) model?.ensureStatus(postId,true) else model?.toggle(postId,epoch) },
        enabled = canUse(model,cell),
        modifier = Modifier.size(48.dp)) {
        if (waiting(model,cell) || model?.ready == true && cell.status == null && cell.error == null) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth = 2.dp,color = AppColors.content(Color.White))
        else Icon(if (cell.status?.saved == true) SavedIcons.saved else SavedIcons.bookmark,label(model,cell),
            tint = AppColors.content(if (cell.status?.saved == true) Color(0xFF6CE5B7) else Color.White),modifier = Modifier.size(25.dp))
    }
}

@Composable internal fun SavedNoticeHost(model: SavedItemsViewModel,modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(model,model.generation) {
        host.currentSnackbarData?.dismiss()
        model.notices.collect { value ->
            if (value.generation == model.generation) {
                val result = host.showSnackbar(value.message,actionLabel = if (value.undo != null) "Undo" else null,withDismissAction = value.undo != null)
                if (result == SnackbarResult.ActionPerformed && value.generation == model.generation) value.undo?.let(model::undo)
            }
        }
    }
    SnackbarHost(host,modifier)
}
