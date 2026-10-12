package com.mongosky.app.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun DeleteAccountDialog(
    state: DeleteAccountState, onDiscard: () -> Unit, onDelete: () -> Unit, onFinish: () -> Unit,
    hostBusy: Boolean = false, hostError: String? = null
) {
    val busy = state.phase == DeletionPhase.SUBMITTING || state.phase == DeletionPhase.PROCESSING
    val deleted = state.phase == DeletionPhase.DELETED
    val singleAction = deleted || state.sessionExpired || state.sessionChanged
    val title = when {
        state.sessionChanged -> "Sign-in changed"
        state.sessionExpired -> "Session expired"
        deleted -> "Account deleted"
        state.phase == DeletionPhase.PROCESSING -> "Deleting your account"
        state.phase == DeletionPhase.SUBMITTING -> "Submitting your request"
        else -> "Delete your account?"
    }
    Dialog(onDismissRequest = { if (!hostBusy) onDiscard() },
        properties = DialogProperties(dismissOnBackPress = !hostBusy, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0.45f)
            onDispose { }
        }
        val actionFocus = remember { FocusRequester() }
        LaunchedEffect(singleAction) { actionFocus.requestFocus() }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 384.dp).fillMaxWidth().heightIn(max = maxHeight).semantics { paneTitle = title },
                shape = RoundedCornerShape(24.dp), color = DeletionColors.white, tonalElevation = 0.dp, shadowElevation = 24.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val iconColor = if (deleted) DeletionColors.emerald600 else if (busy) DeletionColors.slate500 else DeletionColors.red600
                    Box(Modifier.size(64.dp).background(
                        if (deleted) DeletionColors.emerald50 else if (busy) DeletionColors.slate50 else DeletionColors.red50,
                        CircleShape), contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(32.dp), color = iconColor, strokeWidth = 1.8.dp)
                        else Icon(DeleteAccountIcons.get(if (deleted) "check" else "trash"), null, tint = iconColor,
                            modifier = Modifier.size(if (deleted) 34.dp else 30.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif, fontSize = 20.sp,
                            lineHeight = 28.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.SemiBold,
                            color = DeletionColors.slate900, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        when {
                            state.sessionChanged -> DialogParagraph("The new session has not been changed.", false)
                            state.sessionExpired -> DialogParagraph("Please sign in again to continue.", false)
                            deleted -> DialogParagraph("Your account has been permanently deleted.", true)
                            state.phase == DeletionPhase.PROCESSING -> {
                                DialogParagraph("Your request has been accepted. We are removing your account and its associated data.", false)
                                Spacer(Modifier.height(12.dp))
                                DialogParagraph("This message will update when cleanup finishes. Closing it will not cancel an accepted deletion request.", false)
                            }
                            state.phase == DeletionPhase.SUBMITTING ->
                                DialogParagraph("Checking your password and submitting your deletion request. Please wait.", false)
                            else -> {
                                DialogParagraph("Are you sure you want to permanently delete your Mongosky account?", true)
                                Spacer(Modifier.height(12.dp))
                                DialogParagraph("Your password will be verified before deletion starts. Your account and its associated data will be removed from our active systems.", false)
                                Spacer(Modifier.height(12.dp))
                                DialogParagraph("Deleted accounts have no restore option in Mongosky. Deletion may take time to complete.", false)
                            }
                        }
                    }
                    val message = hostError ?: state.error.takeUnless { deleted }
                    if (message != null) { Spacer(Modifier.height(16.dp)); DeletionFeedback(message) }
                    Spacer(Modifier.height(24.dp))
                    if (singleAction) {
                        Button(onClick = { if (state.sessionChanged) onDiscard() else onFinish() }, enabled = !hostBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(actionFocus),
                            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (deleted) DeletionColors.emerald600 else DeletionColors.slate700,
                                contentColor = DeletionColors.white)) {
                            if (hostBusy) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = DeletionColors.white, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.sessionChanged) "Close" else if (state.sessionExpired) "Sign in again" else "Continue",
                                fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onDiscard, enabled = !hostBusy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(actionFocus),
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeletionColors.slate100, contentColor = DeletionColors.slate700)) {
                                Text(if (busy) "Close" else "Discard", fontFamily = FontFamily.SansSerif, fontSize = 14.sp,
                                    lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(onClick = onDelete, enabled = !state.busy && !hostBusy,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeletionColors.red600, contentColor = DeletionColors.white,
                                    disabledContainerColor = DeletionColors.red600.copy(alpha = 0.6f), disabledContentColor = DeletionColors.white)) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = DeletionColors.white, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (state.phase == DeletionPhase.PROCESSING) "Deleting…"
                                    else if (state.phase == DeletionPhase.SUBMITTING) "Submitting…" else "Delete",
                                    fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogParagraph(text: String, prominent: Boolean) {
    Text(text, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif,
        fontSize = if (prominent) 15.sp else 14.sp, lineHeight = 24.sp,
        color = if (prominent) DeletionColors.slate700 else DeletionColors.slate600)
}

