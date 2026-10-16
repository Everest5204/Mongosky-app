package com.mongosky.app.settings

import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.LanguageSettingsHost

import com.mongosky.app.personaldetails.PersonalDetailsSettingsHost

import com.mongosky.app.deactivateaccount.DeactivateAccountSettingsHost

import com.mongosky.app.helpsupport.HelpSupportSettingsHost

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onChangePassword: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null,
    onInformationSelected: (SettingsMenuId) -> Unit,
    onDarkMode: (() -> Unit)? = null
) {
    LanguageSettingsHost { openLanguage ->
        // Mongosky Personal Details integration v1
        PersonalDetailsSettingsHost { openPersonalDetails ->
            // Mongosky Deactivate Account integration v1
            DeactivateAccountSettingsHost { openDeactivateAccount ->
                // Mongosky Help & Support integration v1
                HelpSupportSettingsHost { openHelpAndSupport ->
                    var entered by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { entered = true }
                    val progress = animateFloatAsState(if (entered) 1f else 0f, tween(140, easing = FastOutSlowInEasing), label = "Settings entrance")
                    val distance = with(LocalDensity.current) { 16.dp.toPx() }
                    Box(Modifier.fillMaxSize().background(AppColors.surface(Color.White))) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; translationX = distance * (1f - progress.value) }) {
                            SettingsScreen(onBack = onBack,
                                enabledMenus = buildSet {
                                    add(SettingsMenuId.LANGUAGE)
                                    add(SettingsMenuId.PERSONAL_DETAILS)
                                    if (onDarkMode != null) add(SettingsMenuId.DARK_MODE)
                                    add(SettingsMenuId.DEACTIVATE_ACCOUNT)
                                    add(SettingsMenuId.HELP_AND_SUPPORT)
                                    addAll(listOf(SettingsMenuId.ABOUT, SettingsMenuId.PRIVACY_POLICY, SettingsMenuId.GET_VERIFIED))
                                    if (onChangePassword != null) add(SettingsMenuId.CHANGE_PASSWORD)
                                    if (onDeleteAccount != null) add(SettingsMenuId.DELETE_ACCOUNT)
                                },
                                onMenuSelected = { item ->
                                    when (item) {
                                        SettingsMenuId.LANGUAGE -> openLanguage()
                                        SettingsMenuId.DARK_MODE -> onDarkMode?.invoke()
                                        SettingsMenuId.PERSONAL_DETAILS -> openPersonalDetails()
                                        SettingsMenuId.DEACTIVATE_ACCOUNT -> openDeactivateAccount()
                                        SettingsMenuId.HELP_AND_SUPPORT -> openHelpAndSupport()
                                        SettingsMenuId.CHANGE_PASSWORD -> onChangePassword?.invoke()
                                        SettingsMenuId.DELETE_ACCOUNT -> onDeleteAccount?.invoke()
                                        SettingsMenuId.ABOUT, SettingsMenuId.PRIVACY_POLICY, SettingsMenuId.GET_VERIFIED -> onInformationSelected(item)
                                        else -> Unit
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
