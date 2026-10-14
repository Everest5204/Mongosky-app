@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.helpsupport

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HelpSupportContactScreen(
    draft: SupportDraft, errors: SupportDraftErrors, openingEmail: Boolean,
    summaryFocus: FocusRequester, descriptionFocus: FocusRequester, focusErrorRequest: Int,
    onDraft: (SupportDraft) -> Unit, onOpenEmail: () -> Unit,
    onClearDraft: () -> Unit, onCopyEmail: () -> Unit, onBack: () -> Unit
) {
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(focusErrorRequest) {
        if (focusErrorRequest > 0) {
            val target = when { errors.summary != null -> 2; errors.description != null -> 3; else -> null }
            if (target != null) {
                list.animateScrollToItem(target)
                withFrameNanos { }
                if (target == 2) summaryFocus.requestFocus() else descriptionFocus.requestFocus()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.surface(HelpSupportColors.background)).imePadding()) {
        val side = if (maxWidth < 360.dp) 16.dp else 20.dp
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.TopCenter)) {
            HelpSupportHeader(if (draft.reason == SupportReason.FEEDBACK) "Send feedback" else "Contact support", onBack)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list,
                contentPadding = PaddingValues(start = side, end = side, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpSectionTitle("Tell us how we can help")
                        Text("Your message will open as an email draft. You can review it and add screenshots before sending.",
                            color = AppColors.content(HelpSupportColors.muted), fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
                item(key = "reason") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpSectionTitle("What is this about?")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SupportReason.entries, key = { it.name }) { reason ->
                                FilterChip(selected = draft.reason == reason, enabled = !openingEmail,
                                    onClick = { onDraft(draft.copy(reason = reason)) }, label = { Text(reason.label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.surface(HelpSupportColors.softBrand),
                                        selectedLabelColor = AppColors.content(HelpSupportColors.brand)), modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                }
                item(key = "summary") {
                    OutlinedTextField(value = draft.summary,
                        onValueChange = { onDraft(draft.copy(summary = HelpSupportPolicy.summary(it))) },
                        modifier = Modifier.fillMaxWidth().focusRequester(summaryFocus), enabled = !openingEmail,
                        label = { Text("Short summary") }, placeholder = { Text("What do you need help with?") },
                        singleLine = true, isError = errors.summary != null, shape = RoundedCornerShape(12.dp),
                        supportingText = { Text(errors.summary ?: "${draft.summary.length} / ${HelpSupportPolicy.MAX_SUMMARY}") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { descriptionFocus.requestFocus() }),
                        colors = supportFieldColors())
                }
                item(key = "description") {
                    OutlinedTextField(value = draft.description,
                        onValueChange = { onDraft(draft.copy(description = it.take(HelpSupportPolicy.MAX_DESCRIPTION))) },
                        modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus), enabled = !openingEmail,
                        label = { Text("Details") },
                        placeholder = { Text("Describe what happened, the screen you were using, and any error you saw.") },
                        minLines = 5, maxLines = 10, isError = errors.description != null, shape = RoundedCornerShape(12.dp),
                        supportingText = { Text(errors.description ?: "${draft.description.length} / ${HelpSupportPolicy.MAX_DESCRIPTION}") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = supportFieldColors())
                }
                item(key = "privacy") {
                    Text("Please don't include passwords or verification codes.", color = AppColors.content(HelpSupportColors.muted),
                        fontSize = 13.sp, lineHeight = 20.sp)
                }
                item(key = "app-info") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .toggleable(value = draft.includeAppInfo, enabled = !openingEmail, role = Role.Checkbox,
                            onValueChange = { onDraft(draft.copy(includeAppInfo = it)) }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = draft.includeAppInfo, onCheckedChange = null, enabled = !openingEmail,
                            colors = CheckboxDefaults.colors(checkedColor = HelpSupportColors.brand))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Include app and Android versions", color = AppColors.content(HelpSupportColors.text),
                                fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                            Text("Optional. These details can help us investigate.", color = AppColors.content(HelpSupportColors.muted),
                                fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
                item(key = "submit") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { focus.clearFocus(); onOpenEmail() }, enabled = !openingEmail,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(HelpSupportColors.brand))) {
                            Icon(HelpSupportIcons.mail, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Continue to email", fontWeight = FontWeight.SemiBold)
                        }
                        Text("Your draft stays here when you go back or return from your email app.",
                            color = AppColors.content(HelpSupportColors.muted), fontSize = 12.sp, lineHeight = 19.sp)
                        TextButton(onClick = onClearDraft, enabled = !openingEmail && (draft.summary.isNotBlank() || draft.description.isNotBlank())) {
                            Text("Clear draft", color = AppColors.content(HelpSupportColors.muted))
                        }
                    }
                }
                item(key = "contact-email") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(HelpSupportContent.CONTACT_EMAIL, Modifier.weight(1f), color = AppColors.content(HelpSupportColors.muted),
                            fontSize = 13.sp, lineHeight = 20.sp)
                        IconButton(onClick = onCopyEmail, modifier = Modifier.size(48.dp)) {
                            Icon(HelpSupportIcons.copy, "Copy support email", modifier = Modifier.size(20.dp), tint = AppColors.content(HelpSupportColors.muted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun supportFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.outline(HelpSupportColors.brand), focusedLabelColor = HelpSupportColors.brand,
    cursorColor = AppColors.content(HelpSupportColors.brand))
