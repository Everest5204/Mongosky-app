package com.mongosky.app.comments

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CommentEmptyState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().heightIn(min = 270.dp).padding(horizontal = 22.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp).border(2.dp,AppColors.outline(Color(0xFFD7D7D7)),RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(CommentIcons.message,null,Modifier.size(42.dp),tint=AppColors.content(CommentColors.placeholder))
        }
        Spacer(Modifier.height(18.dp))
        Text(appText("No comments yet"),fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Black,color=AppColors.content(Color(0xFF5F6368)),textAlign=TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(appText("Be the first to share your thoughts."),fontSize=15.sp,lineHeight=22.sp,color=AppColors.content(Color(0xFF9AA0A6)),textAlign=TextAlign.Center)
    }
}

@Composable
internal fun CommentError(message: String, onRetry: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppColors.surface(CommentColors.error),RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=8.dp)) {
        Text(appText(message),color=AppColors.content(CommentColors.active),fontSize=13.sp,lineHeight=18.sp)
        TextButton(onClick=onRetry,enabled=enabled,contentPadding=PaddingValues(horizontal=0.dp)) {
            Text(appText("Try again"),color=AppColors.content(CommentColors.active),fontSize=13.sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
internal fun CommentThreadFooter(parentId: String?, thread: CommentThread, controls: CommentsController, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start=if(parentId==null) 0.dp else 56.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if (thread.loading) Row(Modifier.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp),color=AppColors.content(CommentColors.muted),strokeWidth=2.dp)
            Text(appText("Loading..."),fontSize=13.sp,color=AppColors.content(CommentColors.muted))
        }
        thread.error?.let { CommentError(it,{ controls.retryThread(parentId) },enabled) }
        if (thread.initialized && thread.hasMore && !thread.loading && thread.error==null) {
            TextButton(onClick={ if(parentId==null) controls.loadComments() else controls.loadReplies(parentId) },enabled=enabled,
                colors=ButtonDefaults.textButtonColors(contentColor=AppColors.content(Color(0xFF334155)),containerColor=AppColors.surface(Color(0xFFF1F5F9))),
                contentPadding=PaddingValues(horizontal=14.dp,vertical=8.dp)) {
                Text(appText(if(parentId==null) "Load more comments" else "Load more replies"),fontSize=13.sp,fontWeight=FontWeight.ExtraBold)
            }
        }
    }
}
