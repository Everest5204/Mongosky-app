package com.mongosky.app.settings

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
internal fun SettingsRoute(onBack: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress = animateFloatAsState(if (entered) 1f else 0f, tween(140, easing = FastOutSlowInEasing), label = "Settings entrance")
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; translationX = distance * (1f - progress.value) }) {
            // This section is deliberately menu-only. No submenu callback is supplied.
            SettingsScreen(onBack = onBack)
        }
    }
}
