package com.mongosky.app.darkmode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mongosky.app.settings.SettingsRoute
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests use local appearance state; no real account or server is needed. */
@RunWith(AndroidJUnit4::class)
class DarkModeSettingsFlowTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetMode() {
        compose.runOnIdle { AppAppearance.setDark(false) }
    }

    @Composable private fun SettingsFlow() {
        var stack by rememberSaveable { mutableStateOf(listOf("HOME", "SETTINGS")) }
        DarkModeTheme {
            if (DarkModeNavigation.owns(stack.last())) {
                DarkModeRoute(onBack = { stack = DarkModeNavigation.back(stack, "DARK_MODE") })
            } else {
                SettingsRoute(onBack = {}, onInformationSelected = {},
                    onDarkMode = { stack = DarkModeNavigation.open(stack) })
            }
        }
    }

    @Test fun settingsMenuOpensTheSeparatePageAndBackRestoresSettings() {
        compose.setContent { SettingsFlow() }
        compose.runOnIdle { AppAppearance.setDark(false) }
        compose.onNodeWithText("Dark Mode").performClick()
        compose.onNodeWithTag("dark-mode-page").assertIsDisplayed()
        compose.onNodeWithTag("appearance-dark").performClick().assertIsOn()
        compose.onNodeWithTag("appearance-light").assertIsOff()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Dark Mode").assertIsDisplayed()
    }

    @Test fun restoredNavigationKeepsTheAppearancePageAndSelection() {
        val restore = StateRestorationTester(compose)
        restore.setContent { SettingsFlow() }
        compose.runOnIdle { AppAppearance.setDark(false) }
        compose.onNodeWithText("Dark Mode").performClick()
        compose.onNodeWithTag("appearance-dark").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("dark-mode-page").assertIsDisplayed()
        compose.onNodeWithTag("appearance-dark").assertIsOn()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
