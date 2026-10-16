package com.mongosky.app.signup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.AppLanguage
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

internal data class SignupChoice(val value: String, val label: String)

@Composable
internal fun SignupBirthdayFields(value: SignupBirthday, enabled: Boolean, onValueChange: (SignupBirthday) -> Unit) {
    var field by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val months = stringArrayResource(R.array.signup_months)
    val monthLabel = stringResource(R.string.signup_month)
    val dayLabel = stringResource(R.string.signup_day)
    val yearLabel = stringResource(R.string.signup_year)
    val locale = AppLanguage.current
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(Locale.forLanguageTag(locale.tag)).apply { isGroupingUsed = false } }
    val today = LocalDate.now()
    val years = remember(today.year, locale) { SignupBirthday.years(today).map { SignupChoice(it.toString(), numbers.format(it)) } }
    val options = when (field) {
        "month" -> months.mapIndexed { index, label -> SignupChoice((index + 1).toString(), label) }
        "day" -> (1..value.dayCount).map { SignupChoice(it.toString(), numbers.format(it)) }
        "year" -> years
        else -> emptyList()
    }
    val selected = when (field) { "month" -> value.month; "day" -> value.day; else -> value.year }?.toString().orEmpty()
    val title = when (field) { "month" -> monthLabel; "day" -> dayLabel; else -> yearLabel }

    Text(stringResource(R.string.signup_birthdate), color = AppColors.content(SignupInk),
        fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val stackAll = fontScale > 1.6f || maxWidth < 250.dp
        val stackMonth = stackAll || maxWidth < 300.dp || fontScale > 1.15f
        @Composable fun Month(modifier: Modifier = Modifier) {
            SignupChoiceButton(value.month?.let { months.getOrNull(it - 1) } ?: monthLabel,
                monthLabel, enabled, value.month != null, { focus.clearFocus(); field = "month" }, modifier, tag = "signup-birth-month")
        }
        @Composable fun Day(modifier: Modifier = Modifier) {
            SignupChoiceButton(value.day?.let { numbers.format(it) } ?: dayLabel,
                dayLabel, enabled, value.day != null, { focus.clearFocus(); field = "day" }, modifier, tag = "signup-birth-day")
        }
        @Composable fun Year(modifier: Modifier = Modifier) {
            SignupChoiceButton(value.year?.let { numbers.format(it) } ?: yearLabel,
                yearLabel, enabled, value.year != null, { focus.clearFocus(); field = "year" }, modifier, tag = "signup-birth-year")
        }
        if (stackMonth) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Month()
            if (stackAll) { Day(); Year() }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Day(Modifier.weight(1f)); Year(Modifier.weight(1f)) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Month(Modifier.weight(1.5f)); Day(Modifier.weight(.9f)); Year(Modifier.weight(1.15f))
        }
    }
    if (field != null) SignupChoiceDialog(title, options, selected,
        onDismiss = { field = null }, onSelect = { key ->
            val number = key.toInt()
            onValueChange(when (field) { "month" -> value.withMonth(number); "day" -> value.withDay(number); else -> value.withYear(number) })
            field = null
        })
}

/** Even the year selector composes only the visible rows. */
@Composable
internal fun SignupChoiceDialog(
    title: String, options: List<SignupChoice>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit
) {
    val initialIndex = (options.indexOfFirst { it.value == selected } - 3).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("signup-choice-dialog"),
            shape = RoundedCornerShape(20.dp), color = AppColors.surface(Color.White), tonalElevation = 0.dp) {
            Column {
                Text(title, Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 14.dp).semantics { heading() },
                    color = AppColors.content(SignupInk), fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp).selectableGroup(), state = list) {
                    items(options, key = { it.value }) { item ->
                        val active = item.value == selected
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("signup-choice-${item.value}")
                            .selectable(active, onClick = { onSelect(item.value) }, role = Role.RadioButton)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RadioButton(active, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AppColors.content(SignupBlue)))
                            Text(item.label, color = AppColors.content(SignupInk), fontSize = 16.sp, lineHeight = 24.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onDismiss) { Text(stringResource(R.string.signup_cancel), color = AppColors.content(SignupBlue)) }
                }
            }
        }
    }
}
