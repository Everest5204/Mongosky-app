package com.mongosky.app.comments

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.profile.profileLink
import com.mongosky.app.shared.ProfileAvatar
import java.time.Instant

/** Depth affects connectors, never the remaining text width after the first reply level. */
@Composable
internal fun CommentRow(entry: CommentListEntry.Row, state: CommentsState, controls: CommentsController, now: Instant) {
    val comment = entry.comment
    val mobile = LocalConfiguration.current.screenWidthDp <= 640
    val enabled = !controls.sessionExpired
    val busy = comment.id in state.busyIds
    val inlineEdit = state.editing?.id == comment.id
    val inlineReply = state.replying?.id == comment.id
    val expanded = comment.id in state.expandedIds
    val avatar = if (entry.depth == 0) { if (mobile) 35.dp else 38.dp } else { if (mobile) 29.dp else 31.dp }
    val connector = if (entry.depth == 0) Modifier else Modifier.drawBehind {
        val x = 44.dp.toPx(); val width = 2.dp.toPx()
        drawLine(CommentColors.threadLine,Offset(x,0f),Offset(x,size.height),width)
        if (entry.depth == 1) drawLine(CommentColors.threadLine,Offset(x,23.dp.toPx()),Offset(54.dp.toPx(),23.dp.toPx()),width)
    }
    Row(Modifier.fillMaxWidth().then(connector).padding(start=if(entry.depth==0) 0.dp else 56.dp),
        horizontalArrangement=Arrangement.spacedBy(if(entry.depth==0) 10.dp else 8.dp),verticalAlignment=Alignment.Top) {
        ProfileAvatar(comment.author.profileImageUrl,Modifier.size(avatar)
            .profileLink(comment.author.id,comment.author,beforeOpen=controls::closeComments))
        Column(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().background(AppColors.surface(CommentColors.bubble),RoundedCornerShape(if(entry.depth==0) 17.dp else 15.dp))
                .padding(horizontal=11.dp,vertical=9.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CommentName(comment,comment.userId in state.verifiedAuthors,controls::closeComments,Modifier.weight(1f))
                    if (comment.canModify && !comment.isDeleted) CommentMenu(comment,state,controls,enabled && !busy && !controls.writing)
                }
                Spacer(Modifier.height(4.dp))
                if (inlineEdit) {
                    CommentInput(state.inlineDraft,controls::setInlineDraft,"Edit your comment...","Save comment",controls::sendInline,
                        enabled && !controls.writing,sending=state.sendingInline,onCancel=controls::cancelEdit,autoFocus=true,compact=true)
                } else SelectionContainer {
                    Text(comment.text,color=AppColors.content(CommentColors.body),fontSize=14.sp,lineHeight=20.sp)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(start=5.dp,top=5.dp),verticalArrangement=Arrangement.spacedBy(1.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(appText(CommentPolicy.timeAgo(comment.createdAt,now)),color=AppColors.content(CommentColors.time),fontSize=12.sp,
                    modifier=Modifier.padding(vertical=6.dp))
                CommentMetaAction(if(comment.isLovedByMe) CommentIcons.loved else CommentIcons.heart,
                    if(comment.lovesCount>0) comment.lovesCount.toString() else "Love",
                    if(comment.isLovedByMe) "Remove love from comment" else "Love this comment",
                    enabled && !busy,comment.isLovedByMe) { controls.loveComment(comment) }
                CommentMetaAction(CommentIcons.message,"Reply","Reply to comment",enabled && !busy && !controls.writing,inlineReply) {
                    controls.startReply(comment)
                }
            }
            if (inlineReply) {
                Spacer(Modifier.height(10.dp))
                CommentInput(state.inlineDraft,controls::setInlineDraft,"Reply to ${comment.author.displayName.ifBlank { appText("Mongosky User") }}...",
                    "Send reply",controls::sendInline,enabled && !controls.writing,sending=state.sendingInline,
                    canSubmit=!state.unconfirmedCreate,onCancel=controls::cancelEdit,autoFocus=true)
            }
            if ((inlineEdit || inlineReply) && state.inlineError!=null) {
                CommentError(state.inlineError,controls::retryComments,enabled && !controls.writing,Modifier.padding(top=6.dp))
            }
            if (comment.repliesCount>0 || expanded) {
                Text(appText(if(expanded) "Hide replies" else "View ${comment.repliesCount} ${if(comment.repliesCount==1L) "reply" else "replies"}"),
                    color=AppColors.content(Color(0xFF475569)),fontSize=12.sp,fontWeight=FontWeight.ExtraBold,
                    modifier=Modifier.padding(top=7.dp).clickable(enabled=enabled,role=Role.Button,
                        onClickLabel=appText(if(expanded) "Hide replies" else "View replies")) { controls.toggleReplies(comment) }
                        .padding(horizontal=8.dp,vertical=7.dp))
            }
        }
    }
}

@Composable
private fun CommentMetaAction(icon: ImageVector,label: String,description: String,enabled: Boolean,active: Boolean,onClick: () -> Unit) {
    Row(Modifier.clickable(enabled=enabled,role=Role.Button,onClickLabel=appText(description),onClick=onClick)
        .semantics { stateDescription=appText(if(active) "Active" else "Inactive") }.padding(horizontal=6.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        val color=if(active) CommentColors.active else CommentColors.muted
        Icon(icon,null,Modifier.size(15.dp),tint=AppColors.content(color))
        Text(appText(label),color=AppColors.content(color),fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
}
