package com.mongosky.app.changepassword

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Lucide-react 1.45.0 vector geometry from the web lockfile; ISC license in the ZIP.
internal object ChangePasswordIcons {
    val paths = mapOf(
        "arrow-left" to listOf("m12 19-7-7 7-7", "M19 12H5"),
        "eye" to listOf("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9.0 12.0A3.0 3.0 0 1 0 15.0 12.0A3.0 3.0 0 1 0 9.0 12.0Z"),
        "eye-off" to listOf("M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20"),
    )
    val back by lazy { vector("arrow-left") }
    val eye by lazy { vector("eye") }
    val eyeOff by lazy { vector("eye-off") }
    private fun vector(name: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.getValue(name).forEach { data ->
            addPath(PathParser().parsePathString(data).toNodes(), stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()
}
