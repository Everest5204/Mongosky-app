package com.mongosky.app.editprofile

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

internal object EditProfileColors {
    val green = Color(0xFF087F46)
    val text = Color(0xFF111827)
    val muted = Color(0xFF64748B)
    val border = Color(0xFFE2E8F0)
    val background = Color(0xFFEEF2F5)
    val field = Color(0xFFFBFCFD)
    val disabled = Color(0xFFE2E5E9)
    val disabledText = Color(0xFF7F8894)
    val warning = Color(0xFFB45309)
}
internal object EditProfileIcons {
    val back by lazy { icon("EditorBack", "M19 12H5M12 19L5 12L12 5") }
    val camera by lazy { icon("EditorCamera", "M14.5 4H9.5L7.5 7H4A2 2 0 0 0 2 9V18A2 2 0 0 0 4 20H20A2 2 0 0 0 22 18V9A2 2 0 0 0 20 7H16.5ZM16 13A4 4 0 1 1 8 13A4 4 0 1 1 16 13") }
    val user by lazy { icon("EditorUser", "M20 21V19A7 7 0 0 0 4 19V21M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7") }
    val globe by lazy { icon("EditorPublic", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12M3 12H21M12 3A17 17 0 0 1 12 21A17 17 0 0 1 12 3") }
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}
