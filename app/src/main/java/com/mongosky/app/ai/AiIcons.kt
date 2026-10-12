package com.mongosky.app.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object AiIcons {
    private fun outline(name:String,path:String):ImageVector = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(),stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    val Menu=outline("Menu","M4,6H20 M4,12H20 M4,18H20")
    val Close=outline("Close","M6,6L18,18 M18,6L6,18")
    val Plus=outline("New chat","M12,5V19 M5,12H19")
    val Moon=outline("Dark mode","M20.7,13A9,9 0,1 1,11 3.3A7,7 0,0 0,20.7 13Z")
    val Sun=outline("Light mode","M16,12A4,4 0,1 1,8 12A4,4 0,0 1,16 12Z M12,2V4 M12,20V22 M2,12H4 M20,12H22 M4.9,4.9L6.3,6.3 M17.7,17.7L19.1,19.1 M4.9,19.1L6.3,17.7 M17.7,6.3L19.1,4.9")
    val Send=outline("Send","M21,3L9,15 M21,3L14,21L9,15L3,10L21,3Z")
}
