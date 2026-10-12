package com.mongosky.app.settingsinformation

import com.mongosky.app.settings.SettingsMenuId

/** Guard stale callbacks and repeated taps without finishing the Android Activity. */
internal object SettingsInformationNavigation {
    private val routes = setOf("ABOUT", "PRIVACY_POLICY", "GET_VERIFIED")
    fun owns(route: String): Boolean = route in routes
    fun destination(menu: SettingsMenuId): String? = when (menu) {
        SettingsMenuId.ABOUT -> "ABOUT"
        SettingsMenuId.PRIVACY_POLICY -> "PRIVACY_POLICY"
        SettingsMenuId.GET_VERIFIED -> "GET_VERIFIED"
        else -> null
    }
    fun open(stack: List<String>, destination: String): List<String> =
        if (!owns(destination) || stack.lastOrNull() != "SETTINGS") stack
        else stack + destination

    fun back(stack: List<String>, expected: String): List<String> =
        if (!owns(expected) || stack.lastOrNull() != expected) stack
        else stack.dropLast(1).ifEmpty { listOf("HOME", "SETTINGS") }
}
