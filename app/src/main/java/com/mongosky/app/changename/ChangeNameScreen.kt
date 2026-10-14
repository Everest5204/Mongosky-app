package com.mongosky.app.changename

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Green = Color(0xFF07855D)
@Composable
internal fun ChangeNameScreen(state: ChangeNameState, onFirst: (String) -> Unit, onLast: (String) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit, onRefresh: () -> Unit, onSignInAgain: () -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(AppColors.surface(Color.White)).imePadding()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 68.dp), contentAlignment = Alignment.Center) {
            Text("Mongosky", color = AppColors.content(Color(0xFF080A09)), fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 24.dp, bottom = 30.dp)) {
                Text("Change Name", color = AppColors.content(Color(0xFF101412)), fontSize = 28.sp, lineHeight = 33.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(11.dp))
                Text("Profile name can be changed a limited number of times. After changing your name, you must wait 30 days to change it again.",
                    color = AppColors.content(Color(0xFF59635F)), fontSize = 15.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(28.dp))
                if (state.loading && state.status == null) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 180.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = AppColors.content(Green), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp)); Text("Loading your name...", color = AppColors.content(Color(0xFF66706B)), fontSize = 14.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(21.dp)) {
                        if (state.locked) Surface(color = AppColors.surface(Color(0xFFF4F8F6)), shape = RoundedCornerShape(13.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("You cannot change your name right now.", color = AppColors.content(Color(0xFF17241E)), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                state.status?.next?.let { next ->
                                    Text("You can change it again in ${remainingTime(next, state.now)}.", color = AppColors.content(Color(0xFF33423B)), fontSize = 14.sp)
                                    Text("Available after ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(next)}.", color = AppColors.content(Color(0xFF68736D)), fontSize = 13.sp)
                                }
                            }
                        }
                        if (state.status != null) {
                            NameField("First name", state.first, onFirst, state.locked || state.saving || state.expired, ImeAction.Next) {
                                focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            }
                            NameField("Last name", state.last, onLast, state.locked || state.saving || state.expired, ImeAction.Done) {
                                focus.clearFocus(); if (state.canSave) onSave()
                            }
                        }
                        if (!state.locked && state.changed) state.validation?.let { Notice(it, false) }
                        state.error?.let { Notice(it, false) }
                        if (state.success) Notice("Your profile name has been updated.", true)
                        if (state.expired) Button(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth()) { Text("Sign in again") }
                        else if (state.error != null) TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                            modifier = Modifier.fillMaxWidth()) { Text("Refresh status", color = AppColors.content(Green)) }
                        Button(onClick = { focus.clearFocus(); onSave() }, enabled = state.canSave,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(Green), contentColor = AppColors.content(Color.White),
                                disabledContainerColor = AppColors.fill(Color(0xFFD9DDDB)), disabledContentColor = AppColors.content(Color(0xFF929995)))) {
                            Text(if (state.saving) "Saving..." else "Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onBack, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Cancel", color = AppColors.content(Color(0xFF202522)), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun NameField(label: String, value: String, onValue: (String) -> Unit, readOnly: Boolean, ime: ImeAction, action: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = AppColors.content(Color(0xFF181D1B)), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value, { if (it.codePointCount(0, it.length) <= 30) onValue(it) },
            readOnly = readOnly, singleLine = true, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { contentDescription = label },
            textStyle = TextStyle(fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.outline(Green), unfocusedBorderColor = AppColors.outline(Color(0xFFD8DDDA)),
                focusedTextColor = AppColors.content(Color(0xFF111827)), unfocusedTextColor = AppColors.content(if (readOnly) Color(0xFF747D79) else Color(0xFF111827)),
                focusedContainerColor = AppColors.surface(Color.White), unfocusedContainerColor = AppColors.surface(if (readOnly) Color(0xFFF5F7F6) else Color.White)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ime),
            keyboardActions = KeyboardActions(onNext = { action() }, onDone = { action() }))
    }
}
@Composable
private fun Notice(text: String, success: Boolean) {
    Text(text, modifier = Modifier.fillMaxWidth().background(AppColors.surface(if (success) Color(0xFFEAF8F1) else Color(0xFFFFF1F1)), RoundedCornerShape(11.dp))
        .padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = AppColors.content(if (success) Color(0xFF086944) else Color(0xFFA52D2D)), fontSize = 14.sp)
}
