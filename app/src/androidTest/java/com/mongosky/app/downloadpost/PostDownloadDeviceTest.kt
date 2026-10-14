package com.mongosky.app.downloadpost

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mongosky.app.textpost.TextPostPreset
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device verification for real Canvas, bitmap/video decoding and MediaStore. No network required. */
@RunWith(AndroidJUnit4::class)
class PostDownloadDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun temp(name: String) = File(context.cacheDir, "download-device-$name")
    @Test fun nativeTextPngKeepsBanglaEmojiAndLongContentWithinTheCanvas() = runBlocking {
        for (style in listOf("BOLD_CENTER", "NORMAL_LEFT")) {
            val output = temp("$style.png")
            try {
                PostDownloadTextRenderer().render(DownloadText("বাংলা 😊 ".repeat(65), "SOLID", "#ff0000", "#ffffff", style), null, output)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(output.path))
                try { assertEquals(1080, bitmap.width); assertTrue(bitmap.height in 1080..2160) } finally { bitmap.recycle() }
            } finally { output.delete() }
        }
    }
    @Test fun allNativeGradientPresetsExportAsReadableImages() = runBlocking {
        for (preset in TextPostPreset.entries.filter { it.background.kind.name == "GRADIENT" }) {
            val output = temp("gradient.png")
            try {
                PostDownloadTextRenderer().render(DownloadText("Mongosky", "GRADIENT", preset.background.value, "#ffffff", "BOLD_CENTER"), null, output)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(output.path)); assertEquals(1080, bitmap.width); bitmap.recycle()
            } finally { output.delete() }
        }
    }
    @Test fun realGalleryPngRemainsPendingUntilPublished() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val output = temp("gallery.png"); val gallery = PostDownloadGallery(context); var rows = emptyList<SavedMedia>()
        try {
            val file = PostDownloadTextRenderer().render(DownloadText("বাংলা Gallery", "PLAIN", "", "#111827", "NORMAL_LEFT"), null, output)
            val name = "Mongosky_000000000000000000000001_${java.util.UUID.randomUUID()}_1.png"
            rows = listOf(gallery.allocate(file, name)); gallery.copy(output, rows.single())
            assertFalse(gallery.exists(rows))
            context.contentResolver.query(Uri.parse(rows.single().uri), arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            rows = gallery.publish(rows); assertTrue(gallery.exists(rows))
            context.contentResolver.openInputStream(Uri.parse(rows.single().uri))!!.use { assertArrayEquals(output.readBytes(), it.readBytes()) }
        } finally { gallery.remove(rows); output.delete() }
    }
    @Test fun nativeVideoIsPublishedToTheMoviesGalleryCollection() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val output = temp("video.mp4"); val gallery = PostDownloadGallery(context); var rows = emptyList<SavedMedia>()
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("Mongosky_download_test.mp4").use { input -> output.outputStream().use { input.copyTo(it) } }
            rows = listOf(gallery.allocate(DownloadedFile(output, MediaFormat("video/mp4", "mp4", true)), "Mongosky_000000000000000000000001_${java.util.UUID.randomUUID()}_1.mp4"))
            gallery.copy(output, rows.single()); rows = gallery.publish(rows)
            assertTrue(rows.single().uri.contains("/video/media/")); assertTrue(gallery.exists(rows))
        } finally { gallery.remove(rows); output.delete() }
    }
}
