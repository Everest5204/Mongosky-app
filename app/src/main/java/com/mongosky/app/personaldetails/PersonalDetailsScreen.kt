@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.personaldetails

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.shared.ProfileAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PersonalDetailsScreen(state: PersonalDetailsState, host: PersonalDetailsHost, onBack: () -> Unit,
    onRefresh: () -> Unit, onEdit: (DetailsEditor) -> Unit, onDraft: (DetailsDraft) -> Unit, onSave: () -> Unit,
    onDiscard: () -> Unit, onKeepEditing: () -> Unit, dirty: Boolean) {
    BackHandler(onBack = onBack)
    val busy = host.busy || state.saving || state.loading
    val enabled = !busy && !state.expired && !state.sessionChanged
    Box(Modifier.fillMaxSize().background(AppColors.surface(Color.White)), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, enabled = !host.busy && !state.saving) { Icon(DetailsIcons.back,appDescription("Back"),tint = AppColors.content(DetailsColors.text)) }
                Text(appText(when (state.editor) { DetailsEditor.NAME -> "Name"; DetailsEditor.BIRTHDAY -> "Birthday"; DetailsEditor.GENDER -> "Gender"; null -> "Personal Details" }),
                    modifier = Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = AppColors.content(DetailsColors.text))
                if (state.editor == null) IconButton(onRefresh, enabled = enabled) { Icon(DetailsIcons.refresh,appDescription("Refresh details"),tint = AppColors.content(DetailsColors.muted)) }
            }
            HorizontalDivider(color = AppColors.outline(DetailsColors.line))
            if (state.loading && state.details != null) LinearProgressIndicator(Modifier.fillMaxWidth(),color = AppColors.content(DetailsColors.brand),trackColor = AppColors.surface(DetailsColors.soft))
            if (state.editor != null && state.details != null) {
                PersonalDetailsEditor(state, onDraft, onSave, onRefresh, dirty, enabled)
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item("details:introduction") {
                        Text(appText("Your account information"),fontSize = 24.sp,fontWeight = FontWeight.Bold,color = AppColors.content(DetailsColors.text))
                        Spacer(Modifier.height(8.dp))
                        Text(appText("Manage the personal details connected to your Mongosky account."),fontSize = 15.sp,lineHeight = 23.sp,color = AppColors.content(DetailsColors.muted))
                    }
                    val d = state.details
                    if (d != null) {
                        item("details:identity") {
                            Row(Modifier.fillMaxWidth().background(AppColors.surface(DetailsColors.soft),RoundedCornerShape(16.dp)).padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ProfileAvatar(d.imageUrl,Modifier.size(56.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.displayName,fontWeight = FontWeight.Bold,fontSize = 18.sp,color = AppColors.content(DetailsColors.text))
                                    Spacer(Modifier.height(4.dp));Text(appText("Mongosky account"),fontSize = 13.sp,color = AppColors.content(DetailsColors.muted))
                                }
                            }
                        }
                        item("details:fields") {
                            Surface(color = AppColors.surface(Color.White),shape = RoundedCornerShape(16.dp),border = BorderStroke(1.dp,AppColors.outline(DetailsColors.line))) {
                                Column {
                                    DetailRow(DetailsIcons.person,"Name",d.displayName,
                                        if (d.nameCanChange) "Your name on Mongosky" else "Next change: ${nextNameDate(d.nameNextChangeAt)}",enabled && d.nameCanChange) { onEdit(DetailsEditor.NAME) }
                                    DetailDivider()
                                    DetailRow(DetailsIcons.contact,if (d.contact.contains('@')) "Email address" else "Sign-in contact",d.contact,
                                        if (d.contact.contains('@') && d.emailVerified) "Verified · Used to sign in" else "Used to sign in",false) {}
                                    DetailDivider()
                                    DetailRow(DetailsIcons.calendar,"Birthday",displayBirthday(d.birthDate),"Only visible to you",enabled) { onEdit(DetailsEditor.BIRTHDAY) }
                                    DetailDivider()
                                    DetailRow(DetailsIcons.gender,"Gender",appText(DetailsPolicy.genderLabel(d.gender)),"Only visible to you",enabled) { onEdit(DetailsEditor.GENDER) }
                                }
                            }
                        }
                        item("details:contact-note") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(DetailsIcons.lock,null,Modifier.size(18.dp),tint = AppColors.content(DetailsColors.muted))
                                Text(appText("Changing your sign-in email or phone requires a separate verification process."),fontSize = 13.sp,lineHeight = 20.sp,color = AppColors.content(DetailsColors.muted))
                            }
                        }
                    } else if (state.loading) item("details:loading") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 28.dp),horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(26.dp),color = AppColors.content(DetailsColors.brand),strokeWidth = 2.dp) }
                    }
                    (host.error ?: state.error)?.let { error -> item("details:error") {
                        Text(appText(error),color = AppColors.content(DetailsColors.brand),fontSize = 14.sp,lineHeight = 21.sp)
                        if (state.expired) TextButton(host.onSignIn,enabled = !host.busy) { Text(appText("Sign in again"),color = AppColors.content(DetailsColors.brand)) }
                        else if (!state.sessionChanged) TextButton(onRefresh,enabled = !busy) { Text(appText("Try again"),color = AppColors.content(DetailsColors.brand)) }
                    } }
                    state.notice?.let { notice -> item("details:notice") { Text(appText(notice),color = AppColors.content(DetailsColors.success),fontSize = 14.sp) } }
                }
            }
        }
    }
    if (state.discardOpen) AlertDialog(onDismissRequest = onKeepEditing,containerColor = AppColors.surface(Color.White),
        title = { Text(appText("Discard changes?")) },text = { Text(appText("Your unsaved edits will be cleared.")) },
        confirmButton = { TextButton(onDiscard) { Text(appText("Discard"),color = AppColors.content(DetailsColors.brand)) } },
        dismissButton = { TextButton(onKeepEditing) { Text(appText("Keep editing"),color = AppColors.content(DetailsColors.text)) } })
}

@Composable
private fun DetailRow(icon: ImageVector,title: String,value: String,description: String,editable: Boolean,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = editable,onClick = onClick).heightIn(min = 92.dp).padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,Modifier.size(22.dp),tint = AppColors.content(DetailsColors.muted))
        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(appText(title),fontSize = 13.sp,color = AppColors.content(DetailsColors.muted))
            Text(value.ifEmpty { appText("Not set") },fontSize = 16.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(DetailsColors.text))
            Text(appText(description),fontSize = 12.sp,lineHeight = 18.sp,color = AppColors.content(DetailsColors.muted))
        }
        if (editable) Icon(DetailsIcons.chevron,appDescription("Edit ${appText(title)}"),Modifier.size(20.dp),tint = AppColors.content(DetailsColors.muted))
    }
}
@Composable private fun DetailDivider() { HorizontalDivider(Modifier.padding(start = 52.dp,end = 16.dp),color = AppColors.outline(DetailsColors.line)) }
internal fun displayBirthday(value: String): String = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag(com.mongosky.app.language.AppLanguage.current.tag))) }.getOrElse { appText("Not set") }
private fun nextNameDate(value: String?): String = runCatching { java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag(com.mongosky.app.language.AppLanguage.current.tag))) }.getOrElse { appText("after 30 days") }
