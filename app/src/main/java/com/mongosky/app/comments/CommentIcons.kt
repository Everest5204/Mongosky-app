package com.mongosky.app.comments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object CommentIcons {
    val close by lazy { outline("Close", "M6 6l12 12 M18 6L6 18") }
    val message by lazy { outline("Comment", "M21 11.5a8.5 8.5 0 0 1-8.5 8.5H3l2-5a8.5 8.5 0 1 1 16-3.5z") }
    val send by lazy { outline("Send", "M22 2L9 15 M22 2l-7 20-4-9-9-4z") }
    val heart by lazy { outline("Love", heartPath) }
    val loved by lazy { ImageVector.Builder("LovedComment",24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString(heartPath).toNodes(), fill = SolidColor(Color.Black)).build() }
    val more by lazy { outline("CommentOptions", "M5 12h.01 M12 12h.01 M19 12h.01", 3.4f) }
    val edit by lazy { outline("EditComment", "M16 3l5 5 M3 21l4-1 14-14a2 2 0 0 0-4-4L3 16v5") }
    val delete by lazy { outline("DeleteComment", "M3 6h18 M8 6V3h8v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7") }
    val verified by lazy { ImageVector.Builder("CommentVerified",24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString("M23 12 L20.56 9.21 L20.9 5.52 L17.29 4.7 L15.4 1.5 L12 2.96 L8.6 1.5 L6.71 4.69 L3.1 5.51 L3.44 9.2 L1 12 L3.44 14.79 L3.1 18.48 L6.71 19.3 L8.6 22.5 L12 21.04 L15.4 22.5 L17.29 19.31 L20.9 18.49 L20.56 14.8 Z").toNodes(), fill = SolidColor(Color(0xFFFF3040)))
        .addPath(PathParser().parsePathString("M10.09 16.17 L6.5 12.58 L7.91 11.17 L10.09 13.34 L16.09 7.34 L17.5 8.76 Z").toNodes(), fill = SolidColor(Color.White)).build() }
    private const val heartPath = "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8z"
    private fun outline(name:String, path:String, stroke:Float=2.2f) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString(path).toNodes(), stroke=SolidColor(Color.Black),
            strokeLineWidth=stroke, strokeLineCap=StrokeCap.Round, strokeLineJoin=StrokeJoin.Round).build()
}
