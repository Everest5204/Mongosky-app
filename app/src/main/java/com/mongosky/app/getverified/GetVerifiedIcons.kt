package com.mongosky.app.getverified

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object GetVerifiedIcons {
    // Lucide 1.45.0 geometry; see the bundled ISC license.
    val back by lazy { outline("VerifiedBack", listOf("m12 19-7-7 7-7", "M19 12H5"), 2f) }
    val users by lazy { outline("VerifiedUsers", listOf(
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M16 3.128a4 4 0 0 1 0 7.744", "M22 21v-2a4 4 0 0 0-3-3.87",
        "M5 7A4 4 0 1 0 13 7A4 4 0 1 0 5 7Z"
    ), 1.8f) }
    const val sealPath = "M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.51L3.44 9.2L1 12L3.44 14.79L3.1 18.48L6.71 19.3L8.6 22.5L12 21.04L15.4 22.5L17.29 19.31L20.9 18.49L20.56 14.8Z"
    const val checkPath = "M10.09 16.17L6.5 12.58L7.91 11.17L10.09 13.34L16.09 7.34L17.5 8.76Z"
    val badge by lazy {
        ImageVector.Builder("VerificationIllustration", 96.dp, 96.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(sealPath).toNodes(), fill = SolidColor(Color(0xFFFF3040)))
            addPath(PathParser().parsePathString(checkPath).toNodes(), fill = SolidColor(Color.White))
        }.build()
    }
    private fun outline(name: String, paths: List<String>, stroke: Float): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(PathParser().parsePathString(data).toNodes(), stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
