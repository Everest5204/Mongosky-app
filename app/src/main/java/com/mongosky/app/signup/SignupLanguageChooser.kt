package com.mongosky.app.signup

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.AppLanguage
import com.mongosky.app.language.AppLocale

@Composable
internal fun SignupLanguageChooser(enabled: Boolean = true) {
    val current = AppLanguage.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val failure = stringResource(R.string.signup_language_failure)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        listOf(AppLocale.ENGLISH to "English", AppLocale.BANGLA to "বাংলা").forEach { (locale, label) ->
            TextButton(onClick = {
                focus.clearFocus()
                if (!AppLanguage.select(locale)) Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
            }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("signup-language-${locale.tag}")
                    .semantics { selected = current == locale; role = Role.Tab }) {
                Text(label, fontSize = 14.sp, lineHeight = 21.sp,
                    color = AppColors.content(if (current == locale) SignupBlue else SignupMuted),
                    fontWeight = if (current == locale) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
