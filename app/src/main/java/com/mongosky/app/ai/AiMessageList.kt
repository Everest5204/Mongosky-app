package com.mongosky.app.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun AiMessageList(state:AiState,colors:AiColors,followRequest:Int,modifier:Modifier=Modifier) {
    val list=rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(state.threadVersion) { mutableStateOf(true) }
    val density=LocalDensity.current
    val threshold=with(density) { 120.dp.toPx() }
    val keyboard=WindowInsets.ime.getBottom(density)
    fun nearBottom():Boolean {
        val layout=list.layoutInfo
        val last=layout.visibleItemsInfo.lastOrNull() ?: return true
        return last.index==layout.totalItemsCount-1 && last.offset+last.size-layout.viewportEndOffset<threshold
    }
    LaunchedEffect(list,state.threadVersion) {
        snapshotFlow { if(dragged) nearBottom() else null }.distinctUntilChanged().collect { near -> if(near!=null) follow=near }
    }
    LaunchedEffect(followRequest,state.threadVersion) { follow=true }
    LaunchedEffect(state.threadVersion,state.messages.size,state.messages.lastOrNull()?.content?.length,keyboard,followRequest) {
        if(!follow || state.messages.isEmpty() || dragged) return@LaunchedEffect
        withFrameNanos { }
        val index=state.messages.lastIndex
        if(list.layoutInfo.visibleItemsInfo.none { it.index==index }) list.scrollToItem(index)
        withFrameNanos { }
        list.layoutInfo.visibleItemsInfo.lastOrNull { it.index==index }?.let { last ->
            val distance=last.offset+last.size-list.layoutInfo.viewportEndOffset
            if(distance>0) list.scrollBy(distance.toFloat())
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val phone=maxWidth<600.dp
        when {
            state.messagesLoading && state.messages.isEmpty() -> CircularProgressIndicator(
                Modifier.size(24.dp).align(Alignment.Center),color=colors.muted,strokeWidth=2.dp)
            state.messages.isEmpty() -> Text("How can I help you?",fontSize=if(phone) 29.sp else 34.sp,
                lineHeight=40.sp,fontWeight=FontWeight.Light,color=colors.muted,
                modifier=Modifier.align(Alignment.Center).padding(20.dp))
            else -> LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(
                start=if(phone) 14.dp else 30.dp,end=if(phone) 14.dp else 30.dp,
                top=if(phone) 22.dp else 36.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(if(phone) 19.dp else 22.dp)) {
                items(state.messages,key={it.key},contentType={it.role}) { message ->
                    AiMessageRow(message,colors,phone)
                }
            }
        }
    }
}

@Composable
private fun AiMessageRow(message:AiMessage,colors:AiColors,phone:Boolean) {
    val user=message.role==AiRole.USER
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val maximum=maxWidth*(if(phone) .84f else .78f)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user) Arrangement.End else Arrangement.Start) {
        Column((if(user) Modifier.widthIn(max=maximum) else Modifier.fillMaxWidth()).then(if(user) Modifier.background(colors.user,
            RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomEnd=5.dp,bottomStart=18.dp)).padding(horizontal=16.dp,vertical=11.dp)
            else Modifier.padding(end=if(phone) 24.dp else 38.dp))) {
            if(!user && message.content.isEmpty() && message.delivery==AiDelivery.STREAMING) AiTyping(colors)
            else SelectionContainer { Text(message.content,color=colors.text,fontSize=16.sp,lineHeight=26.sp) }
            if(message.delivery==AiDelivery.INTERRUPTED) Text("Response interrupted",color=colors.muted,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))
        }
    }
    }
}

@Composable
private fun AiTyping(colors:AiColors) {
    val transition=rememberInfiniteTransition(label="AI typing")
    Row(Modifier.height(26.dp).semantics { contentDescription="Mongosky AI is responding" },
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(.3f,.95f,infiniteRepeatable(tween(550),RepeatMode.Reverse,
                initialStartOffset=StartOffset(index*180)),label="Typing dot $index")
            Box(Modifier.size(6.dp).background(colors.muted.copy(alpha=alpha),RoundedCornerShape(50)))
        }
    }
}
