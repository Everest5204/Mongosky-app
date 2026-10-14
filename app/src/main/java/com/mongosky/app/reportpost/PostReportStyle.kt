package com.mongosky.app.reportpost

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ReportColors {
    val text = Color(0xFF172033)
    val muted = Color(0xFF697386)
    val line = Color(0xFFE8EBF0)
    val soft = Color(0xFFF5F7FA)
    val brand = Color(0xFFE60023)
    val success = Color(0xFF178453)
}

internal object ReportIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .apply { path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw) }.build()
    val back = icon("Back") { moveTo(15f,5f);lineTo(8f,12f);lineTo(15f,19f) }
    val close = icon("Close") { moveTo(6f,6f);lineTo(18f,18f);moveTo(18f,6f);lineTo(6f,18f) }
    val chevron = icon("Select reason") { moveTo(9f,6f);lineTo(15f,12f);lineTo(9f,18f) }
    val flag = icon("Report") { moveTo(5f,21f);lineTo(5f,3f);curveTo(10f,0f,14f,6f,20f,3f);lineTo(20f,14f);curveTo(14f,17f,10f,11f,5f,14f) }
    val shield = icon("Private report") { moveTo(12f,2f);lineTo(21f,6f);lineTo(20f,13f);curveTo(19f,18f,15f,21f,12f,22f);curveTo(9f,21f,5f,18f,4f,13f);lineTo(3f,6f);close() }
    val check = icon("Received") { moveTo(5f,12f);lineTo(10f,17f);lineTo(19f,7f) }
}
