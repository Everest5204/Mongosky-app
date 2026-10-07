package com.mongosky.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.mongosky.app.profile.ProfileImageKind
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Copies once, samples before decoding, fixes camera orientation and strips metadata. */
class ProfileImagePreparer(context: Context) {
    private val context = context.applicationContext
    suspend fun prepare(uri: String, kind: ProfileImageKind): File = withContext(Dispatchers.IO) {
        val selected = Uri.parse(uri)
        require(selected.scheme == "content" || selected.scheme == "file")
        val mime = context.contentResolver.getType(selected)
        if (mime != null && !mime.startsWith("image/")) throw IOException("Choose an image file.")
        val folder = File(context.cacheDir, "own-profile-images").apply { if (!mkdirs() && !isDirectory) throw IOException("Image storage unavailable.") }
        val cutoff = System.currentTimeMillis() - 86_400_000
        folder.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val original = File.createTempFile("input-", ".image", folder)
        val output = File.createTempFile("upload-", ".jpg", folder)
        val bitmaps = linkedSetOf<Bitmap>()
        var complete = false
        try {
            val input = context.contentResolver.openInputStream(selected) ?: throw IOException("Could not open this image. Choose it again.")
            input.use { source -> original.outputStream().use { target ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 20L * 1024 * 1024) throw IOException("Choose an image smaller than 20 MB.")
                    target.write(buffer, 0, count)
                }
            } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("This image format is not supported on this phone.")
            var sample = 1
            while (bounds.outWidth.toLong() / sample * (bounds.outHeight.toLong() / sample) > 4_194_304 ||
                max(bounds.outWidth, bounds.outHeight) / sample > kind.maxEdge * 2) sample *= 2
            currentCoroutineContext().ensureActive()
            val decoded = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IOException("Could not decode this image.")
            bitmaps.add(decoded)
            val orientation = runCatching { ExifInterface(original.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            bitmaps.add(oriented)
            val scale = minOf(1f, kind.maxEdge.toFloat() / max(oriented.width, oriented.height))
            val width = (oriented.width * scale).roundToInt().coerceAtLeast(1)
            val height = (oriented.height * scale).roundToInt().coerceAtLeast(1)
            val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmaps.add(flattened)
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(oriented, null, android.graphics.Rect(0, 0, width, height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            currentCoroutineContext().ensureActive()
            output.outputStream().use { if (!flattened.compress(Bitmap.CompressFormat.JPEG, 86, it)) throw IOException("Could not prepare this image.") }
            if (output.length() !in 1..8_388_608) throw IOException("Choose a smaller image.")
            currentCoroutineContext().ensureActive()
            complete = true
            output
        } finally {
            original.delete()
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            if (!complete) output.delete()
        }
    }
}
