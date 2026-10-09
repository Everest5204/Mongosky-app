package com.mongosky.app.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Native Material3 sheet: cached header, native ripple, scroll and drawer gesture handling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeftDrawer(viewModel: DrawerViewModel, userName: String, profileImageUrl: String?,
    selectedRouteName: String, enabled: Boolean, onClose: () -> Unit,
    onNavigate: (DrawerDestination) -> Unit, onSignOut: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val preferred = when { screenWidth > 768 -> 380f; screenWidth > 480 -> 340f; else -> 320f }
    val width = preferred.coerceAtMost(screenWidth * 0.92f).dp
    ModalDrawerSheet(modifier = Modifier.width(width).fillMaxHeight(), drawerShape = RoundedCornerShape(0.dp),
        drawerContainerColor = Color.White, drawerTonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DrawerHeader(state, userName, profileImageUrl, enabled && !state.sessionExpired,
                onProfile = { onNavigate(DrawerDestination.PROFILE) }, onClose = onClose)
            state.error?.let { message ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    Text(message, color = DrawerColors.muted, fontSize = 13.sp)
                    if (!state.sessionExpired) TextButton(onClick = { viewModel.refresh(force = true) }, enabled = enabled) { Text("Retry") }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DrawerColors.border))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.selectableGroup()) {
                DrawerDestination.entries.forEach { item ->
                    DrawerMenuItem(item, selectedRouteName, enabled) {
                        if (item == DrawerDestination.SIGN_OUT) onSignOut() else onNavigate(item)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
