package com.mongosky.app.downloadpost

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlinx.coroutines.*
import kotlin.math.*

internal interface DownloadTextRenderer { suspend fun render(text: DownloadText, background: File?, output: File): DownloadedFile }
/** Native Canvas exports the complete text and its preset background to a bounded PNG. */
internal class PostDownloadTextRenderer : DownloadTextRenderer {
    override suspend fun render(text: DownloadText, background: File?, output: File): DownloadedFile = withContext(Dispatchers.Default) {
        ensureActive()
        val centered = text.style.endsWith("CENTER"); val length = text.text.codePointCount(0, text.text.length)
        val size = if (centered) when { length > 260 -> 58f; length > 150 -> 72f; else -> 90f }
            else when { length > 260 -> 51f; length > 150 -> 58f; else -> 66f }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(text.color, Color.rgb(17, 24, 39)); textSize = size
            typeface = if (text.style.startsWith("BOLD")) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        var layout = layout(text.text, paint, centered)
        while (layout.height > 1900 && paint.textSize > 34f) { paint.textSize -= 2f; layout = layout(text.text, paint, centered) }
        if (layout.height > 1900) throw DownloadException("This text post is too long to export.")
        val height = max(1080, layout.height + 144).coerceAtMost(2160)
        val image = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(if (text.kind == "SOLID") color(text.background, Color.WHITE) else Color.WHITE)
            if (text.kind == "GRADIENT") drawGradients(canvas, text.background, 1080f, height.toFloat())
            if (text.kind == "IMAGE") drawBackground(canvas, requireNotNull(background), 1080, height)
            canvas.save(); canvas.translate(72f, (height - layout.height) / 2f); layout.draw(canvas); canvas.restore()
            ensureActive()
            withContext(Dispatchers.IO) { output.outputStream().buffered().use {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, it)) throw DownloadException("Could not export this text post.")
            } }
            ensureActive(); DownloadedFile(output, MediaFormat("image/png", "png", false))
        } catch (error: Throwable) { output.delete(); throw error } finally { image.recycle() }
    }
    private fun layout(text: String, paint: TextPaint, centered: Boolean) = StaticLayout.Builder.obtain(text, 0, text.length, paint, 936)
        .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        .setLineSpacing(0f, 1.4f).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build()
    private fun drawBackground(canvas: Canvas, file: File, width: Int, height: Int) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) throw DownloadException("This background image is unavailable.")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        while (bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 4_000_000) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw DownloadException("This background image is unavailable.")
        try {
            val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val left = (width - bitmap.width * scale) / 2f; val top = (height - bitmap.height * scale) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally { bitmap.recycle() }
    }
    private fun drawGradients(canvas: Canvas, value: String, width: Float, height: Float) {
        val layers = Regex("(linear|radial)-gradient\\(([^()]*)\\)").findAll(value).toList()
        if (layers.isEmpty()) throw DownloadException("This text background could not be exported.")
        for (match in layers.asReversed()) {
            val body = match.groupValues[2]; val stops = Regex("(#[0-9a-fA-F]{3,8}|transparent)(?:\\s+([0-9.]+)%)?").findAll(body).toList()
            if (stops.size < 2) throw DownloadException("This text background could not be exported.")
            val pairs = stops.mapIndexed { i, stop ->
                (stop.groupValues[2].toFloatOrNull()?.div(100f) ?: i.toFloat() / (stops.size - 1)).coerceIn(0f, 1f) to
                    if (stop.groupValues[1] == "transparent") Color.TRANSPARENT else color(stop.groupValues[1], Color.WHITE)
            }.sortedBy { it.first }
            val colors = pairs.map { it.second }.toIntArray(); val positions = pairs.map { it.first }.toFloatArray()
            val shader = if (match.groupValues[1] == "radial") {
                val at = Regex("at\\s+([0-9.]+)%\\s+([0-9.]+)%").find(body)
                val x = width * (at?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 0.5f)
                val y = height * (at?.groupValues?.get(2)?.toFloatOrNull()?.div(100f) ?: 0.5f)
                RadialGradient(x, y, hypot(max(x, width - x), max(y, height - y)), colors, positions, Shader.TileMode.CLAMP)
            } else {
                val angle = Math.toRadians(Regex("([0-9.]+)deg").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 135.0)
                val x = sin(angle).toFloat(); val y = -cos(angle).toFloat(); val extent = (abs(width * x) + abs(height * y)) / 2f
                LinearGradient(width / 2 - x * extent, height / 2 - y * extent, width / 2 + x * extent, height / 2 + y * extent, colors, positions, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width, height, Paint().apply { this.shader = shader })
        }
    }
    private fun color(value: String, fallback: Int): Int = runCatching {
        val v = value.trim()
        Color.parseColor(when { v.length == 4 && v.startsWith("#") -> "#" + v.drop(1).flatMap { listOf(it, it) }.joinToString("")
            v.length == 9 && v.startsWith("#") -> "#${v.takeLast(2)}${v.substring(1, 7)}"; else -> v })
    }.getOrDefault(fallback)
}
