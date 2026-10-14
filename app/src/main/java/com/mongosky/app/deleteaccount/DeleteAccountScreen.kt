package com.mongosky.app.deleteaccount

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeleteAccountScreen(
    state: DeleteAccountState, onPassword: (String) -> Unit, onConfirm: () -> Unit,
    onBack: () -> Unit, hostBusy: Boolean = false
) {
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(false) }
    val enabled = !state.busy && !state.dialogOpen && !hostBusy
    val confirm: () -> Unit = {
        if (enabled) { visible = false; focus.clearFocus(); onConfirm() }
    }
    val back: () -> Unit = { if (!hostBusy) { visible = false; focus.clearFocus(); onBack() } }
    LaunchedEffect(state.dialogOpen, state.passwordError) {
        if (state.dialogOpen) visible = false
        if (state.passwordError != null) passwordFocus.requestFocus()
    }
    BackHandler(onBack = back)
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(DeletionColors.white)).imePadding()) {
        val horizontal = if (maxWidth >= 640.dp) 24.dp else 16.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 448.dp).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, enabled = !hostBusy, modifier = Modifier.size(48.dp)) {
                    Icon(DeleteAccountIcons.get("back"), "Go back", tint = AppColors.content(DeletionColors.slate900), modifier = Modifier.size(22.dp))
                }
                Text("Delete Account", modifier = Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold, color = AppColors.content(DeletionColors.slate900))
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.widthIn(max = 448.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = horizontal, end = horizontal, top = 8.dp, bottom = 24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).background(AppColors.surface(DeletionColors.red50), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(DeleteAccountIcons.get("trash"), null, tint = AppColors.content(DeletionColors.red600), modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Delete Your Account", fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, textAlign = TextAlign.Center,
                        color = AppColors.content(DeletionColors.slate900), modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    Text("We're sorry to see you go. Please read the information below before deleting your account.",
                        fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 24.sp,
                        textAlign = TextAlign.Center, color = AppColors.content(DeletionColors.slate500))
                }
                Spacer(Modifier.height(20.dp))
                DeletionInfoCard("warning", "This action cannot be undone",
                    "Your account and associated data will be removed from our active systems. Deleted accounts have no restore option in Mongosky.", false)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.padding(horizontal = 8.dp)) {
                    Text("What will be deleted?", fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold, color = AppColors.content(DeletionColors.slate900), modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeleteAccountContent.dataItems.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(DeleteAccountIcons.get(item.icon), null, tint = AppColors.content(DeletionColors.slate500),
                                    modifier = Modifier.padding(top = 2.dp).size(18.dp))
                                Text(item.description, modifier = Modifier.weight(1f), fontFamily = FontFamily.SansSerif,
                                    fontSize = 12.sp, lineHeight = 20.sp, color = AppColors.content(DeletionColors.slate600))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                DeletionInfoCard("info", "Before you delete",
                    "Save anything you want to keep. Cleanup may take time. Copies in backups or saved by other people may remain after deletion from active systems.", true)
                Spacer(Modifier.height(20.dp))
                Text("To confirm, please enter your password", fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = AppColors.content(DeletionColors.slate900))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.password, onValueChange = onPassword, singleLine = true, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(passwordFocus).semantics {
                        contentDescription = "Current password"; contentType = ContentType.Password
                    }, shape = RoundedCornerShape(12.dp), isError = state.passwordError != null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif,
                        fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.content(DeletionColors.slate900)),
                    placeholder = { Text("Enter your password", fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp, color = AppColors.content(DeletionColors.slate400)) },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    leadingIcon = { Icon(DeleteAccountIcons.get("lock"), null, modifier = Modifier.size(18.dp), tint = AppColors.content(DeletionColors.slate500)) },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                            Icon(DeleteAccountIcons.get(if (visible) "eyeOff" else "eye"),
                                if (visible) "Hide password" else "Show password", modifier = Modifier.size(19.dp), tint = AppColors.content(DeletionColors.slate500))
                        }
                    }, colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.outline(DeletionColors.red400), unfocusedBorderColor = AppColors.outline(DeletionColors.slate200),
                        errorBorderColor = AppColors.outline(DeletionColors.red600), disabledBorderColor = AppColors.outline(DeletionColors.slate200),
                        focusedContainerColor = AppColors.surface(DeletionColors.white), unfocusedContainerColor = AppColors.surface(DeletionColors.white),
                        disabledContainerColor = AppColors.surface(DeletionColors.slate50), cursorColor = AppColors.content(DeletionColors.red600)))
                state.passwordError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, lineHeight = 20.sp, color = AppColors.content(DeletionColors.red600),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                }
                if (!state.dialogOpen && state.error != null) {
                    Spacer(Modifier.height(12.dp)); DeletionFeedback(state.error)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = confirm, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(DeletionColors.red500), contentColor = AppColors.content(DeletionColors.white),
                        disabledContainerColor = AppColors.fill(DeletionColors.red500.copy(alpha = 0.6f)), disabledContentColor = AppColors.content(DeletionColors.white))) {
                    if (state.phase == DeletionPhase.SUBMITTING) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.content(DeletionColors.white), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.phase == DeletionPhase.SUBMITTING) "Submitting…" else "Delete My Account",
                        fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = back, enabled = !hostBusy, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.content(DeletionColors.slate500))) {
                    Text(if (state.phase == DeletionPhase.SUBMITTING) "Close" else "Cancel", fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DeletionInfoCard(icon: String, title: String, body: String, information: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = AppColors.surface(if (information) DeletionColors.blue50 else DeletionColors.red50),
        border = if (information) BorderStroke(1.dp, AppColors.outline(DeletionColors.blue100)) else null,
        tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(DeleteAccountIcons.get(icon), null, tint = Color.Unspecified,
                modifier = Modifier.padding(top = 2.dp).size(if (information) 24.dp else 25.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold, color = AppColors.content(if (information) DeletionColors.blue600 else DeletionColors.red600),
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(body, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 20.sp, color = AppColors.content(DeletionColors.slate600))
            }
        }
    }
}

@Composable
internal fun DeletionFeedback(message: String) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        shape = RoundedCornerShape(12.dp), color = AppColors.surface(DeletionColors.red50), border = BorderStroke(1.dp, AppColors.outline(DeletionColors.red100))) {
        Text(message, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp, lineHeight = 24.sp, color = AppColors.content(DeletionColors.red800))
    }
}
