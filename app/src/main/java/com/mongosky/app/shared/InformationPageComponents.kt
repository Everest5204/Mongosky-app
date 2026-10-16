package com.mongosky.app.shared

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object InformationColors {
    val heading = Color(0xFF111827)
    val text = Color(0xFF475467)
    val muted = Color(0xFF667085)
    val primary = Color(0xFF00866B)
    val primaryDark = Color(0xFF006B56)
    val border = Color(0xFFE4E9E7)
}
internal object InformationIcons {
    val back by lazy { outline("InformationBack", listOf("M15 18L9 12L15 6")) }
    val shield by lazy { outline("InformationShield", listOf("M12 3L19 6V11C19 15.5 16.2 19.4 12 21C7.8 19.4 5 15.5 5 11V6L12 3Z", "M9 12L11 14L15.5 9.5")) }
    val check by lazy { outline("InformationCheck", listOf("M8.5 12L10.8 14.3L15.8 9.3")) }
    val info by lazy { outline("InformationNote", listOf("M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3", "M12 11V16", "M12 7.5V8.5")) }
    private fun outline(name: String, paths: List<String>) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round) }
    }.build()
}

/** The MainScreen host supplies system-bar insets; this page owns only its app bar and content. */
@Composable
internal fun InformationPageFrame(
    title: String, onBack: () -> Unit, background: Color, onTitleClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(AppColors.surface(background))) {
        Surface(color = AppColors.surface(Color.White), shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(InformationIcons.back, contentDescription = appDescription("Return to previous page"), tint = AppColors.content(InformationColors.heading), modifier = Modifier.size(22.dp))
                }
                Box(Modifier.weight(1f).heightIn(min = 48.dp).then(if (onTitleClick != null)
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTitleClick) else Modifier), contentAlignment = Alignment.Center) {
                    Text(appText(title), color = AppColors.content(InformationColors.heading), fontFamily = FontFamily.SansSerif,
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() })
                }
                Spacer(Modifier.width(48.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.outline(Color(0xFFE5E7EB))))
        content()
    }
}
@Composable
internal fun InformationComingSoon(title: String, message: String, onBack: () -> Unit) {
    InformationPageFrame(title, onBack, Color(0xFFF6F8F7)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableHeight = maxHeight
            Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = availableHeight)
                .padding(horizontal = 20.dp, vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text(appText(message), color = AppColors.content(InformationColors.text), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 31.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.widthIn(max = 560.dp))
            }
        }
    }
}
@Composable
internal fun InformationCard(
    modifier: Modifier = Modifier, colors: List<Color> = listOf(Color.White, Color.White),
    radius: Dp = 22.dp, border: Color? = InformationColors.border,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Surface(modifier, color = AppColors.surface(Color.Transparent), shape = shape,
        border = border?.let { BorderStroke(1.dp, AppColors.outline(it)) }, shadowElevation = 3.dp) {
        Box(Modifier.background(Brush.linearGradient(AppColors.surfaces(colors))), content = content)
    }
}
@Composable
internal fun InformationHeading(text: String, size: Float = 26f, color: Color = InformationColors.heading, centered: Boolean = false) {
    Text(appText(text), color = AppColors.content(color), fontSize = size.sp, lineHeight = (size * 1.18f).sp, letterSpacing = (-size * .04f).sp,
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.semantics { heading() })
}
@Composable
internal fun InformationBadge(text: String, background: Color = Color(0xFFE9F7F3), color: Color = InformationColors.primaryDark, icon: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(100.dp)).background(AppColors.surface(background)).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon) Icon(InformationIcons.shield, null, tint = AppColors.content(color), modifier = Modifier.size(20.dp))
        Text(appText(text), color = AppColors.content(color), fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = .9.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
    }
}
@Composable
internal fun InformationBackButton(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(Color.White), contentColor = AppColors.content(InformationColors.primaryDark)),
        shape = RoundedCornerShape(13.dp), modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Icon(InformationIcons.back, null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(appText(label), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
    }
}

/** Break into columns only when both panes have useful width and text is not enlarged. */
@Composable
internal fun InformationPair(wide: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit, gap: Dp = 30.dp) {
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) { left(); right() }
}
