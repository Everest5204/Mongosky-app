@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.reportpost

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A single native modal per screen. Fixed stages never reset the feed's scroll. */
@Composable
internal fun PostReportSheet(controller: PostReportController) {
    val state = controller.state ?: return
    key(state.target.key) {
        val latest by rememberUpdatedState(state)
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden || !latest.busy })
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        val close: () -> Unit = {
            if (!latest.busy && !closing) {
                closing = true
                scope.launch {
                    try { sheet.hide(); controller.dismiss() }
                    finally { closing = false }
                }
            }
        }
        LaunchedEffect(state.retryAtMillis) {
            val remaining = controller.remainingCooldownMillis()
            if (remaining > 0) { delay(remaining); controller.rateLimitFinished() }
        }
        ModalBottomSheet(onDismissRequest = { controller.dismiss() }, sheetState = sheet,
            sheetGesturesEnabled = !state.busy && !closing, sheetMaxWidth = 640.dp,
            containerColor = AppColors.surface(Color.White), tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).fillMaxHeight(0.86f).imePadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (state.stage == ReportStage.REVIEW && !state.locked && !state.unavailable) {
                        IconButton(controller::back, enabled = !state.busy && !closing) { Icon(ReportIcons.back,appDescription("Back to reasons"),tint = AppColors.content(ReportColors.text)) }
                    } else Spacer(Modifier.width(12.dp))
                    Text(appText(if (state.stage == ReportStage.RECEIVED) "Report" else "Report post"),
                        modifier = Modifier.weight(1f).semantics { heading() }, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = AppColors.content(ReportColors.text))
                    IconButton(close, enabled = !state.busy && !closing) { Icon(ReportIcons.close,appDescription("Close report"),tint = AppColors.content(ReportColors.muted)) }
                }
                HorizontalDivider(color = AppColors.outline(ReportColors.line))
                AnimatedContent(state.stage, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "Report step") { stage ->
                    when (stage) {
                        ReportStage.REASONS -> ReportReasons(state, controller, !closing)
                        ReportStage.REVIEW -> ReportReview(state, controller, close, !closing)
                        ReportStage.RECEIVED -> ReportReceived(state, close, !closing)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportReasons(state: PostReportState, controller: PostReportController, enabled: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item("report:reason-introduction") {
            Column(Modifier.padding(20.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(appText("Why are you reporting this post?"),fontSize = 22.sp,lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,color = AppColors.content(ReportColors.text))
                Text(appText("Choose the reason that best describes your concern."),fontSize = 14.sp,lineHeight = 21.sp,color = AppColors.content(ReportColors.muted))
            }
        }
        items(ReportReason.entries,key = { "report:reason:${it.code}" }) { reason ->
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled && !state.busy,role = Role.Button,
                onClick = { controller.choose(reason) }).heightIn(min = 76.dp).padding(horizontal = 20.dp,vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(appText(reason.title),fontSize = 16.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(ReportColors.text))
                    Text(appText(reason.description),fontSize = 13.sp,lineHeight = 18.sp,color = AppColors.content(ReportColors.muted))
                }
                Icon(ReportIcons.chevron,null,Modifier.size(20.dp),tint = AppColors.content(ReportColors.muted))
            }
        }
        item("report:reason-privacy") { PrivacyNote(Modifier.padding(20.dp)) }
    }
}

