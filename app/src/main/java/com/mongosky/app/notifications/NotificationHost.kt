package com.mongosky.app.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mongosky.app.profile.ProfileOpenRequest

@Composable
internal fun NotificationHost(viewModel:NotificationsViewModel, owner:String?, signingOut:Boolean,
    onOpenNotifications:()->Unit,onOpenProfile:(ProfileOpenRequest)->Unit, navigationEnabled:Boolean=true) {
    val context=LocalContext.current; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val openNotifications by rememberUpdatedState(onOpenNotifications); val openProfile by rememberUpdatedState(onOpenProfile)
    val tap by NotificationSystem.tap.collectAsState()
    val state by viewModel.controller.state.collectAsState()
    LaunchedEffect(viewModel,owner,signingOut) {
        if(signingOut || owner==null) viewModel.endSession() else viewModel.startSession(owner)
    }
    DisposableEffect(lifecycle,viewModel,signingOut) {
        NotificationSystem.channel(context)
        fun foreground() {
            viewModel.controller.setForeground(!signingOut && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            if(!signingOut && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) NotificationRegistration.refresh(context)
        }
        val observer=LifecycleEventObserver { _,_ -> foreground() }
        lifecycle.addObserver(observer); foreground()
        onDispose { lifecycle.removeObserver(observer); viewModel.controller.setForeground(false) }
    }
    LaunchedEffect(tap,owner,signingOut,state.ready,navigationEnabled) {
        val selected=tap ?: return@LaunchedEffect
        if(!signingOut && state.ready && navigationEnabled && selected.ownerId==owner) {
            openNotifications(); viewModel.controller.markRead(selected.id)
            selected.actorId?.let { openProfile(ProfileOpenRequest(it)) }
            NotificationSystem.consumeTap()
        }
    }
}

@Composable
internal fun NotificationBell(count:Long,enabled:Boolean,onClick:()->Unit) {
    Box {
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)) {
            Icon(NotificationIcons.bell,"Notifications${if(count>0) ", $count unread" else ""}",Modifier.size(23.dp),tint=NotificationColors.text)
        }
        if(count>0) Box(Modifier.align(Alignment.TopEnd).padding(top=4.dp,end=2.dp).defaultMinSize(minWidth=17.dp,minHeight=17.dp)
            .background(Color(0xFFFF3040),CircleShape).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
            Text(NotificationPolicy.badge(count),color=Color.White,fontSize=10.sp,lineHeight=15.sp,fontWeight=FontWeight.Bold)
        }
    }
}
