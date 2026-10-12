package com.mongosky.app.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AiChatDrawer(state:AiState,colors:AiColors,dark:Boolean,onClose:()->Unit,onNew:()->Unit,
    onTheme:()->Unit,onOpen:(String)->Unit,onDelete:(String)->Unit,onClear:()->Unit,onReload:()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Mongosky",color=colors.text,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            IconButton(onClose,Modifier.size(48.dp)) { Icon(AiIcons.Close,"Close chat menu",tint=colors.muted,modifier=Modifier.size(22.dp)) }
        }
        AiDrawerAction("New Chat",AiIcons.Plus,colors,!state.mutating && !state.expired,onNew)
        AiDrawerAction(if(dark) "Light Mode" else "Dark Mode",if(dark) AiIcons.Sun else AiIcons.Moon,colors,true,onTheme)
        HorizontalDivider(Modifier.padding(top=18.dp,bottom=14.dp),color=colors.border)
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("RECENT CHATS",color=colors.muted,fontSize=13.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            TextButton(onClick=onClear,enabled=state.ready && state.chats.isNotEmpty() && !state.sending && !state.mutating) {
                if(state.clearing) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=colors.red)
                else Text("Clear all",color=if(!state.sending && !state.mutating) colors.red else colors.muted,fontSize=14.sp)
            }
        }
        if(state.chatsLoading && state.chats.isEmpty()) Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=colors.muted)
            Text("Loading chats…",color=colors.muted,fontSize=14.sp,modifier=Modifier.padding(start=12.dp))
        } else if(state.chats.isEmpty()) Text("No recent chats",color=colors.muted,fontSize=14.sp,modifier=Modifier.padding(12.dp))
        if(state.error?.action==AiErrorAction.CHATS) TextButton(onReload,enabled=!state.sending && !state.mutating) { Text("Reload chats",color=colors.red) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            items(state.chats,key={it.id}) { chat ->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).background(
                    if(chat.id==state.activeChatId) colors.soft else colors.surface,RoundedCornerShape(12.dp)),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min=48.dp).clickable(enabled=!state.mutating) { onOpen(chat.id) }.padding(horizontal=12.dp,vertical=14.dp)) {
                        Text(chat.title,color=colors.text,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick={onDelete(chat.id)},enabled=state.ready && !state.sending && !state.mutating,modifier=Modifier.size(48.dp)) {
                        if(state.deletingId==chat.id) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=colors.muted)
                        else Icon(AiIcons.Close,"Delete chat: ${chat.title}",tint=colors.muted,modifier=Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiDrawerAction(title:String,icon:ImageVector,colors:AiColors,enabled:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable(enabled=enabled,onClick=onClick).padding(horizontal=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,title,tint=colors.text,modifier=Modifier.size(22.dp))
        Text(title,color=colors.text,fontSize=16.sp)
    }
}
