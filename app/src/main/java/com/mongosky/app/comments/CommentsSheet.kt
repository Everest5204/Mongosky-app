package com.mongosky.app.comments

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

/** Existing feed entry point, with web mobile presentation implemented in native Compose. */
@Composable
@Suppress("DEPRECATION") // Resize mode supports older Android versions; imePadding handles current insets.
internal fun CommentsSheet(state: CommentsState,userName: String,viewModel: CommentsController) {
    val configuration=LocalConfiguration.current
    val mobile=configuration.screenWidthDp<=640
    val owner=LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val list=rememberLazyListState()
    var visibleIds by remember(state.postKey) { mutableStateOf(emptySet<String>()) }
    var now by remember { mutableStateOf(Instant.now()) }
    val rows=remember(state.root,state.replies,state.expandedIds) { commentEntries(state.root,state.replies,state.expandedIds) }
    val enabled=!viewModel.sessionExpired
    DisposableEffect(viewModel,state.postKey,owner) {
        val observer=LifecycleEventObserver { _,_ ->
            resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            viewModel.setForeground(resumed)
        }
        owner.lifecycle.addObserver(observer); viewModel.setForeground(resumed)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.setForeground(false) }
    }
    LaunchedEffect(list,viewModel,state.postKey) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull {
            val key=it.key as? String
            key?.takeIf { value -> value.startsWith("comment:") }?.substringAfter("comment:")
        }.toSet() }.distinctUntilChanged().collect { visibleIds=it; viewModel.visibleComments(it) }
    }
    LaunchedEffect(viewModel,state.postKey,resumed,enabled) {
        if (resumed && enabled) while(isActive) {
            delay(CommentPolicy.UPDATE_INTERVAL_MILLIS)
            if (!list.isScrollInProgress) { viewModel.checkForUpdates(visibleIds); now=Instant.now() }
        }
    }
    Dialog(onDismissRequest=viewModel::backComments,properties=DialogProperties(
        usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnBackPress=false,dismissOnClickOutside=false)) {
        BackHandler(onBack=viewModel::backComments)
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Box(Modifier.fillMaxSize().background(AppColors.surface(if(mobile) Color.White else Color(0x8F0F172A)))
            .safeDrawingPadding().imePadding().padding(if(mobile) 0.dp else 18.dp),contentAlignment=Alignment.Center) {
            val panel=if(mobile) Modifier.fillMaxSize() else Modifier.widthIn(max=620.dp).fillMaxWidth()
                .heightIn(max=minOf(760.dp,configuration.screenHeightDp.dp*0.86f)).fillMaxHeight()
            Column(panel.clip(RoundedCornerShape(if(mobile) 0.dp else 24.dp)).background(AppColors.surface(Color.White))) {
                Row(Modifier.fillMaxWidth().heightIn(min=54.dp).padding(start=if(mobile) 10.dp else 18.dp,end=6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(appText("Comments"),color=AppColors.content(Color.Black),fontSize=if(configuration.screenWidthDp<=420) 20.sp else 21.sp,
                        fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                    IconButton(onClick=viewModel::closeComments,modifier=Modifier.size(48.dp)) {
                        Icon(CommentIcons.close,appDescription("Close comments"),Modifier.size(26.dp),tint=AppColors.content(Color.Black))
                    }
                }
                HorizontalDivider(color=AppColors.outline(CommentColors.line),thickness=1.dp)
                LazyColumn(state=list,modifier=Modifier.weight(1f).fillMaxWidth(),
                    contentPadding=PaddingValues(horizontal=if(mobile) 10.dp else 12.dp,vertical=12.dp),
                    verticalArrangement=Arrangement.spacedBy(13.dp)) {
                    state.error?.let { message -> item(key="section_error",contentType="error") {
                        CommentError(message,viewModel::retryComments,enabled && !viewModel.writing)
                    } }
                    if (state.root.comments.isEmpty() && !state.root.initialized && state.root.loading) item(key="initial_loading",contentType="state") {
                        Column(Modifier.fillMaxWidth().padding(top=62.dp),horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp),color=AppColors.content(CommentColors.placeholder),strokeWidth=2.dp)
                            Text(appText("Loading comments..."),fontSize=17.sp,color=AppColors.content(CommentColors.placeholder))
                        }
                    }
                    if (state.root.initialized && state.root.comments.isEmpty() && state.root.error==null)
                        item(key="empty",contentType="state") { CommentEmptyState() }
                    items(rows,key={ it.key },contentType={ if(it is CommentListEntry.Row) "comment" else "thread_footer" }) { entry ->
                        when(entry) {
                            is CommentListEntry.Row -> CommentRow(entry,state,viewModel,now)
                            is CommentListEntry.Footer -> CommentThreadFooter(entry.parentId,entry.thread,viewModel,enabled)
                        }
                    }
                    if (state.root.comments.isNotEmpty() || state.root.error!=null)
                        item(key="root_footer",contentType="thread_footer") { CommentThreadFooter(null,state.root,viewModel,enabled) }
                }
                HorizontalDivider(color=AppColors.outline(CommentColors.line),thickness=1.dp)
                CommentInput(state.draft,viewModel::setCommentDraft,"Write a comment...","Send comment as $userName",viewModel::sendComment,
                    enabled && !viewModel.writing,state.sending,canSubmit=!state.unconfirmedCreate,
                    modifier=Modifier.padding(horizontal=if(mobile) 8.dp else 12.dp,vertical=10.dp))
            }
        }
    }
}
