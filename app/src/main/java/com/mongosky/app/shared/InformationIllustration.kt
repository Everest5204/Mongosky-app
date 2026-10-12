package com.mongosky.app.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal data class InformationArtNode(
    val data: String, val fill: Brush?, val alpha: Float = 1f,
    val stroke: Brush? = null, val strokeWidth: Float = 1f,
    val roundCap: Boolean = false, val roundJoin: Boolean = false,
    val dash: List<Float> = emptyList()
)
internal data class InformationArt(val width: Float, val height: Float, val description: String, val nodes: List<InformationArtNode>)

/** Paths and strokes are prepared once per visible illustration, then drawn at native resolution. */
@Composable
internal fun InformationIllustration(art: InformationArt, modifier: Modifier = Modifier) {
    val paths = remember(art) { art.nodes.map { PathParser().parsePathString(it.data).toPath() } }
    val strokes = remember(art) { art.nodes.map {
        Stroke(width = it.strokeWidth, cap = if (it.roundCap) StrokeCap.Round else StrokeCap.Butt,
            join = if (it.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
            pathEffect = if (it.dash.isEmpty()) null else PathEffect.dashPathEffect(it.dash.toFloatArray()))
    } }
    Canvas(modifier.fillMaxWidth().aspectRatio(art.width / art.height).semantics { contentDescription = art.description }) {
        scale(size.width / art.width, size.height / art.height, pivot = Offset.Zero) {
            art.nodes.forEachIndexed { index, node ->
                node.fill?.let { drawPath(paths[index], it, alpha = node.alpha) }
                node.stroke?.let { drawPath(paths[index], it, alpha = node.alpha, style = strokes[index]) }
            }
        }
    }
}
