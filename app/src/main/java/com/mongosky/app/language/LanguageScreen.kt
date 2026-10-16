@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.language

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.settings.SettingsIcons
import kotlinx.coroutines.launch

@Composable
internal fun LanguageScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme
    val selected = AppLanguage.current
    val notices = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun choose(locale: AppLocale) {
        if (!AppLanguage.select(locale)) scope.launch {
            notices.showSnackbar(appText("Could not save language. Please try again."))
        }
    }
    Scaffold(
        modifier = Modifier.testTag("language-page"),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(notices) },
        topBar = {
            TopAppBar(
                title = { Text(appText("Language"), fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onBack, modifier = Modifier.testTag("language-back")) {
                        Icon(SettingsIcons.back, appText("Back to settings"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(appText("Choose your language"), fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, lineHeight = 32.sp, color = colors.onBackground)
                Text(appText("Use Mongosky in the language you prefer."),
                    color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                LanguageOption(AppLocale.ENGLISH, selected, "English", appText("English"), ::choose)
                LanguageOption(AppLocale.BANGLA, selected, "বাংলা", appText("Bengali"), ::choose)
                Text(appText("Your choice is saved on this device. You can change it anytime."),
                    color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun LanguageOption(
    locale: AppLocale, selected: AppLocale, nativeName: String, description: String,
    onChoose: (AppLocale) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val active = selected == locale
    val brand = Color(0xFFE60023)
    Surface(
        color = colors.surface, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) brand else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp)
                .testTag("language-${locale.tag}")
                .toggleable(value = active, role = Role.Switch,
                    onValueChange = { checked -> onChoose(if (checked) locale else locale.other()) })
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(nativeName, color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, lineHeight = 26.sp)
                if (description != nativeName) Text(description,
                    color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
            }
            Switch(checked = active, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = brand,
                    checkedThumbColor = Color.White, checkedBorderColor = brand))
        }
    }
}
