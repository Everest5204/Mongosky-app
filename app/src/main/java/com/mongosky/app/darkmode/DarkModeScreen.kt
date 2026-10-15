@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.darkmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Light mode is pure white; dark mode follows the app's existing dark palette. */
private val DarkModePageColors = lightColorScheme(
    primary = Color(0xFFE60023),
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color(0xFFE60023),
    background = Color.White,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF667085),
    outlineVariant = Color(0xFFE5E7EB),
    surfaceTint = Color.White,
)

@Composable internal fun DarkModeScreen(onBack: () -> Unit) {
    MaterialTheme(colorScheme = if (AppAppearance.isDark) AppColors.DarkScheme else DarkModePageColors) {
        DarkModePageContent(onBack)
    }
}

@Composable private fun DarkModePageContent(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme;val selected = AppAppearance.mode
    val notices = remember { SnackbarHostState() };val scope = rememberCoroutineScope()
    fun choose(mode: AppearanceMode) { if (!AppAppearance.select(mode)) scope.launch { notices.showSnackbar("Could not save appearance. Please try again.") } }
    Scaffold(modifier = Modifier.testTag("dark-mode-page"),containerColor = colors.background,snackbarHost = { SnackbarHost(notices) },topBar = {
        TopAppBar(title = { Text("Dark Mode",fontWeight = FontWeight.SemiBold,fontSize = 20.sp,modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onBack) { Icon(DarkModeIcons.back,"Back to settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface))
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp,vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose your appearance",fontWeight = FontWeight.SemiBold,fontSize = 22.sp,color = colors.onBackground)
                Text("Choose the look that feels right for you. It applies across Mongosky.",color = colors.onSurfaceVariant,fontSize = 14.sp,lineHeight = 21.sp)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppearanceOption(AppearanceMode.LIGHT,selected,"Light mode","A bright, familiar look",DarkModeIcons.sun,::choose)
                    AppearanceOption(AppearanceMode.DARK,selected,"Dark mode","A softer look in low light",DarkModeIcons.moon,::choose)
                }
                Text("Your choice is saved on this device.",color = colors.onSurfaceVariant,fontSize = 12.sp,modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
@Composable private fun AppearanceOption(mode: AppearanceMode,selected: AppearanceMode,title: String,subtitle: String,icon: ImageVector,onChoose: (AppearanceMode) -> Unit) {
    val colors = MaterialTheme.colorScheme;val active = mode == selected
    Surface(color = colors.surface,shape = RoundedCornerShape(18.dp),border = BorderStroke(if(active) 1.5.dp else 1.dp,if(active) colors.primary else colors.outlineVariant),modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(role = Role.Button,onClick = { onChoose(mode) }).padding(start = 16.dp,end = 10.dp,top = 16.dp,bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = if(active) colors.primaryContainer else colors.surfaceVariant,shape = RoundedCornerShape(13.dp),modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon,null,tint = if(active) colors.onPrimaryContainer else colors.onSurfaceVariant,modifier = Modifier.size(25.dp)) }
            }
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title,color = colors.onSurface,fontWeight = FontWeight.SemiBold,fontSize = 16.sp)
                Text(subtitle,color = colors.onSurfaceVariant,fontSize = 12.sp,lineHeight = 18.sp)
            }
            Switch(checked = active,onCheckedChange = { checked -> onChoose(mode.fromSwitch(checked)) },
                colors = SwitchDefaults.colors(checkedTrackColor = DarkModePageColors.primary,checkedThumbColor = Color.White,checkedBorderColor = DarkModePageColors.primary),
                modifier = Modifier.testTag("appearance-${mode.stored}"))
        }
    }
}
internal object DarkModeIcons {
    private fun icon(name: String,draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .apply { path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=draw) }.build()
    val back = icon("Back") { moveTo(15f,5f);lineTo(8f,12f);lineTo(15f,19f) }
    val moon = icon("Dark mode") { moveTo(20.5f,13.5f);curveTo(13f,16f,8f,11f,10.5f,3.5f);curveTo(0f,7f,4f,23f,15f,21f);curveTo(18f,20f,20f,17f,20.5f,13.5f) }
    val sun = icon("Light mode") { moveTo(17f,12f);curveTo(17f,19f,7f,19f,7f,12f);curveTo(7f,5f,17f,5f,17f,12f);close();moveTo(12f,2f);lineTo(12f,4f);moveTo(12f,20f);lineTo(12f,22f);moveTo(2f,12f);lineTo(4f,12f);moveTo(20f,12f);lineTo(22f,12f);moveTo(4.9f,4.9f);lineTo(6.3f,6.3f);moveTo(17.7f,17.7f);lineTo(19.1f,19.1f);moveTo(4.9f,19.1f);lineTo(6.3f,17.7f);moveTo(17.7f,6.3f);lineTo(19.1f,4.9f) }
}
