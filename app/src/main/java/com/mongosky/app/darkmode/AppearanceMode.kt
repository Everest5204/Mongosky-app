package com.mongosky.app.darkmode

/** Explicit device preference; independent of accounts and server state. */
internal enum class AppearanceMode(val stored: String) {
    LIGHT("light"), DARK("dark");
    fun fromSwitch(checked: Boolean) = if (checked) this else if (this == LIGHT) DARK else LIGHT
    companion object { fun parse(value: String?) = entries.firstOrNull { it.stored == value } ?: LIGHT }
}
internal interface AppearancePreferences {
    fun read(): AppearanceMode
    fun write(mode: AppearanceMode)
}
