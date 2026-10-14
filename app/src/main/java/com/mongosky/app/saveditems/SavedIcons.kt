package com.mongosky.app.saveditems

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object SavedIcons {
    private fun icon(name: String,data: String,fill: Boolean = false) = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(data).toNodes(),fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),strokeLineWidth = 1.8f,strokeLineCap = StrokeCap.Round,strokeLineJoin = StrokeJoin.Round)
    }.build()
    val bookmark = icon("Save","M6 3H18V21L12 17L6 21Z")
    val saved = icon("Saved","M6 3H18V21L12 17L6 21Z",true)
    val back = icon("Back","M19 12H5 M11 6L5 12L11 18")
    val search = icon("Search","M16 10A6 6 0 1 1 4 10A6 6 0 1 1 16 10 M15 15L21 21")
    val close = icon("Clear","M6 6L18 18 M6 18L18 6")
    val refresh = icon("Refresh","M20 5V10H15 M20 10A8 8 0 1 0 20 15")
    val more = icon("Options","M12 4V4.1 M12 12V12.1 M12 20V20.1")
    val photo = icon("Photo","M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3 M3 16L9 10L15 17L18 14L21 17 M17 7H17.1")
    val video = icon("Video","M8 4L20 12L8 20Z")
    val text = icon("Text","M5 4H19 M12 4V20 M8 20H16")
    val lock = icon("Private","M7 10V7A5 5 0 0 1 17 7V10 M6 10H18A1 1 0 0 1 19 11V20A1 1 0 0 1 18 21H6A1 1 0 0 1 5 20V11A1 1 0 0 1 6 10 M12 14V17")
}
