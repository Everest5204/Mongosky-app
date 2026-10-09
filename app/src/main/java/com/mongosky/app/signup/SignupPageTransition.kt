package com.mongosky.app.signup

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** One active page and a lightweight layer animation; animation frames do not recompose the form. */
@Composable
internal fun SignupPageTransition(forward: Boolean = true, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress = animateFloatAsState(if (entered) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing), label = "Signup page entrance")
    val distance = with(LocalDensity.current) { 20.dp.toPx() } * if (forward) 1f else -1f
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value
            translationX = distance * (1f - progress.value)
        }) { content() }
    }
}
