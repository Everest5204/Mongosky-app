package com.mongosky.app.deactivateaccount

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeactivateAccountScreen(
    state: DeactivateAccountState, host: DeactivateAccountHost,
    onPassword: (String) -> Unit, onVisibility: () -> Unit,
    onContinue: () -> Unit, onBack: () -> Unit
) {
    val colors = DeactivateAccountColors
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val blocked = state.submitting || host.busy || state.exitRequested
    BackHandler { if (!blocked) onBack() }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !blocked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.Ink)
                }
                Text("Deactivate account", color = colors.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = colors.Border)
            if (state.exitRequested) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PauseMark()
                    Text(if (state.deactivated) "Account deactivated" else "Sign in again", fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, color = colors.Ink, textAlign = TextAlign.Center)
                    Text(if (state.deactivated) "Your account is paused. Sign in with your account details whenever you want to return."
                        else "Sign in again to check your account and continue.", color = colors.Muted, textAlign = TextAlign.Center)
                    if (host.busy) CircularProgressIndicator(Modifier.size(24.dp), color = colors.Red, strokeWidth = 2.dp)
                    if (host.error != null && !host.busy) {
                        Text("Could not finish signing out. Please try again.", color = colors.Red)
                        Button(onClick = host.onLeaveAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.Red)) { Text("Try signing out again") }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item(key = "deactivate:welcome") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PauseMark()
                            Text("Take a break from Mongosky", color = colors.Ink, fontSize = 25.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("Temporarily pause your account. You can return whenever you are ready.",
                                color = colors.Muted, textAlign = TextAlign.Center, lineHeight = 22.sp)
                        }
                    }
                    item(key = "deactivate:details") {
                        Surface(color = colors.Surface, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                InformationRow("Your profile will be hidden", "Your profile and posts will disappear from search, feeds and profile pages.")
                                InformationRow("Your data is kept", "Your posts, photos and connections are kept. Deactivation does not delete your account.")
                                InformationRow("Sign in to reactivate", "A successful sign-in with your password or linked Google account restores your account.")
                            }
                        }
                    }
                    item(key = "deactivate:history") {
                        Text("Previous comments and messages may remain visible to other people.", color = colors.Muted,
                            fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    item(key = "deactivate:password") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Confirm it is you", fontWeight = FontWeight.SemiBold, color = colors.Ink, fontSize = 18.sp)
                            Text("Enter your current password to continue.", color = colors.Muted, fontSize = 14.sp)
                            OutlinedTextField(value = state.password, onValueChange = onPassword,
                                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).semantics { contentType = ContentType.Password },
                                label = { Text("Current password") }, singleLine = true,
                                enabled = !blocked && !state.uncertain, isError = state.passwordError != null,
                                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { TextButton(onClick = onVisibility, enabled = !blocked) { Text(if (state.passwordVisible) "Hide" else "Show") } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (!blocked) { focus.clearFocus(); onContinue() } }),
                                supportingText = state.passwordError?.let { { Text(it) } }, shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.Red,
                                    focusedLabelColor = colors.Ink, cursorColor = colors.Red))
                            if (state.error != null && !state.dialogOpen) Text(state.error, color = colors.Red, fontSize = 14.sp)
                        }
                    }
                    item(key = "deactivate:actions") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                if (DeactivateAccountPolicy.passwordError(state.password) != null) passwordFocus.requestFocus()
                                else focus.clearFocus()
                                onContinue()
                            }, enabled = !blocked, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.Red)) {
                                Text("Continue", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = onBack, enabled = !blocked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("Keep my account active", color = colors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InformationRow(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = DeactivateAccountColors.Ink, fontWeight = FontWeight.SemiBold)
        Text(description, color = DeactivateAccountColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun PauseMark() {
    Row(Modifier.size(60.dp).background(DeactivateAccountColors.Warning, CircleShape),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { Box(Modifier.width(5.dp).height(23.dp).background(DeactivateAccountColors.Red, RoundedCornerShape(2.dp))) }
    }
}
