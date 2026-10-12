package com.mongosky.app.notifications

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object NotificationIcons {
    val bell by lazy { outline("Notifications", "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21a2 2 0 0 0 4 0") }
    val back by lazy { outline("Back", "M15 18l-6-6 6-6") }
    val refresh by lazy { outline("Refresh", "M20 7v5h-5 M4 17v-5h5 M5 7a8 8 0 0 1 13-2l2 2 M19 17a8 8 0 0 1-13 2l-2-2") }
    val user by lazy { outline("User", "M20 21v-2a7 7 0 0 0-16 0v2 M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0") }
    val follow by lazy { outline("Follow", "M14 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M19 8v6 M16 11h6") }
    val followBack by lazy { outline("FollowBack", "M14 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M16 11l2 2 4-4") }
    val comment by lazy { outline("Comment", "M21 11.5a8.5 8.5 0 0 1-8.5 8.5H3l2-5a8.5 8.5 0 1 1 16-3.5z") }
    val reply by lazy { outline("Reply", "M9 10L4 15l5 5 M4 15h10a6 6 0 0 0 0-12") }
    val heart by lazy { ImageVector.Builder("Love",24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8z").toNodes(), fill=SolidColor(Color.Black)).build() }
    val verified by lazy { ImageVector.Builder("Verified",24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString("M23 12 L20.56 9.21 L20.9 5.52 L17.29 4.7 L15.4 1.5 L12 2.96 L8.6 1.5 L6.71 4.69 L3.1 5.51 L3.44 9.2 L1 12 L3.44 14.79 L3.1 18.48 L6.71 19.3 L8.6 22.5 L12 21.04 L15.4 22.5 L17.29 19.31 L20.9 18.49 L20.56 14.8 Z").toNodes(),fill=SolidColor(Color(0xFFFF3040)))
        .addPath(PathParser().parsePathString("M10.09 16.17 L6.5 12.58 L7.91 11.17 L10.09 13.34 L16.09 7.34 L17.5 8.76 Z").toNodes(),fill=SolidColor(Color.White)).build() }
    private fun outline(name:String,path:String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString(path).toNodes(),stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()
}
