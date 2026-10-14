package com.mongosky.app.darkmode

import android.content.Context
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One observable theme for every section. Switching does not recreate the Activity or navigation. */
internal object AppAppearance {
    private var controller by mutableStateOf(AppearanceController(object : AppearancePreferences {
        override fun read() = AppearanceMode.LIGHT
        override fun write(mode: AppearanceMode) = Unit
    }))
    private var initialized = false
    val mode get() = controller.mode
    val isDark get() = mode == AppearanceMode.DARK
    fun initialize(context: Context) {
        if (!initialized) { controller = AppearanceController(AppearanceStore(context));initialized = true }
    }
    val dialogTheme get() = if (isDark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    fun prepareStartup(activity: Activity) {
        initialize(activity.applicationContext)
        activity.setTheme(if (isDark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        activity.window.setBackgroundDrawable(ColorDrawable((if (isDark) AppColors.darkBackground else Color.White).toArgb()))
    }
    fun select(mode: AppearanceMode) = controller.select(mode)
    fun setDark(dark: Boolean) = select(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT)
}
