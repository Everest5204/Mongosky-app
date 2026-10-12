package com.mongosky.app.drawer

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
import com.mongosky.app.communityguidelines.CommunityGuidelinesScreen
import com.mongosky.app.mobileapp.MobileAppScreen
import com.mongosky.app.myactivity.MyActivityScreen
import com.mongosky.app.saveditems.SavedItemsScreen

internal object DrawerInformationNavigation {
    val routes = setOf("MOBILE_APP", "GUIDELINES", "SAVED_ITEMS", "MY_ACTIVITY")
    fun opens(route: String?) = route in routes
    fun push(stack: List<String>, route: String): List<String> {
        require(opens(route))
        return if (stack.lastOrNull() == route) stack else stack.ifEmpty { listOf("HOME") } + route
    }
    /** Ignore a second callback from a page which has already been popped. */
    fun back(stack: List<String>, expected: String): List<String> {
        if (stack.lastOrNull() != expected || !opens(expected)) return stack
        return stack.dropLast(1).ifEmpty { listOf("HOME") }
    }
}

/** Full-screen native destinations; the drawer remains behind an opaque page during its dismissal. */
@Composable
internal fun DrawerInformationPage(route: String, onBack: () -> Unit) {
    key(route) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val progress = animateFloatAsState(if (entered) 1f else 0f, tween(140, easing = FastOutSlowInEasing), label = "Drawer information page entrance")
        val distance = with(LocalDensity.current) { 16.dp.toPx() }
        Box(Modifier.fillMaxSize().background(Color(0xFFF4F7F6))) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; translationX = distance * (1f - progress.value) }) {
                when (route) {
                    "MOBILE_APP" -> MobileAppScreen(onBack)
                    "GUIDELINES" -> CommunityGuidelinesScreen(onBack)
                    "SAVED_ITEMS" -> SavedItemsScreen(onBack)
                    "MY_ACTIVITY" -> MyActivityScreen(onBack)
                }
            }
        }
    }
}
