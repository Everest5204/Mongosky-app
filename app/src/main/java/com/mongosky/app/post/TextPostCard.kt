package com.mongosky.app.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.post.FeedTextBackgroundKind
import com.mongosky.app.post.FeedTextStyle
import com.mongosky.app.reactions.PostReactionState
import com.mongosky.app.textpost.TextPost
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun TextPostCard(post: TextPost, now: Instant, reaction: PostReactionState, commentsCount: Long,
    enabled: Boolean, actions: PostCardActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Color.White)) {
        PostHeader(post.author, post.createdAt, now, actions)
        val centered = post.textStyle == FeedTextStyle.BOLD_CENTER || post.textStyle == FeedTextStyle.NORMAL_CENTER
        val bold = post.textStyle == FeedTextStyle.BOLD_CENTER || post.textStyle == FeedTextStyle.BOLD_LEFT
        val length = post.displayText.codePointCount(0, post.displayText.length)
        val fontSize = if (centered) when { length > 260 -> 19; length > 150 -> 24; else -> 30 }
            else when { length > 260 -> 17; length > 150 -> 19; else -> 22 }
        val textColor = remember(post.textColor) { cssColor(post.textColor, FeedColors.text) }
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment =
            if (centered) Alignment.Center else Alignment.CenterStart) {
            TextPostBackground(post.textBackground.kind, post.textBackground.value, Modifier.matchParentSize())
            Text(post.displayText, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
        PostFooter(reaction, commentsCount, enabled, actions)
    }
}

@Composable
internal fun TextPostBackground(kind: FeedTextBackgroundKind, value: String, modifier: Modifier) {
    val layers = remember(kind, value) { if (kind == FeedTextBackgroundKind.GRADIENT) parseGradient(value) else emptyList() }
    Box(modifier.background(if (kind == FeedTextBackgroundKind.SOLID) cssColor(value, Color.White) else Color.White)) {
        if (kind == FeedTextBackgroundKind.IMAGE && value.startsWith("https://")) {
            AsyncImage(model = value, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (layers.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            layers.asReversed().forEach { layer ->
                val brush = if (layer.radial) {
                    val center = Offset(size.width * layer.x, size.height * layer.y)
                    val radius = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y)).coerceAtLeast(1f)
                    Brush.radialGradient(*layer.stops.toTypedArray(), center = center, radius = radius)
                } else {
                    val radians = Math.toRadians(layer.angle.toDouble())
                    val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
                    val extent = (abs(size.width * direction.x) + abs(size.height * direction.y)) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    Brush.linearGradient(*layer.stops.toTypedArray(), start = center - direction * extent, end = center + direction * extent)
                }
                drawRect(brush)
            }
        }
    }
}

private data class GradientLayer(val radial: Boolean, val stops: List<Pair<Float, Color>>, val angle: Float = 135f,
    val x: Float = 0.5f, val y: Float = 0.5f)

/** Supports the actual hex-colour linear and radial presets in CreateTextPost. */
private fun parseGradient(value: String): List<GradientLayer> =
    Regex("(linear|radial)-gradient\\(([^()]*)\\)").findAll(value).mapNotNull { match ->
        val body = match.groupValues[2]
        val colors = Regex("(#[0-9a-fA-F]{3,8}|transparent)(?:\\s+([0-9.]+)%)?").findAll(body).toList()
        if (colors.size < 2) return@mapNotNull null
        val stops = colors.mapIndexed { index, stop ->
            val position = stop.groupValues[2].toFloatOrNull()?.div(100f) ?: index.toFloat() / (colors.size - 1)
            position.coerceIn(0f, 1f) to if (stop.groupValues[1] == "transparent") Color.Transparent else cssColor(stop.groupValues[1], Color.White)
        }.sortedBy { it.first }
        val position = Regex("at\\s+([0-9.]+)%\\s+([0-9.]+)%").find(body)
        GradientLayer(match.groupValues[1] == "radial", stops,
            Regex("([0-9.]+)deg").find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: 135f,
            position?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 0.5f,
            position?.groupValues?.get(2)?.toFloatOrNull()?.div(100f) ?: 0.5f)
    }.toList()

internal fun cssColor(value: String, fallback: Color): Color = try {
    val color = value.trim()
    val normalized = when (color.length) {
        4 -> if (color.startsWith("#")) "#" + color.drop(1).flatMap { listOf(it, it) }.joinToString("") else color
        9 -> if (color.startsWith("#")) "#${color.takeLast(2)}${color.substring(1, 7)}" else color
        else -> color
    }
    Color(android.graphics.Color.parseColor(normalized))
} catch (_: IllegalArgumentException) { fallback }