@Composable
private fun ReportReview(state: PostReportState, controller: PostReportController, close: () -> Unit, enabled: Boolean) {
    val editable = enabled && !state.busy && !state.locked && !state.unavailable
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("report:review-introduction") {
            Text(appText("Review your report"),fontSize = 23.sp,fontWeight = FontWeight.Bold,color = AppColors.content(ReportColors.text))
            Spacer(Modifier.height(8.dp))
            Text(appText("About a post by ${state.target.authorName}"),fontSize = 14.sp,lineHeight = 21.sp,color = AppColors.content(ReportColors.muted))
        }
        item("report:review-reason") {
            Surface(Modifier.fillMaxWidth(),color = AppColors.surface(ReportColors.soft),shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(ReportIcons.flag,null,Modifier.size(22.dp),tint = AppColors.content(ReportColors.brand))
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(appText("Reason"),fontSize = 12.sp,color = AppColors.content(ReportColors.muted))
                        Text(appText(state.reason?.title.orEmpty()),fontSize = 16.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(ReportColors.text))
                    }
                    if (editable) TextButton(controller::back) { Text(appText("Change"),color = AppColors.content(ReportColors.brand)) }
                }
            }
        }
        item("report:review-details") {
            OutlinedTextField(state.details,controller::updateDetails,modifier = Modifier.fillMaxWidth(),enabled = editable,
                label = { Text(appText("Additional details (optional)")) },placeholder = { Text(appText("Tell us a little more about your concern")) },
                minLines = 3,maxLines = 6,shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = { Text("${ReportPolicy.count(state.details)} / ${ReportPolicy.MAX_DETAILS}") })
        }
        item("report:review-privacy") { PrivacyNote() }
        state.error?.let { error -> item("report:review-error") {
            Text(appText(error),color = AppColors.content(ReportColors.brand),fontSize = 14.sp,lineHeight = 21.sp)
        } }
        item("report:review-submit") {
            val cooldown = controller.remainingCooldownMillis() > 0
            Button(if (state.unavailable) close else if (state.uncertain) controller::checkReceipt else controller::submit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),enabled = enabled && !state.busy && !cooldown,
                shape = RoundedCornerShape(12.dp),colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(ReportColors.brand),contentColor = AppColors.content(Color.White))) {
                if (state.busy) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth = 2.dp,color = AppColors.content(Color.White));Spacer(Modifier.width(10.dp)) }
                Text(appText(when { state.checking -> "Checking…";state.busy -> "Sending…";cooldown -> "Try again later";
                    state.unavailable -> "Close";state.uncertain -> "Check report status";state.attempt != null -> "Retry report";else -> "Submit report" }),fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportReceived(state: PostReportState, close: () -> Unit, enabled: Boolean) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding = PaddingValues(horizontal = 24.dp,vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),horizontalAlignment = Alignment.CenterHorizontally) {
        item("report:received-icon") {
            Box(Modifier.size(72.dp).background(AppColors.surface(Color(0xFFEAF7F0)),CircleShape),contentAlignment = Alignment.Center) {
                Icon(ReportIcons.check,appDescription("Report received"),Modifier.size(36.dp),tint = AppColors.content(ReportColors.success))
            }
        }
        item("report:received-description") {
            Text(appText(if (state.receipt?.alreadyReported == true) "Report already received" else "Report received"),
                fontSize = 25.sp,fontWeight = FontWeight.Bold,color = AppColors.content(ReportColors.text))
            Spacer(Modifier.height(12.dp))
            Text(appText("Your report has been saved for review. Thank you for helping keep Mongosky safe."),
                fontSize = 16.sp,lineHeight = 25.sp,color = AppColors.content(ReportColors.muted))
        }
        item("report:received-reason") {
            Surface(Modifier.fillMaxWidth(),color = AppColors.surface(Color.White),shape = RoundedCornerShape(14.dp),border = BorderStroke(1.dp,AppColors.outline(ReportColors.line))) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(appText("Reported for"),fontSize = 13.sp,color = AppColors.content(ReportColors.muted))
                    Text(appText(state.receipt?.reason?.title.orEmpty()),fontSize = 16.sp,fontWeight = FontWeight.SemiBold,color = AppColors.content(ReportColors.text))
                }
            }
        }
        item("report:received-privacy") { PrivacyNote() }
        item("report:received-done") {
            Button(close,enabled = enabled,modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(ReportColors.brand),contentColor = AppColors.content(Color.White))) { Text(appText("Done"),fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(ReportIcons.shield,null,Modifier.size(18.dp),tint = AppColors.content(ReportColors.muted))
        Text(appText("Your name won't be shown to the person who posted this."),fontSize = 13.sp,lineHeight = 20.sp,color = AppColors.content(ReportColors.muted))
    }
}
