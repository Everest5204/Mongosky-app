package com.mongosky.app.myactivity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object ActivityIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    val back = icon("Back","M19 12H5 M11 6L5 12L11 18")
    val history = icon("Activity","M3 10A9 9 0 1 1 5 18 M3 4V10H9 M12 7V12L15 14")
    val refresh = icon("Refresh","M20 5V10H15 M20 10A8 8 0 1 0 20 15")
    val more = icon("Options","M12 4V4.1 M12 12V12.1 M12 20V20.1")
    val post = icon("Post","M6 3H18A2 2 0 0 1 20 5V19A2 2 0 0 1 18 21H6A2 2 0 0 1 4 19V5A2 2 0 0 1 6 3 M8 8H16 M8 12H16 M8 16H13")
    val heart = icon("Reaction","M12 20L4 12C-1 7 6 0 12 6C18 0 25 7 20 12Z")
    val comment = icon("Comment","M6 3H18A3 3 0 0 1 21 6V15A3 3 0 0 1 18 18H9L3 21V6A3 3 0 0 1 6 3")
    val people = icon("Connection","M15 8A3 3 0 1 1 9 8A3 3 0 1 1 15 8 M5 21V19A7 7 0 0 1 19 19V21 M20 8V14 M17 11H23")
    val lock = icon("Private","M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A1 1 0 0 1 19 11V20A1 1 0 0 1 18 21H6A1 1 0 0 1 5 20V11A1 1 0 0 1 6 10 M12 14V17")
}
