package com.mongosky.app.helpsupport

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small cached native vectors; no image downloads or extended icon dependency. */
internal object HelpSupportIcons {
    val back by lazy { icon("HelpBack", "M15 6L9 12L15 18", mirrored = true) }
    val chevron by lazy { icon("HelpChevron", "M9 6L15 12L9 18", mirrored = true) }
    val down by lazy { icon("HelpExpand", "M6 9L12 15L18 9") }
    val close by lazy { icon("HelpClear", "M6 6L18 18M6 18L18 6") }
    val search by lazy { icon("HelpSearch", "M3 10A7 7 0 1 0 17 10A7 7 0 1 0 3 10M15 15L21 21") }
    val help by lazy { icon("HelpQuestion", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12M9 9A3 3 0 0 1 15 10C15 12 12 12 12 14M12 17L12.01 17") }
    val mail by lazy { icon("HelpMail", "M3 5H21V19H3ZM3 5L12 12L21 5") }
    val report by lazy { icon("HelpReport", "M12 3L22 21H2ZM12 9V14M12 17L12.01 17") }
    val feedback by lazy { icon("HelpFeedback", "M3 4H21V17H8L3 21ZM7 8H17M7 12H14") }
    val copy by lazy { icon("HelpCopy", "M9 9H21V21H9ZM15 5V3H3V15H5") }

    private fun icon(name: String, path: String, mirrored: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirrored).apply {
            addPath(PathParser().parsePathString(path).toNodes(), fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()
}
