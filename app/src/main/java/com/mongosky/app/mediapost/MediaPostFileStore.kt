package com.mongosky.app.mediapost

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.mongosky.app.mediapost.MediaPostDraft
import com.mongosky.app.mediapost.MediaPostUploadLimits
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.mediapost.PreparedMediaPost
import com.mongosky.app.mediapost.PreparedPostMedia
import com.mongosky.app.mediapost.SelectedPostMedia
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaPostFileException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class MediaPostFileStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val resolver = context.applicationContext.contentResolver

    private val root = File(
        context.applicationContext.noBackupFilesDir,
        "media_post_uploads"
    )

    private val mutex = Mutex()

    suspend fun inspect(uris: List<Uri>): List<SelectedPostMedia> =
        withContext(ioDispatcher) {
            try {
                val uniqueUris = uris.distinctBy { it.toString() }

                if (uniqueUris.size > MediaPostUploadLimits.MAX_IMAGES) {
                    throw MediaPostFileException(
                        "You can select up to 10 photos."
                    )
                }

                val media = uniqueUris.map { uri ->
                    currentCoroutineContext().ensureActive()
                    inspectOne(uri)
                }

                MediaPostDraft("selection", "", media)
                    .validationError()?.let {
                        throw MediaPostFileException(it)
                    }

                media
            } catch (error: Exception) {
                throw fileError(error)
            }
        }

    suspend fun prepare(
        draft: MediaPostDraft,
        ownerUserId: String
    ): PreparedMediaPost {
        var createdFolder: File? = null

        try {
            return withContext(ioDispatcher) {
                mutex.withLock {
                    draft.validationError()?.let {
                        throw MediaPostFileException(it)
                    }

                    val folder = uploadFolder(ownerUserId, draft.id)
                    val parent = checkNotNull(folder.parentFile)

                    if (!parent.isDirectory && !parent.mkdirs()) {
                        throw MediaPostFileException(
                            "Could not create upload storage."
                        )
                    }

                    // Never overwrite an existing upload.
                    if (!folder.mkdir()) {
                        throw MediaPostFileException(
                            "Could not start this upload."
                        )
                    }

                    createdFolder = folder

                    val knownBytes = draft.media.sumOf {
                        it.sizeBytes ?: 0L
                    }

                    if (folder.usableSpace < knownBytes) {
                        throw MediaPostFileException(
                            "Not enough free space on your phone."
                        )
                    }

                    val buffer = ByteArray(COPY_BUFFER_BYTES)

                    val prepared = draft.media.mapIndexed { index, item ->
                        currentCoroutineContext().ensureActive()
                        copyMedia(item, folder, index, buffer)
                    }

                    PreparedMediaPost(
                        draftId = draft.id,
                        ownerUserId = ownerUserId,
                        caption = draft.caption.trim(),
                        media = prepared
                    )
                }
            }
        } catch (error: Throwable) {
            // Includes cancellation while returning from Dispatchers.IO.
            try {
                withContext(NonCancellable + ioDispatcher) {
                    mutex.withLock {
                        createdFolder?.let { deleteFolder(it) }
                    }
                }
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }

            if (error is Exception) throw fileError(error)
            throw error
        }
    }

    suspend fun validate(prepared: PreparedMediaPost) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val folder = uploadFolder(
                    prepared.ownerUserId,
                    prepared.draftId
                )

                if (prepared.media.isEmpty()) {
                    throw MediaPostFileException(
                        "No media is available for this upload."
                    )
                }

                for (item in prepared.media) {
                    currentCoroutineContext().ensureActive()

                    val file = File(item.localPath).canonicalFile

                    if (
                        file.parentFile != folder ||
                        !file.isFile ||
                        item.sizeBytes <= 0L ||
                        file.length() != item.sizeBytes ||
                        item.sizeBytes > item.kind.maxFileBytes
                    ) {
                        throw MediaPostFileException(
                            "Upload media is unavailable. Please select it again."
                        )
                    }
                }
            }
        }

    suspend fun delete(prepared: PreparedMediaPost) =
        withContext(ioDispatcher) {
            mutex.withLock {
                deleteFolder(
                    uploadFolder(
                        prepared.ownerUserId,
                        prepared.draftId
                    )
                )
            }
        }

    private fun inspectOne(uri: Uri): SelectedPostMedia {
        checkContentUri(uri)

        var name = ""
        var size: Long? = null

        resolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                val sizeIndex = cursor.getColumnIndex(
                    OpenableColumns.SIZE
                )

                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex).orEmpty()
                }

                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex).takeIf {
                        it >= 0L
                    }
                }
            }
        }

        name = name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace('\r', '_')
            .replace('\n', '_')
            .replace('\u0000', '_')
            .trim()
            .take(120)

        val extension = name
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)

        val inferredMime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)

        val providerMime = resolver.getType(uri)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)

        val mime = providerMime?.takeIf {
            mediaKind(it) != null
        } ?: inferredMime?.takeIf {
            mediaKind(it) != null
        } ?: throw MediaPostFileException(
            "Please select a photo or video."
        )

        val kind = checkNotNull(mediaKind(mime))

        return SelectedPostMedia(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            name = name.ifBlank {
                if (kind == PostMediaKind.IMAGE) "photo" else "video"
            },
            mimeType = mime,
            kind = kind,
            sizeBytes = size
        )
    }

    private suspend fun copyMedia(
        item: SelectedPostMedia,
        folder: File,
        index: Int,
        buffer: ByteArray
    ): PreparedPostMedia {
        val uri = Uri.parse(item.uri)
        checkContentUri(uri)

        if (mediaKind(item.mimeType) != item.kind) {
            throw MediaPostFileException(
                "This media format is unavailable."
            )
        }

        val target = File(folder, "media_$index.bin")
        val partial = File(folder, "media_$index.part")
        var copied = 0L

        val input = resolver.openInputStream(uri)
            ?: throw MediaPostFileException(
                "Could not open this media. Select it again."
            )

        input.use { source ->
            FileOutputStream(partial).use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()

                    val count = source.read(buffer)

                    if (count < 0) break
                    if (count == 0) continue

                    if (count.toLong() > item.kind.maxFileBytes - copied) {
                        throw MediaPostFileException(
                            if (item.kind == PostMediaKind.IMAGE) {
                                "Each photo must be 15 MB or smaller."
                            } else {
                                "The video must be 200 MB or smaller."
                            }
                        )
                    }

                    currentCoroutineContext().ensureActive()

                    output.write(buffer, 0, count)
                    copied += count
                }
            }
        }

        if (copied == 0L) {
            throw MediaPostFileException(
                "This file is empty. Select another file."
            )
        }

        currentCoroutineContext().ensureActive()

        if (!partial.renameTo(target)) {
            throw MediaPostFileException(
                "Could not finish preparing this media."
            )
        }

        return PreparedPostMedia(
            id = item.id,
            localPath = target.absolutePath,
            name = item.name,
            mimeType = item.mimeType,
            kind = item.kind,
            sizeBytes = copied
        )
    }

    private fun uploadFolder(
        ownerUserId: String,
        draftId: String
    ): File {
        if (
            !SAFE_ID.matches(ownerUserId) ||
            !SAFE_ID.matches(draftId)
        ) {
            throw MediaPostFileException(
                "Invalid upload session. Please try again."
            )
        }

        val folder = File(
            File(root, ownerUserId),
            draftId
        ).canonicalFile

        if (!folder.path.startsWith(root.canonicalPath + File.separator)) {
            throw MediaPostFileException(
                "Invalid upload storage location."
            )
        }

        return folder
    }

    private fun deleteFolder(folder: File) {
        if (folder.exists() && !folder.deleteRecursively()) {
            throw MediaPostFileException(
                "Could not clear temporary upload files."
            )
        }
    }

    private fun checkContentUri(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw MediaPostFileException(
                "Please select media using the photo picker."
            )
        }
    }

    private fun mediaKind(mime: String): PostMediaKind? = when {
        mime.startsWith("image/") -> PostMediaKind.IMAGE
        mime.startsWith("video/") -> PostMediaKind.VIDEO
        else -> null
    }

    private fun fileError(error: Exception): Exception = when (error) {
        is CancellationException -> error
        is MediaPostFileException -> error

        is SecurityException -> MediaPostFileException(
            "Media access has expired. Please select it again.",
            error
        )

        else -> MediaPostFileException(
            "Could not read or save the media. Check phone storage and try again.",
            error
        )
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024

        val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
