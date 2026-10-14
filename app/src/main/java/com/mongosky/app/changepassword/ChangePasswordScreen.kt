package com.mongosky.app.changepassword

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ChangePasswordScreen(
    state: ChangePasswordState,
    onUpdate: (PasswordField, String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    hostBusy: Boolean = false
) {
    val focus = LocalFocusManager.current
    val enabled = !state.busy && !hostBusy
    val nextFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.submitting, state.successRevision) {
        if (state.submitting || state.successRevision > 0) {
            currentVisible = false; newVisible = false; confirmVisible = false
        }
    }
    val goBack: () -> Unit = { if (enabled) { focus.clearFocus(); onBack() } }
    val submit: () -> Unit = {
        if (enabled) {
            focus.clearFocus()
            currentVisible = false; newVisible = false; confirmVisible = false
            onSubmit()
        }
    }
    // Consume Back while submitting too; a pending request must never finish the Activity.
    BackHandler(onBack = goBack)
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(PasswordColors.white)).imePadding()) {
        val wide = maxWidth >= 640.dp
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 576.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = if (wide) 32.dp else 20.dp, end = if (wide) 32.dp else 20.dp,
                    top = if (wide) 28.dp else 20.dp, bottom = 48.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = goBack, enabled = enabled, modifier = Modifier.size(48.dp)) {
                        Icon(ChangePasswordIcons.back, "Return to previous page", tint = AppColors.content(PasswordColors.ink),
                            modifier = Modifier.size(23.dp))
                    }
                    Text("Change password", color = AppColors.content(PasswordColors.ink), fontFamily = FontFamily.SansSerif,
                        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
                }
                Spacer(Modifier.height(20.dp))
                Text("You can use this password to log in to your account. Your new password must be at least 6 characters.",
                    fontFamily = FontFamily.SansSerif, color = AppColors.content(PasswordColors.help),
                    fontSize = if (wide) 16.sp else 14.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.widthIn(max = 384.dp).fillMaxWidth()) {
                    PasswordInput(state.values.current, { onUpdate(PasswordField.CURRENT, it) }, "Current password",
                        ContentType.Password, enabled, currentVisible, { currentVisible = !currentVisible },
                        ImeAction.Next, KeyboardActions(onNext = { nextFocus.requestFocus() }))
                    Spacer(Modifier.height(12.dp))
                    PasswordInput(state.values.new, { onUpdate(PasswordField.NEW, it) }, "New password",
                        ContentType.NewPassword, enabled, newVisible, { newVisible = !newVisible },
                        ImeAction.Next, KeyboardActions(onNext = { confirmFocus.requestFocus() }), Modifier.focusRequester(nextFocus))
                    Spacer(Modifier.height(12.dp))
                    PasswordInput(state.values.confirmation, { onUpdate(PasswordField.CONFIRM, it) }, "Re-type new password",
                        ContentType.NewPassword, enabled, confirmVisible, { confirmVisible = !confirmVisible },
                        ImeAction.Done, KeyboardActions(onDone = { submit() }), Modifier.focusRequester(confirmFocus))
                    state.error?.let { PasswordFeedback(it, error = true) }
                    state.success?.let { PasswordFeedback(it, error = false) }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { if (enabled) { focus.clearFocus(); onForgotPassword() } }, enabled = enabled,
                        contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.content(PasswordColors.link))) {
                        Text("Forgot your password?", fontSize = 14.sp, lineHeight = 20.sp,
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = submit, enabled = enabled, shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(PasswordColors.green), contentColor = AppColors.content(PasswordColors.white),
                            disabledContainerColor = AppColors.fill(PasswordColors.green.copy(alpha = 0.6f)), disabledContentColor = AppColors.content(PasswordColors.white))) {
                        if (state.submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.content(PasswordColors.white), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.submitting) "Changing password..." else "Change password", fontSize = 16.sp,
                            lineHeight = 24.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordInput(
    value: String, onChange: (String) -> Unit, label: String, autofillType: ContentType,
    enabled: Boolean, visible: Boolean, onToggle: () -> Unit,
    imeAction: ImeAction, actions: KeyboardActions, modifier: Modifier = Modifier
) {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 62.dp).semantics {
            contentDescription = label; contentType = autofillType
        }, shape = RoundedCornerShape(16.dp),
        placeholder = { Text(label, fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.content(PasswordColors.muted)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp,
            lineHeight = 24.sp, color = AppColors.content(PasswordColors.ink)),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password, imeAction = imeAction), keyboardActions = actions,
        trailingIcon = {
            IconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(if (visible) ChangePasswordIcons.eyeOff else ChangePasswordIcons.eye,
                    (if (visible) "Hide " else "Show ") + label.lowercase(), modifier = Modifier.size(20.dp),
                    tint = AppColors.content(PasswordColors.muted.copy(alpha = if (enabled) 1f else 0.5f)))
            }
        }, colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.content(PasswordColors.ink), unfocusedTextColor = AppColors.content(PasswordColors.ink),
            disabledTextColor = AppColors.content(PasswordColors.ink), focusedContainerColor = AppColors.surface(PasswordColors.white),
            unfocusedContainerColor = AppColors.surface(PasswordColors.white), disabledContainerColor = AppColors.surface(PasswordColors.disabled),
            focusedBorderColor = AppColors.outline(PasswordColors.green), unfocusedBorderColor = AppColors.outline(PasswordColors.border),
            disabledBorderColor = AppColors.outline(PasswordColors.border), cursorColor = AppColors.content(PasswordColors.green)))
}

@Composable
private fun PasswordFeedback(message: String, error: Boolean) {
    Spacer(Modifier.height(16.dp))
    Text(message, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.SansSerif,
        color = AppColors.content(if (error) PasswordColors.error else PasswordColors.success),
        modifier = Modifier.fillMaxWidth().background(AppColors.surface(if (error) PasswordColors.errorBackground else PasswordColors.successBackground),
            RoundedCornerShape(12.dp)).padding(12.dp).semantics {
                liveRegion = if (error) LiveRegionMode.Assertive else LiveRegionMode.Polite
            })
}
