package com.mongosky.app.personaldetails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object DetailsColors {
    val text = Color(0xFF172033); val muted = Color(0xFF697386); val line = Color(0xFFE6E9EF)
    val brand = Color(0xFFE60023); val soft = Color(0xFFF5F7FA); val success = Color(0xFF178453)
}
internal object DetailsIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .apply { path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw) }.build()
    val back = icon("Back") { moveTo(15f,5f);lineTo(8f,12f);lineTo(15f,19f) }
    val chevron = icon("Details") { moveTo(9f,6f);lineTo(15f,12f);lineTo(9f,18f) }
    val refresh = icon("Refresh") { moveTo(20f,11f);curveTo(19f,5f,10f,1f,5f,7f);moveTo(4f,4f);lineTo(4f,9f);lineTo(9f,9f);moveTo(4f,13f);curveTo(5f,19f,14f,23f,19f,17f);moveTo(20f,20f);lineTo(20f,15f);lineTo(15f,15f) }
    val person = icon("Name") { moveTo(12f,11f);curveTo(17f,11f,17f,3f,12f,3f);curveTo(7f,3f,7f,11f,12f,11f);moveTo(4f,21f);lineTo(4f,18f);curveTo(4f,12f,20f,12f,20f,18f);lineTo(20f,21f) }
    val contact = icon("Contact") { moveTo(3f,5f);lineTo(21f,5f);lineTo(21f,19f);lineTo(3f,19f);close();moveTo(3f,6f);lineTo(12f,13f);lineTo(21f,6f) }
    val calendar = icon("Birthday") { moveTo(4f,5f);lineTo(20f,5f);lineTo(20f,21f);lineTo(4f,21f);close();moveTo(8f,3f);lineTo(8f,7f);moveTo(16f,3f);lineTo(16f,7f);moveTo(4f,11f);lineTo(20f,11f);moveTo(8f,15f);lineTo(10f,15f);moveTo(14f,15f);lineTo(16f,15f) }
    val gender = icon("Gender") { moveTo(12f,15f);curveTo(19f,15f,19f,4f,12f,4f);curveTo(5f,4f,5f,15f,12f,15f);moveTo(12f,15f);lineTo(12f,22f);moveTo(8f,19f);lineTo(16f,19f) }
    val lock = icon("Private") { moveTo(6f,11f);lineTo(18f,11f);lineTo(18f,21f);lineTo(6f,21f);close();moveTo(8f,11f);lineTo(8f,7f);curveTo(8f,1f,16f,1f,16f,7f);lineTo(16f,11f) }
}
