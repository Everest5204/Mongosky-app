package com.mongosky.app.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Cached vectors keep the feature independent of external icon downloads. */
internal object SearchIcons {
    val search = outline("Search", "M21 21l-4.3-4.3 M19 11a8 8 0 1 1-16 0a8 8 0 0 1 16 0")
    val person = outline("Person", "M20 21v-2a7 7 0 0 0-14 0v2 M17 7a4 4 0 1 1-8 0a4 4 0 0 1 8 0")
    val peopleSearch = outline("PeopleSearch", "M16 21v-2a6 6 0 0 0-12 0v2 M13 7a4 4 0 1 1-8 0a4 4 0 0 1 8 0 M21 14a4 4 0 1 1-8 0a4 4 0 0 1 8 0 M23 20l-3-3")
    val close = outline("Close", "M6 6l12 12 M6 18L18 6")
    val refresh = outline("Refresh", "M20 7v5h-5 M4 17v-5h5 M6.1 6a8 8 0 0 1 13.4 4 M17.9 18a8 8 0 0 1-13.4-4")
    // Exact seal, filled tick and colors from the web VerifiedBadge component.
    val verified: ImageVector = ImageVector.Builder("Verified", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString("M23 12 L20.56 9.21 L20.9 5.52 L17.29 4.7 L15.4 1.5 L12 2.96 L8.6 1.5 L6.71 4.69 L3.1 5.51 L3.44 9.2 L1 12 L3.44 14.79 L3.1 18.48 L6.71 19.3 L8.6 22.5 L12 21.04 L15.4 22.5 L17.29 19.31 L20.9 18.49 L20.56 14.8 Z").toNodes(),
            fill = SolidColor(Color(0xFFFF3040))
        ).addPath(
            pathData = PathParser().parsePathString("M10.09 16.17 L6.5 12.58 L7.91 11.17 L10.09 13.34 L16.09 7.34 L17.5 8.76 Z").toNodes(),
            fill = SolidColor(Color.White)
        ).build()

    private fun outline(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(), fill = null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ).build()
}
