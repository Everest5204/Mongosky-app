package com.mongosky.app.reels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The same 24px SVG paths used by the web Reels controls. */
internal object ReelsIcons {
    private fun outline(name: String, data: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(data).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    private const val HEART = "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78Z"
    val heart by lazy { outline("Love", HEART) }
    val filledHeart by lazy { ImageVector.Builder("Loved", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(HEART).toNodes(), fill = SolidColor(Color.White))
    }.build() }
    val comment by lazy { outline("Comments", "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7A8.38 8.38 0 0 1 4 11.5a8.5 8.5 0 0 1 4.7-7.6A8.38 8.38 0 0 1 12.5 3h.5a8.48 8.48 0 0 1 8 8v.5Z") }
    val share by lazy { outline("Share", "M22 2 11 13 M22 2l-7 20-4-9-9-4 20-7Z") }
    val back by lazy { outline("Back", "m15 18-6-6 6-6") }
    val forward by lazy { outline("Next", "m9 18 6-6-6-6") }
    val preview by lazy { ImageVector.Builder("Reels preview", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z M7 4l3.5 4 M13 4l3.5 4 M3 9h18").toNodes(),
            stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        addPath(PathParser().parsePathString("m10 12 5 3-5 3v-6Z").toNodes(), fill = SolidColor(Color.White))
    }.build() }
    val volume by lazy { outline("Sound on", "M11 5 6 9H3v6h3l5 4V5Z M15.54 8.46a5 5 0 0 1 0 7.07 M19.07 4.93a10 10 0 0 1 0 14.14") }
    val muted by lazy { outline("Sound off", "M11 5 6 9H3v6h3l5 4V5Z M23 9l-6 6 M17 9l6 6") }
    val play by lazy { ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) { moveTo(7f, 4f); lineTo(21f, 12f); lineTo(7f, 20f); close() }
    }.build() }
    val seal by lazy { ImageVector.Builder("Verified", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString("M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.51L3.44 9.2L1 12L3.44 14.79L3.1 18.48L6.71 19.3L8.6 22.5L12 21.04L15.4 22.5L17.29 19.31L20.9 18.49L20.56 14.8Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
        addPath(PathParser().parsePathString("M10.09 16.17L6.5 12.58L7.91 11.17L10.09 13.34L16.09 7.34L17.5 8.76Z").toNodes(), fill = SolidColor(Color.White))
    }.build() }
}
