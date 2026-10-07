package com.mongosky.app.mediapost

import com.mongosky.app.auth.SessionApi
import com.mongosky.app.auth.SessionExpiredException
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostDraft
import com.mongosky.app.mediapost.MediaPostFileException
import com.mongosky.app.mediapost.MediaPostFileStore
import com.mongosky.app.mediapost.MediaPostUploadApi
import com.mongosky.app.mediapost.MediaPostUploadException
import com.mongosky.app.mediapost.MediaPostUploadProgress
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.mediapost.PreparedMediaPost
import com.mongosky.app.post.FeedApiFailure
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface MediaUploadFiles {
    suspend fun prepare(
        draft: MediaPostDraft,
        ownerUserId: String
    ): PreparedMediaPost

    suspend fun validate(prepared: PreparedMediaPost)

    suspend fun delete(prepared: PreparedMediaPost)
}

internal interface MediaUploadSession {
    suspend fun readToken(): String?

    suspend fun ownerUserId(token: String): String
}

internal fun interface MediaUploader {
    suspend fun upload(
        token: String,
        prepared: PreparedMediaPost,
        onProgress: (MediaPostUploadProgress) -> Unit,
        onPublishing: () -> Unit
    ): MediaPost
}

/**
 * The activity-scoped ViewModel owns the coroutine
 * running publish or retry.
 */
