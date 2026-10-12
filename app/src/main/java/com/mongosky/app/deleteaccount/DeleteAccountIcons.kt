package com.mongosky.app.deleteaccount

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Exact lucide-react 1.45.0 web geometry. ISC license is included with the section ZIP.
internal object DeleteAccountIcons {
    val paths = mapOf(
        "back" to listOf("m12 19-7-7 7-7", "M19 12H5"),
        "trash" to listOf("M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
        "eye" to listOf("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9.0 12.0A3.0 3.0 0 1 0 15.0 12.0A3.0 3.0 0 1 0 9.0 12.0Z"),
        "eyeOff" to listOf("M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20"),
        "profile" to listOf("M7.0 8.0A5.0 5.0 0 1 0 17.0 8.0A5.0 5.0 0 1 0 7.0 8.0Z", "M20 21a8 8 0 0 0-16 0"),
        "media" to listOf("M5.0 3.0H19.0A2.0 2.0 0 0 1 21.0 5.0V19.0A2.0 2.0 0 0 1 19.0 21.0H5.0A2.0 2.0 0 0 1 3.0 19.0V5.0A2.0 2.0 0 0 1 5.0 3.0Z", "M7.0 9.0A2.0 2.0 0 1 0 11.0 9.0A2.0 2.0 0 1 0 7.0 9.0Z", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"),
        "users" to listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M16 3.128a4 4 0 0 1 0 7.744", "M22 21v-2a4 4 0 0 0-3-3.87", "M5.0 7.0A4.0 4.0 0 1 0 13.0 7.0A4.0 4.0 0 1 0 5.0 7.0Z"),
        "message" to listOf("M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719"),
        "badge" to listOf("M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z", "m16 9-5.5 5.5L8 12"),
        "heart" to listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"),
        "warning" to listOf("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01"),
        "info" to listOf("M2.0 12.0A10.0 10.0 0 1 0 22.0 12.0A10.0 10.0 0 1 0 2.0 12.0Z", "M12 16v-4", "M12 8h.01"),
        "lock" to listOf("M11.0 16.0A1.0 1.0 0 1 0 13.0 16.0A1.0 1.0 0 1 0 11.0 16.0Z", "M5.0 10.0H19.0A2.0 2.0 0 0 1 21.0 12.0V20.0A2.0 2.0 0 0 1 19.0 22.0H5.0A2.0 2.0 0 0 1 3.0 20.0V12.0A2.0 2.0 0 0 1 5.0 10.0Z", "M7 10V7a5 5 0 0 1 10 0v3"),
        "check" to listOf("M2.0 12.0A10.0 10.0 0 1 0 22.0 12.0A10.0 10.0 0 1 0 2.0 12.0Z", "m16 9-5.5 5.5L8 12"),
    )
    private val vectors by lazy { paths.mapValues { (name, paths) -> build(name, paths) } }
    fun get(name: String): ImageVector = vectors.getValue(name)
    private fun build(name: String, paths: List<String>) =
        ImageVector.Builder("DeleteAccount:$name", 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEachIndexed { index, data ->
                val accent = when (name) { "warning" -> DeletionColors.red600; "info" -> DeletionColors.blue600; else -> null }
                val stroke = if (accent == null) Color.Black else if (index == 0) accent else Color.White
                addPath(PathParser().parsePathString(data).toNodes(),
                    fill = if (accent != null && index == 0) SolidColor(accent) else null,
                    stroke = SolidColor(stroke), strokeLineWidth = if (name in listOf("back", "eye", "eyeOff", "warning", "info", "lock")) 2f else 1.8f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}
