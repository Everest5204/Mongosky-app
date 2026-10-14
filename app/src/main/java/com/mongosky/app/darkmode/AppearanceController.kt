package com.mongosky.app.darkmode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal class AppearanceController(private val preferences: AppearancePreferences) {
    var mode by mutableStateOf(preferences.read()); private set
    /** The selected mode remains valid if a preference write cannot start. */
    fun select(next: AppearanceMode): Boolean {
        if (mode == next) return true
        return try { preferences.write(next); mode = next; true } catch (_: Exception) { false }
    }
}