class MediaPostUploadRepository internal constructor(
    private val files: MediaUploadFiles,
    private val session: MediaUploadSession,
    private val uploader: MediaUploader
) {
    constructor(
        tokenStore: TokenStore,
        fileStore: MediaPostFileStore,
        uploadApi: MediaPostUploadApi = MediaPostUploadApi(),
        sessionApi: SessionApi = SessionApi()
    ) : this(
        files = object : MediaUploadFiles {
            override suspend fun prepare(
                draft: MediaPostDraft,
                ownerUserId: String
            ) = fileStore.prepare(draft, ownerUserId)

            override suspend fun validate(
                prepared: PreparedMediaPost
            ) {
                fileStore.validate(prepared)
            }

            override suspend fun delete(
                prepared: PreparedMediaPost
            ) {
                fileStore.delete(prepared)
            }
        },
        session = object : MediaUploadSession {
            override suspend fun readToken() = tokenStore.read()

            override suspend fun ownerUserId(token: String) =
                sessionApi.getSession(token).userId
        },
        uploader = MediaUploader(uploadApi::upload)
    )

    private val mutableState =
        MutableStateFlow<MediaPostUploadState>(
            MediaPostUploadState.Idle
        )

    val state: StateFlow<MediaPostUploadState> =
        mutableState.asStateFlow()

    private val gate = Mutex()
    private val generation = AtomicLong()
    private val cleanupQueue = LinkedHashSet<PreparedMediaPost>()

    private var prepared: PreparedMediaPost? = null
    private var boundToken: String? = null
    private var boundOwner: String? = null

    @Volatile
    private var retryNotBeforeNanos = 0L

    suspend fun publish(draft: MediaPostDraft) {
        if (!gate.tryLock()) return

        try {
            if (mutableState.value !== MediaPostUploadState.Idle) {
                return
            }

            val snapshot = draft.copy(
                caption = draft.caption.trim(),
                media = draft.media.toList()
            )

            attempt(snapshot)
        } finally {
            gate.unlock()
        }
    }

    suspend fun retry() {
        if (!gate.tryLock()) return

        try {
            val failed = mutableState.value
                    as? MediaPostUploadState.Failed
                ?: return

            if (
                !failed.canRetry ||
                failed.outcomeUnknown ||
                failed.requiresSignIn
            ) {
                return
            }

            val delaySeconds = retryDelaySeconds()

            if (delaySeconds > 0) {
                mutableState.value = failed.copy(
                    message =
                        "Please wait $delaySeconds seconds before retrying."
                )
                return
            }

            attempt(failed.draft)
        } finally {
            gate.unlock()
        }
    }

    fun retryDelaySeconds(): Int {
        val deadline = retryNotBeforeNanos

        if (deadline == 0L) return 0

        val remaining = (
                deadline - System.nanoTime()
                ).coerceAtLeast(0L)

        return (
                (remaining + NANOS_PER_SECOND - 1L) /
                        NANOS_PER_SECOND
                ).toInt()
    }

    // Home calls this after inserting the returned post into its feed.
    suspend fun acknowledgeSuccess(draftId: String) {
        gate.withLock {
            val success = mutableState.value
                    as? MediaPostUploadState.Succeeded
                ?: return

            if (success.draft.id == draftId) {
                reset()
            }
        }
    }

    /**
     * Cancel and join the ViewModel upload job
     * before clearing an active upload.
     */
    suspend fun clear() = withContext(NonCancellable) {
        gate.withLock {
            reset()
        }
    }

    private suspend fun attempt(draft: MediaPostDraft) {
        val version = generation.incrementAndGet()

        var httpAttempted = false
        var confirmed = false

        retryNotBeforeNanos = 0L
        mutableState.value = MediaPostUploadState.Preparing(draft)

        try {
            draft.validationError()?.let {
                throw MediaPostFileException(it)
            }

            flushCleanup()

            val token = readToken()

            if (boundToken != null && boundToken != token) {
                throw SessionChanged()
            }

            boundToken = token

            val owner = session.ownerUserId(token)
                .trim()
                .lowercase(Locale.ROOT)

            if (!OBJECT_ID.matches(owner)) {
                throw IOException("Invalid account response.")
            }

            if (boundOwner != null && boundOwner != owner) {
                throw SessionChanged()
            }

            boundOwner = owner

            val upload = prepared
                ?: files.prepare(draft, owner).also {
                    prepared = it
                }

            if (
                upload.draftId != draft.id ||
                upload.ownerUserId != owner
            ) {
                throw SessionChanged()
            }

            files.validate(upload)

            // The account could change while large files are copied.
            if (readToken() != token) {
                throw SessionChanged()
            }

            currentCoroutineContext().ensureActive()

            mutableState.value =
                MediaPostUploadState.Uploading(draft)

            httpAttempted = true

            val post = uploader.upload(
                token = token,
                prepared = upload,
                onProgress = { progress ->
                    mutableState.update { current ->
                        if (
                            generation.get() == version &&
                            current is MediaPostUploadState.Uploading &&
                            current.draft.id == draft.id
                        ) {
                            current.copy(progress = progress)
                        } else {
                            current
                        }
                    }
                },
                onPublishing = {
                    mutableState.update { current ->
                        if (
                            generation.get() == version &&
                            current is MediaPostUploadState.Uploading &&
                            current.draft.id == draft.id
                        ) {
                            MediaPostUploadState.Publishing(draft)
                        } else {
                            current
                        }
                    }
                }
            )

            if (!post.userId.equals(owner, ignoreCase = true)) {
                throw MediaPostUploadException(
                    UNKNOWN_RESULT,
                    FeedApiFailure.INVALID_RESPONSE,
                    outcomeUnknown = true
                )
            }

            confirmed = true

            mutableState.value =
                MediaPostUploadState.Succeeded(draft, post)

            retireFiles()
        } catch (error: CancellationException) {
            if (!confirmed) {
                mutableState.value =
                    MediaPostUploadState.Failed(
                        draft = draft,
                        message = if (httpAttempted) {
                            UNKNOWN_RESULT
                        } else {
                            "Upload was cancelled."
                        },
                        canRetry = !httpAttempted,
                        outcomeUnknown = httpAttempted
                    )
            }

            throw error
        } catch (error: MediaPostUploadException) {
            if (error.failure == FeedApiFailure.RATE_LIMITED) {
                val seconds = (
                        error.retryAfterSeconds ?: 5
                        ).coerceIn(1, 3_600)

                retryNotBeforeNanos =
                    System.nanoTime() + seconds * NANOS_PER_SECOND
            }

            mutableState.value = MediaPostUploadState.Failed(
                draft = draft,
                message = error.message
                    ?: "Could not publish this post.",
                canRetry = error.canRetry &&
                        !error.outcomeUnknown &&
                        !error.requiresSignIn,
                requiresSignIn = error.requiresSignIn,
                outcomeUnknown = error.outcomeUnknown
            )
        } catch (_: SessionExpiredException) {
            mutableState.value = MediaPostUploadState.Failed(
                draft = draft,
                message =
                    "Your session has expired. Please sign in again.",
                canRetry = false,
                requiresSignIn = true
            )
        } catch (_: SessionChanged) {
            mutableState.value = MediaPostUploadState.Failed(
                draft = draft,
                message =
                    "Your sign-in session changed. Please create a new post.",
                canRetry = false
            )
        } catch (error: MediaPostFileException) {
            mutableState.value = MediaPostUploadState.Failed(
                draft = draft,
                message = error.message
                    ?: "Please select the media again.",
                canRetry = false
            )
        } catch (_: IOException) {
            mutableState.value = MediaPostUploadState.Failed(
                draft = draft,
                message = if (httpAttempted) {
                    UNKNOWN_RESULT
                } else {
                    "Could not prepare your upload. Check your connection and phone storage, then retry."
                },
                canRetry = !httpAttempted,
                outcomeUnknown = httpAttempted
            )
        } catch (_: Exception) {
            if (!confirmed) {
                mutableState.value = MediaPostUploadState.Failed(
                    draft = draft,
                    message = if (httpAttempted) {
                        UNKNOWN_RESULT
                    } else {
                        "Could not prepare this post."
                    },
                    canRetry = false,
                    outcomeUnknown = httpAttempted
                )
            }
        }
    }

    private suspend fun readToken(): String {
        val token = session.readToken()?.trim()

        if (
            token.isNullOrEmpty() ||
            token.any { it <= ' ' || it >= '\u007f' }
        ) {
            throw SessionExpiredException()
        }

        return token
    }

    private suspend fun reset() {
        generation.incrementAndGet()

        boundToken = null
        boundOwner = null
        retryNotBeforeNanos = 0L

        mutableState.value = MediaPostUploadState.Idle

        retireFiles()
    }

    private suspend fun retireFiles() {
        prepared?.let {
            cleanupQueue.add(it)
        }

        prepared = null

        flushCleanup()
    }

    private suspend fun flushCleanup() =
        withContext(NonCancellable) {
            val iterator = cleanupQueue.iterator()

            while (iterator.hasNext()) {
                try {
                    files.delete(iterator.next())
                    iterator.remove()
                } catch (_: Exception) {
                    // Keep failed cleanup entries for another attempt.
                    // A published post must remain successful.
                }
            }
        }

    private class SessionChanged : Exception()

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L

        const val UNKNOWN_RESULT =
            "Could not confirm whether your post was published. Check your posts before trying again."

        val OBJECT_ID = Regex("[a-fA-F0-9]{24}")
    }
}
