package com.mongosky.app.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object HomeFeedIcons {
    val delete by lazy { outline("Delete", "M3 6h18 M8 6V3h8v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7") }
    val edit by lazy { outline("Edit", "M16 3l5 5 M3 21l4-1 14-14a2 2 0 0 0-4-4L3 16v5") }
    val pin by lazy { outline("Pin", "M16 3l5 5-5 2-2 5-5-5 5-2z M9 15l-6 6") }
    val save by lazy { outline("Save", "M5 3h14v18l-7-4-7 4z") }
    val copy by lazy { outline("Copy", "M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2 M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2") }
    val download by lazy { outline("Download", "M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5") }
    val hide by lazy { outline("Hide", "M3 3l18 18 M10 10a3 3 0 0 0 4 4 M9 5a10 10 0 0 1 12 7 14 14 0 0 1-3 4 M6 6a14 14 0 0 0-3 6 10 10 0 0 0 13 7") }
    val report by lazy { outline("Report", "M4 22V3 M4 3h16l-3 4 3 4H4") }
    // Same filled seal, white check and #FF3040 as the web badge.
    val verified = ImageVector.Builder("HomeVerified", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString("M23 12 L20.56 9.21 L20.9 5.52 L17.29 4.7 L15.4 1.5 L12 2.96 L8.6 1.5 L6.71 4.69 L3.1 5.51 L3.44 9.2 L1 12 L3.44 14.79 L3.1 18.48 L6.71 19.3 L8.6 22.5 L12 21.04 L15.4 22.5 L17.29 19.31 L20.9 18.49 L20.56 14.8 Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
        .addPath(PathParser().parsePathString("M10.09 16.17 L6.5 12.58 L7.91 11.17 L10.09 13.34 L16.09 7.34 L17.5 8.76 Z").toNodes(), fill = SolidColor(Color.White)).build()
    fun forAction(action: HomeMenuAction) = when (action) {
        HomeMenuAction.DELETE -> delete
        HomeMenuAction.EDIT -> edit
        HomeMenuAction.PIN -> pin
        HomeMenuAction.SAVE -> save
        HomeMenuAction.COPY -> copy
        HomeMenuAction.DOWNLOAD -> download
        HomeMenuAction.HIDE -> hide
        HomeMenuAction.REPORT -> report
    }
    private fun outline(name: String, path: String) = ImageVector.Builder("Home$name", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
}
