package com.mongosky.app.notifications

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.profile.ProfileOpenRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onSignInAgain: () -> Unit, onOpenProfile: (ProfileOpenRequest) -> Unit) {
    val state by viewModel.controller.state.collectAsState(); val context = LocalContext.current
    val list = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(list,viewModel,lifecycle,context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                val layout = list.layoutInfo
                val keys = layout.visibleItemsInfo.filter {
                    it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset
                }.mapNotNull { it.key as? String }.toSet()
                val unread = latestState.items.filter {
                    it.id in keys && !it.isRead && it.id !in latestState.pendingReadIds && it.id !in latestState.failedReadIds
                }.map { it.id }
                keys to unread
            }.distinctUntilChanged().collectLatest { (keys, unread) ->
                viewModel.controller.visibleAuthors(viewModel.controller.state.value.items.filter { it.id in keys }.map { it.actorId })
                // Briefly visible rows during a fling or background transition stay unread.
                if (unread.isNotEmpty()) {
                    delay(250)
                    viewModel.controller.markVisibleRead(unread).forEach { NotificationSystem.dismiss(context,it) }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val compact = maxWidth<=360.dp
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max=680.dp).fillMaxWidth().heightIn(min=60.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) { Icon(NotificationIcons.back,"Go back",tint=NotificationColors.secondary) }
                Text("Notification",Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Bold,color=NotificationColors.text,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick=viewModel.controller::refresh,enabled=!state.loading && state.connection!=NotificationConnection.UNAUTHORIZED,modifier=Modifier.size(48.dp)) {
                    if(state.loading) CircularProgressIndicator(Modifier.size(20.dp),color=NotificationColors.muted,strokeWidth=1.8.dp)
                    else Icon(NotificationIcons.refresh,"Refresh notifications",Modifier.size(20.dp),tint=NotificationColors.secondary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(NotificationColors.line))
            if(state.connection==NotificationConnection.UNAUTHORIZED) NotificationState("Please sign in again","Your session has expired.","Sign in",onSignInAgain)
            else if(!state.loaded && state.error!=null) NotificationState("Couldn't load notifications","Please check your connection and try again.","Try again",viewModel::retry)
            else if(!state.loaded) Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp),color=NotificationColors.muted,strokeWidth=1.8.dp)
                    Text("Loading notifications…",fontSize=14.sp,color=NotificationColors.muted)
                }
            } else LazyColumn(Modifier.widthIn(max=680.dp).fillMaxWidth().weight(1f),state=list) {
                item("permission") { NotificationPermission() }
                if(state.connection==NotificationConnection.RECONNECTING || state.connection==NotificationConnection.ERROR) item("connection") {
                    NotificationNotice(if(state.connection==NotificationConnection.RECONNECTING) "Reconnecting to notifications…" else "Live updates are unavailable. Refresh to try again.")
                }
                if(state.error!=null) item("refresh-error") { NotificationNotice(state.error.orEmpty(),true) }
                if(state.readError!=null) item("read-error") { NotificationNotice(state.readError.orEmpty(),true,viewModel.controller::retryReads) }
                if(state.items.isEmpty()) item("empty") {
                    Column(Modifier.fillParentMaxHeight(0.75f).fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp,Alignment.CenterVertically)) {
                        Icon(NotificationIcons.bell,null,Modifier.size(64.dp),tint=Color(0xFF9CA3AF))
                        Text("No notification yet",fontSize=16.sp,color=Color(0xFF6B7280))
                    }
                }
                items(state.items,key={it.id},contentType={"notification"}) { item ->
                    NotificationRow(if(item.id in state.pendingReadIds) item.copy(isRead=true) else item,item.actorId in state.verifiedAuthors,compact) {
                        viewModel.controller.markRead(item.id); NotificationSystem.dismiss(context,item.id)
                        item.actor?.let { onOpenProfile(ProfileOpenRequest(it.id,it)) }
                    }
                }
                if(state.hasMore) item("more") {
                    Column(Modifier.fillMaxWidth().padding(16.dp,20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        if(state.moreError!=null) NotificationNotice(state.moreError.orEmpty(),true)
                        Button(onClick=viewModel.controller::loadMore,enabled=!state.loadingMore && !state.loading,
                            colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFF1F5F9),contentColor=NotificationColors.secondary),shape=RoundedCornerShape(10.dp)) {
                            Text(if(state.loadingMore)"Loading…" else if(state.moreError!=null)"Try again" else "Load more",fontSize=14.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ColumnScope.NotificationState(title:String,body:String,action:String,onAction:()->Unit) {
    Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)) {
        Text(title,color=NotificationColors.secondary,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text(body,color=NotificationColors.muted,fontSize=14.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick=onAction) { Text(action,color=NotificationColors.blue) }
    }
}
@Composable
private fun NotificationNotice(text:String,error:Boolean=false,onRetry:(()->Unit)?=null) {
    Column(Modifier.padding(16.dp,12.dp).fillMaxWidth().background(if(error)Color(0xFFFFF1F2) else Color(0xFFF1F5F9),RoundedCornerShape(8.dp)).padding(12.dp,10.dp)) {
        Text(text,fontSize=13.sp,lineHeight=20.sp,color=if(error)Color(0xFF9F1239) else NotificationColors.muted)
        if(onRetry!=null) TextButton(onClick=onRetry) { Text("Retry",color=NotificationColors.blue) }
    }
}
