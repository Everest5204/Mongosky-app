package com.mongosky.app.language

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mongosky.app.settings.SettingsRoute
import com.mongosky.app.theme.MongoskyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** No account or network is needed: this exercises the actual Settings -> Language integration. */
class LanguageSettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun resetLanguage() {
        compose.runOnUiThread {
            AppLanguage.initialize(compose.activity)
            AppLanguage.select(AppLocale.ENGLISH)
        }
    }

    @After fun restoreLanguage() {
        compose.runOnUiThread { AppLanguage.select(AppLocale.ENGLISH) }
    }

    @Test fun languageChangesAcrossTheCompositionAndIsSavedWithoutRecreatingActivity() {
        val activity = compose.activity
        compose.setContent {
            LanguageProvider {
                MongoskyTheme {
                    Column {
                        Text(appText("Home"), Modifier.testTag("global-home"))
                        // Deliberately the same word as an app label, but this is a person's name.
                        val userName = rememberSaveable { "Home" }
                        Text(userName, Modifier.testTag("user-name"))
                        var languageOpen by rememberSaveable { mutableStateOf(true) }
                        if (languageOpen) LanguageScreen { languageOpen = false }
                    }
                }
            }
        }
        compose.onNodeWithTag("language-en").assertIsOn()
        compose.onNodeWithTag("language-bn").assertIsOff().performClick()
        compose.onNodeWithTag("global-home").assertTextEquals("হোম")
        compose.onNodeWithTag("user-name").assertTextEquals("Home")
        compose.onNodeWithTag("language-bn").assertIsOn()
        compose.onNodeWithTag("language-en").assertIsOff()
        compose.runOnIdle {
            assertSame(activity, compose.activity)
            assertEquals("bn", activity.getSharedPreferences("mongosky_language", Context.MODE_PRIVATE).getString("locale", null))
            assertEquals("Mongosky", activity.getString(com.mongosky.app.R.string.app_name))
        }
        // Turning Bangla off selects English; there is never a state with neither selected.
        compose.onNodeWithTag("language-bn").performClick().assertIsOff()
        compose.onNodeWithTag("language-en").assertIsOn()
        compose.onNodeWithTag("global-home").assertTextEquals("Home")
    }

    @Test fun settingsOpensLanguageAndReturnsWithTranslatedMenu() {
        compose.setContent {
            LanguageProvider {
                MongoskyTheme {
                    SettingsRoute(onBack = {}, onInformationSelected = {})
                }
            }
        }
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithTag("language-page").assertExists()
        compose.onNodeWithTag("language-bn").performClick()
        compose.onNodeWithTag("language-back").performClick()
        compose.onNodeWithText("সেটিংস").assertExists()
        compose.onNodeWithText("ভাষা").performClick()
        compose.onNodeWithTag("language-bn").assertIsOn()
    }
}
