package com.mongosky.app.notifications

import com.mongosky.app.language.appText
import com.mongosky.app.language.AppLanguage

import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object NotificationColors {
    val text = Color(0xFF111827); val secondary = Color(0xFF334155); val muted = Color(0xFF64748B)
    val line = Color(0xFFEEF2F6); val unread = Color(0xFFF5F8FF); val blue = Color(0xFF2563EB)
}
private val notificationDateFormatter = DateTimeFormatter.ofPattern("MMM d, uuuu, h:mm a", Locale.ENGLISH)

@Composable
internal fun NotificationRow(item: NotificationItem, verified: Boolean, compact: Boolean, onSelect: () -> Unit) {
    val surface = if (item.isRead) Color.White else NotificationColors.unread
    val locale = AppLanguage.current
    val actorName = item.actor?.displayName?.takeIf(String::isNotBlank) ?: appText("Someone")
    val action = appText(item.actionText)
    val time = remember(item.createdAt, locale) {
        notificationDateFormatter.withLocale(Locale.forLanguageTag(locale.tag))
            .withZone(ZoneId.systemDefault()).format(item.createdAt)
    }
    Column(Modifier.fillMaxWidth().background(AppColors.surface(surface)).clickable(role=Role.Button,onClick=onSelect)
        .semantics(mergeDescendants=true) { contentDescription = "${if (item.isRead) "" else appText("Unread. ")}$actorName $action. $time." }) {
        Row(Modifier.fillMaxWidth().heightIn(min=84.dp).padding(horizontal=if(compact)12.dp else 16.dp,vertical=if(compact)12.dp else 14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact)10.dp else 12.dp)) {
            NotificationAvatar(item, surface, if(compact)44 else 48)
            Column(Modifier.weight(1f)) {
                val message = remember(actorName,action,verified, com.mongosky.app.darkmode.AppAppearance.mode) { buildAnnotatedString {
                    withStyle(SpanStyle(color=AppColors.content(NotificationColors.text),fontWeight=FontWeight.SemiBold)) { append("\u2068${actorName}\u2069") }
                    if (verified) { append("\u00a0"); appendInlineContent("verified") }
                    append(" $action.")
                } }
                val inline = if(verified) mapOf("verified" to InlineTextContent(Placeholder(1.em,1.em,PlaceholderVerticalAlign.TextCenter)) {
                    Icon(NotificationIcons.verified,appDescription("Verified account"),Modifier.fillMaxSize(),tint=Color.Unspecified)
                }) else emptyMap()
                Text(message,inlineContent=inline,color=AppColors.content(NotificationColors.secondary),fontSize=15.sp,lineHeight=22.sp)
                Spacer(Modifier.height(4.dp))
                Text(time,color=AppColors.content(if(item.isRead)NotificationColors.muted else Color(0xFF1D4ED8)),fontSize=12.sp,lineHeight=18.sp)
            }
            if(!item.isRead) Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.surface(NotificationColors.blue)))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.outline(NotificationColors.line)))
    }
}

@Composable
private fun NotificationAvatar(item: NotificationItem, surface: Color, size: Int) {
    val context = LocalContext.current
    val request = remember(context,item.actor?.profileImageUrl) { item.actor?.profileImageUrl?.let {
        ImageRequest.Builder(context.applicationContext).data(it).crossfade(false).build()
    } }
    val follow = item.kind == NotificationKind.FOLLOW || item.kind == NotificationKind.FOLLOW_BACK
    val comment = item.kind != NotificationKind.REACTION && !follow
    val accent = when(item.kind) { NotificationKind.FOLLOW_BACK -> Color(0xFF0F766E); NotificationKind.REPLY -> Color(0xFF7C3AED); else -> NotificationColors.blue }
    Box(Modifier.size(size.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(AppColors.surface(Color(0xFFF1F5F9))),contentAlignment=Alignment.Center) {
            Icon(NotificationIcons.user,null,Modifier.size(25.dp),tint=AppColors.content(NotificationColors.muted))
            if(request!=null) AsyncImage(request,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        }
        Box(Modifier.align(Alignment.BottomEnd).offset(2.dp,2.dp).size(if(follow||comment)22.dp else 24.dp)
            .clip(CircleShape).background(AppColors.surface(if(follow||comment)accent else Color.White)).border(2.dp,AppColors.outline(surface),CircleShape),contentAlignment=Alignment.Center) {
            val emoji = if(item.kind==NotificationKind.REACTION) NotificationPolicy.reactionEmoji(item.reaction) else null
            if(emoji!=null) Text(emoji,fontSize=16.sp,lineHeight=18.sp) else Icon(when(item.kind) {
                NotificationKind.FOLLOW -> NotificationIcons.follow; NotificationKind.FOLLOW_BACK -> NotificationIcons.followBack
                NotificationKind.COMMENT -> NotificationIcons.comment; NotificationKind.REPLY -> NotificationIcons.reply
                NotificationKind.LOVE -> NotificationIcons.heart; else -> NotificationIcons.bell
            },null,Modifier.size(14.dp),tint=AppColors.content(if(item.kind==NotificationKind.LOVE)Color(0xFFE11D48) else if(follow||comment)Color.White else NotificationColors.muted))
        }
    }
}
