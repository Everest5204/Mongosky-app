@file:Suppress("DEPRECATION")
package com.mongosky.app.darkmode

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable internal fun DarkModeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    remember(context) { AppAppearance.initialize(context) }
    DarkModeSystemBars()
    val light = remember { lightColorScheme() }
    MaterialTheme(colorScheme = AppColors.scheme(light)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Also works for full-screen Compose dialogs; media can explicitly retain dark bars. */
@Composable internal fun DarkModeSystemBars(forceDark: Boolean = false) {
    val view = LocalView.current;val dark = AppAppearance.isDark || forceDark
    if (!view.isInEditMode) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
        SideEffect {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window,window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                if (Build.VERSION.SDK_INT < 35) {
                    val color = if (forceDark) Color.Black else if (dark) AppColors.darkBackground else Color.White
                    if (window.statusBarColor != color.toArgb()) window.statusBarColor = color.toArgb()
                    if (window.navigationBarColor != color.toArgb()) window.navigationBarColor = color.toArgb()
                }
            }
        }
    }
}
private fun Context.activity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext;if (next === current) break;current = next
    }
    return current as? Activity
}
