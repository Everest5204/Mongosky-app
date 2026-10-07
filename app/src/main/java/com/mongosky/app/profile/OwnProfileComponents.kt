package com.mongosky.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object OwnProfileColors {
    val brand = Color(0xFFFF1020)
    val text = Color(0xFF111111)
    val muted = Color(0xFF717171)
    val soft = Color(0xFFF3F3F3)
    val line = Color(0xFFE5E5E5)
}
internal object OwnProfileIcons {
    val verified by lazy { ImageVector.Builder("Verified", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.51L3.44 9.2L1 12L3.44 14.79L3.1 18.48L6.71 19.3L8.6 22.5L12 21.04L15.4 22.5L17.29 19.31L20.9 18.49L20.56 14.8Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
        addPath(PathParser().parsePathString("M10.09 16.17L6.5 12.58L7.91 11.17L10.09 13.34L16.09 7.34L17.5 8.76Z").toNodes(), fill = SolidColor(Color.White))
    }.build() }
    val camera by lazy { vector("Camera", "M4 6H7L9 3H15L17 6H20A2 2 0 0 1 22 8V19A2 2 0 0 1 20 21H4A2 2 0 0 1 2 19V8A2 2 0 0 1 4 6ZM16 13A4 4 0 1 1 8 13A4 4 0 1 1 16 13") }
    val plus by lazy { vector("AddStory", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12M12 8V16M8 12H16") }
    val play by lazy { vector("Play", "M9 5L20 12L9 19Z") }
    val pause by lazy { vector("Pause", "M7 5V19M17 5V19") }
    val back by lazy { vector("Back", "M15 6L9 12L15 18") }
    private fun vector(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

@Composable
internal fun OwnProfileSpinner(modifier: Modifier = Modifier, color: Color = OwnProfileColors.brand) {
    CircularProgressIndicator(modifier.size(24.dp), color = color, strokeWidth = 2.dp)
}

@Composable
internal fun OwnProfileNotice(message: String, action: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = OwnProfileColors.muted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        TextButton(onClick, enabled = enabled) { Text(action, color = OwnProfileColors.brand, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun OwnProfileDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(OwnProfileColors.line)) }
