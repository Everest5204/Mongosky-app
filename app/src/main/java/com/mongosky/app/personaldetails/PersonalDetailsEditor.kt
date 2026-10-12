@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.personaldetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun PersonalDetailsEditor(state: PersonalDetailsState,onDraft: (DetailsDraft) -> Unit,onSave: () -> Unit,
    onRefresh: () -> Unit,dirty: Boolean,enabled: Boolean) {
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    val editable = enabled && !state.uncertain
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight().imePadding(),contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item("details:editor-description") {
            Text(when (state.editor) { DetailsEditor.NAME -> "Choose the name people know you by."; DetailsEditor.BIRTHDAY -> "Choose your date of birth."; else -> "Choose the option that describes you." },fontSize = 16.sp,lineHeight = 24.sp,color = DetailsColors.text)
        }
        if (state.editor == DetailsEditor.NAME) {
            item("details:editor-first-name") { OutlinedTextField(state.draft.firstName,{ if (it.length <= 120) onDraft(state.draft.copy(firstName = it)) },
                modifier = Modifier.fillMaxWidth(),enabled = editable,singleLine = true,label = { Text("First name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,imeAction = ImeAction.Next),shape = RoundedCornerShape(12.dp)) }
            item("details:editor-last-name") { OutlinedTextField(state.draft.lastName,{ if (it.length <= 120) onDraft(state.draft.copy(lastName = it)) },
                modifier = Modifier.fillMaxWidth(),enabled = editable,singleLine = true,label = { Text("Last name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,imeAction = ImeAction.Done),shape = RoundedCornerShape(12.dp)) }
            item("details:editor-name-policy") { Text("You can change your name once every 30 days. First and last name can contain up to 30 letters in total.",fontSize = 13.sp,lineHeight = 21.sp,color = DetailsColors.muted) }
        } else if (state.editor == DetailsEditor.BIRTHDAY) {
            item("details:editor-birthday") {
                Surface(Modifier.fillMaxWidth().clickable(enabled = editable) { dateOpen = true },shape = RoundedCornerShape(12.dp),color = Color.White,border = BorderStroke(1.dp,DetailsColors.line)) {
                    Row(Modifier.padding(18.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(DetailsIcons.calendar,null,tint = DetailsColors.muted)
                        Column(Modifier.weight(1f)) { Text("Birthday",fontSize = 13.sp,color = DetailsColors.muted);Spacer(Modifier.height(6.dp));Text(displayBirthday(state.draft.birthDate),fontSize = 17.sp,color = DetailsColors.text) }
                        Text("Choose",color = DetailsColors.brand,fontSize = 14.sp)
                    }
                }
                if (state.draft.birthDate.isNotEmpty()) TextButton({ onDraft(state.draft.copy(birthDate = "")) },enabled = editable) { Text("Remove birthday",color = DetailsColors.muted) }
            }
            item("details:editor-birthday-privacy") { Text("Your birthday is only visible to you.",fontSize = 13.sp,color = DetailsColors.muted) }
        } else {
            item("details:editor-gender") {
                Surface(shape = RoundedCornerShape(14.dp),color = Color.White,border = BorderStroke(1.dp,DetailsColors.line)) {
                    Column(Modifier.selectableGroup()) { listOf("female","male","other","").forEach { gender ->
                        Row(Modifier.fillMaxWidth().selectable(selected = state.draft.gender == gender,enabled = editable,role = Role.RadioButton,
                            onClick = { onDraft(state.draft.copy(gender = gender)) }).heightIn(min = 60.dp).padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.draft.gender == gender,onClick = null,enabled = editable,colors = RadioButtonDefaults.colors(selectedColor = DetailsColors.brand))
                            Spacer(Modifier.width(14.dp));Text(DetailsPolicy.genderLabel(gender),fontSize = 16.sp,color = DetailsColors.text)
                        }
                    } }
                }
            }
            item("details:editor-gender-privacy") { Text("Your gender is only visible to you.",fontSize = 13.sp,color = DetailsColors.muted) }
        }
        (state.draftError ?: state.error)?.let { message -> item("details:editor-error") { Text(message,color = DetailsColors.brand,fontSize = 14.sp,lineHeight = 21.sp) } }
        item("details:editor-save") {
            Button(if (state.uncertain) onRefresh else onSave,enabled = if (state.uncertain) enabled else editable && dirty,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DetailsColors.brand,contentColor = Color.White)) {
                if (state.saving || state.loading) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth = 2.dp,color = Color.White);Spacer(Modifier.width(10.dp)) }
                Text(if (state.saving) "Saving…" else if (state.uncertain) "Check saved details" else "Save changes",fontWeight = FontWeight.Bold)
            }
        }
    }
    if (dateOpen) BirthdayPicker(state.draft.birthDate,onDismiss = { dateOpen = false },onSelect = { onDraft(state.draft.copy(birthDate = it));dateOpen = false })
}

@Composable
private fun BirthdayPicker(value: String,onDismiss: () -> Unit,onSelect: (String) -> Unit) {
    val today = remember { LocalDate.now(ZoneOffset.UTC) }
    val initial = remember(value) { runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
    val selectable = remember(today) { object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
        override fun isSelectableYear(year: Int) = year in 1900..today.year
    } }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial,yearRange = 1900..today.year,selectableDates = selectable)
    DatePickerDialog(onDismissRequest = onDismiss,confirmButton = {
        TextButton({ picker.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } },enabled = picker.selectedDateMillis != null) { Text("Done",color = DetailsColors.brand) }
    },dismissButton = { TextButton(onDismiss) { Text("Cancel",color = DetailsColors.muted) } }) { DatePicker(picker) }
}
