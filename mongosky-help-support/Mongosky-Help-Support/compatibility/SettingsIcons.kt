package com.mongosky.app.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Icon geometry from lucide-react 1.45.0, the exact web lockfile version (ISC license).
// Attribution and license are supplied in the ZIP under licenses/Lucide-LICENSE.txt.
internal data class SettingsIconPath(val data: String, val filled: Boolean = false)
internal object SettingsIcons {
    val paths: Map<SettingsMenuId, List<SettingsIconPath>> = mapOf(
        SettingsMenuId.LANGUAGE to listOf(SettingsIconPath("m5 8 6 6"), SettingsIconPath("m4 14 6-6 2-3"), SettingsIconPath("M2 5h12"), SettingsIconPath("M7 2h1"), SettingsIconPath("m22 22-5-10-5 10"), SettingsIconPath("M14 18h6")),
        SettingsMenuId.DARK_MODE to listOf(SettingsIconPath("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")),
        SettingsMenuId.PERSONAL_DETAILS to listOf(SettingsIconPath("M7.0 8.0A5.0 5.0 0 1 0 17.0 8.0A5.0 5.0 0 1 0 7.0 8.0Z"), SettingsIconPath("M20 21a8 8 0 0 0-16 0")),
        SettingsMenuId.CHANGE_PASSWORD to listOf(SettingsIconPath("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z"), SettingsIconPath("M16.0 7.5A0.5 0.5 0 1 0 17.0 7.5A0.5 0.5 0 1 0 16.0 7.5Z", filled = true)),
        SettingsMenuId.GET_VERIFIED to listOf(SettingsIconPath("M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z"), SettingsIconPath("m16 9-5.5 5.5L8 12")),
        SettingsMenuId.PRIVACY_POLICY to listOf(SettingsIconPath("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"), SettingsIconPath("m9 12 2 2 4-4")),
        SettingsMenuId.HELP_AND_SUPPORT to listOf(SettingsIconPath("M2.0 12.0A10.0 10.0 0 1 0 22.0 12.0A10.0 10.0 0 1 0 2.0 12.0Z"), SettingsIconPath("M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"), SettingsIconPath("M12 17h.01")),
        SettingsMenuId.DEACTIVATE_ACCOUNT to listOf(SettingsIconPath("m16.5 16.5 5 5"), SettingsIconPath("M2 21a8 8 0 0 1 11.531-7.18"), SettingsIconPath("m21.5 16.5-5 5"), SettingsIconPath("M5.0 8.0A5.0 5.0 0 1 0 15.0 8.0A5.0 5.0 0 1 0 5.0 8.0Z")),
        SettingsMenuId.DELETE_ACCOUNT to listOf(SettingsIconPath("M10 11v6"), SettingsIconPath("M14 11v6"), SettingsIconPath("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"), SettingsIconPath("M3 6h18"), SettingsIconPath("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")),
        SettingsMenuId.ABOUT to listOf(SettingsIconPath("M2.0 12.0A10.0 10.0 0 1 0 22.0 12.0A10.0 10.0 0 1 0 2.0 12.0Z"), SettingsIconPath("M12 16v-4"), SettingsIconPath("M12 8h.01")),
    )
    private val vectors by lazy { paths.mapValues { (id, nodes) -> build(id.webId, nodes) } }
    fun forMenu(id: SettingsMenuId): ImageVector = vectors.getValue(id)
    val back by lazy { build("SettingsBack", listOf(SettingsIconPath("M15 18L9 12L15 6")), 2.2f) }
    val chevron by lazy { build("SettingsChevron", listOf(SettingsIconPath("m9 18 6-6-6-6")), 2.2f) }
    private fun build(name: String, nodes: List<SettingsIconPath>, width: Float = 2.15f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            nodes.forEach { node ->
                addPath(PathParser().parsePathString(node.data).toNodes(),
                    fill = if (node.filled) SolidColor(Color.Black) else null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
