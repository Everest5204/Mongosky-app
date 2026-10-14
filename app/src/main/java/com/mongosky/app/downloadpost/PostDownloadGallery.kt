package com.mongosky.app.downloadpost

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.*

internal interface DownloadGallery {
    suspend fun allocate(file: DownloadedFile, name: String): SavedMedia
    suspend fun copy(file: File, row: SavedMedia)
    suspend fun publish(rows: List<SavedMedia>): List<SavedMedia>
    suspend fun remove(rows: List<SavedMedia>)
    suspend fun exists(rows: List<SavedMedia>): Boolean
}
/** Pending files are hidden; cleanup only touches this app's exact, recorded media rows. */
internal class PostDownloadGallery(private val context: Context) : DownloadGallery {
    private val resolver get() = context.contentResolver
    override suspend fun allocate(file: DownloadedFile, name: String): SavedMedia = withContext(Dispatchers.IO) {
        if (!file.format.video) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw DownloadException("This photo could not be opened.")
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.file.absolutePath)
                if ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) <= 0 ||
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) <= 0 ||
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0) <= 0) throw DownloadException("This video could not be opened.")
            } finally { retriever.release() }
        }
        if (Build.VERSION.SDK_INT < 29) {
            val dir = legacyDirectory(file.format.mime)
            if (!dir.isDirectory && !dir.mkdirs()) throw DownloadException("Could not open your Gallery folder.")
            val path = File(dir, name)
            if (path.exists() || pending(path).exists()) throw DownloadException("This filename already exists.")
            return@withContext SavedMedia(Uri.fromFile(path).toString(), name, file.format.mime, file.file.length())
        }
        val collection = if (file.format.video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, file.format.mime)
            put("datetaken", System.currentTimeMillis())
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (file.format.video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Mongosky")
            put(MediaStore.MediaColumns.IS_PENDING, 1); put(MediaStore.MediaColumns.DATE_EXPIRES, System.currentTimeMillis() / 1000 + 86400)
        }
        val uri = resolver.insert(collection, values) ?: throw DownloadException("Could not create this Gallery file.")
        SavedMedia(uri.toString(), name, file.format.mime, file.file.length())
    }
    override suspend fun copy(file: File, row: SavedMedia) = withContext(Dispatchers.IO) {
        val uri = owned(row) ?: throw DownloadException("Could not open this Gallery file.")
        val output = if (uri.scheme == "file") pending(File(requireNotNull(uri.path))).outputStream() else resolver.openOutputStream(uri, "w")
        output?.use { out -> file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
        } } ?: throw DownloadException("Could not write this Gallery file.")
    }
    override suspend fun publish(rows: List<SavedMedia>): List<SavedMedia> = withContext(Dispatchers.IO) {
        rows.map { row ->
            ensureActive(); val uri = owned(row) ?: throw DownloadException("The Gallery file is unavailable.")
            if (uri.scheme == "file") {
                val path = File(requireNotNull(uri.path))
                if (!path.exists() && !pending(path).renameTo(path)) throw DownloadException("Could not finish saving to Gallery.")
                val scanned = withTimeout(15_000) { suspendCancellableCoroutine<Uri> { continuation ->
                    MediaScannerConnection.scanFile(context, arrayOf(path.absolutePath), arrayOf(row.mime)) { _, result ->
                        if (continuation.isActive) continuation.resumeWith(if (result == null) Result.failure(DownloadException("Gallery indexing failed. Please retry.")) else Result.success(result))
                    }
                } }
                row.copy(uri = scanned.toString())
            } else {
                if (Build.VERSION.SDK_INT >= 29 && resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0); putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                }, null, null) != 1) throw DownloadException("Could not finish saving to Gallery.")
                row
            }
        }
    }
    override suspend fun remove(rows: List<SavedMedia>) = withContext(Dispatchers.IO) {
        rows.forEach { row -> runCatching { owned(row)?.let {
            if (it.scheme == "file") File(requireNotNull(it.path)).let { file ->
                val collection = if (row.mime.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                runCatching { resolver.delete(collection, "${MediaStore.MediaColumns.DATA} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(file.absolutePath, row.name)) }
                pending(file).delete(); file.delete()
            } else resolver.delete(it, null, null)
        } } }
    }
    override suspend fun exists(rows: List<SavedMedia>): Boolean = withContext(Dispatchers.IO) {
        rows.isNotEmpty() && rows.all { row -> runCatching {
            val uri = owned(row) ?: return@runCatching false
            if (uri.scheme == "file") return@runCatching File(requireNotNull(uri.path)).let { it.isFile && it.length() == row.size }
            if (Build.VERSION.SDK_INT >= 29 && resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)?.use {
                it.moveToFirst() && it.getInt(0) == 0 } != true) return@runCatching false
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize == row.size && row.size > 0 } == true
        }.getOrDefault(false) }
    }
    private fun owned(row: SavedMedia): Uri? {
        val uri = Uri.parse(row.uri)
        if (!row.name.matches(Regex("Mongosky_[a-f0-9]{24}_[a-f0-9-]{36}_[0-9]+\\.[a-z0-9]+"))) return null
        if (Build.VERSION.SDK_INT < 29 && uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            return uri.takeIf { file.name == row.name && file.canonicalFile.parentFile == legacyDirectory(row.mime).canonicalFile }
        }
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return null
        val columns = if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.OWNER_PACKAGE_NAME) else arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return resolver.query(uri, columns, null, null, null)?.use {
            if (it.moveToFirst() && it.getString(0) == row.name && (Build.VERSION.SDK_INT < 29 || it.getString(1) == context.packageName)) uri else null
        }
    }
    private fun legacyDirectory(mime: String) = File(Environment.getExternalStoragePublicDirectory(if (mime.startsWith("video/")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "Mongosky")
    private fun pending(file: File) = File(file.parentFile, ".${file.name}.pending")
}
