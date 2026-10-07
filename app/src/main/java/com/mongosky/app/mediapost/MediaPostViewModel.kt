package com.mongosky.app.mediapost

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.mediapost.MediaPostDraft
import com.mongosky.app.mediapost.MediaPostFileException
import com.mongosky.app.mediapost.MediaPostFileStore
import com.mongosky.app.mediapost.MediaPostUploadLimits
import com.mongosky.app.mediapost.MediaPostUploadRepository
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.mediapost.SelectedPostMedia
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaPostComposerState(
    val caption: String = "",
    val media: List<SelectedPostMedia> = emptyList(),
    val activeIndex: Int = 0,
    val isSelecting: Boolean = false,
    val isResetting: Boolean = false,
    val error: String? = null
) {
    val activeMedia: SelectedPostMedia?
        get() = media.getOrNull(activeIndex)

    val canPost: Boolean
        get() = media.isNotEmpty() && !isSelecting && !isResetting

    val canAddMore: Boolean
        get() = !isSelecting &&
                !isResetting &&
                media.isNotEmpty() &&
                media.all { it.kind == PostMediaKind.IMAGE } &&
                media.size < MediaPostUploadLimits.MAX_IMAGES
}

/** Scope this ViewModel to the activity, shared by the composer and Home. */
@MainThread
class MediaPostViewModel internal constructor(
    private val repository: MediaPostUploadRepository,
    private val inspectMedia: suspend (List<Uri>) -> List<SelectedPostMedia>,
    private val cleanupDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    constructor(
        repository: MediaPostUploadRepository,
        fileStore: MediaPostFileStore
    ) : this(repository, fileStore::inspect)

    private val mutableComposerState = MutableStateFlow(
        MediaPostComposerState()
    )

    val composerState: StateFlow<MediaPostComposerState> =
        mutableComposerState.asStateFlow()

    private val mutableUploadState =
        MutableStateFlow<MediaPostUploadState>(MediaPostUploadState.Idle)

    val uploadState: StateFlow<MediaPostUploadState> =
        mutableUploadState.asStateFlow()

    private var selectionJob: Job? = null
    private var uploadJob: Job? = null
    private var maintenanceJob: Job? = null

    private var selectionVersion = 0L
    private var sessionStarted = false
    private var cleared = false

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.state.collect { showUploadState(it) }
        }
    }

    fun startSession() {
        if (cleared || !viewModelScope.isActive) return

        sessionStarted = true
        showUploadState()
    }

    /** Call on sign out, but never when only closing the composer or rotating. */
    fun endSession() {
        if (cleared) return

        sessionStarted = false
        cancelSelection()

        mutableComposerState.value = MediaPostComposerState(
            isResetting = maintenanceJob?.isActive == true
        )

        showUploadState()

        if (maintenanceJob?.isActive != true) {
            resetUpload()
        }
    }

    fun updateCaption(caption: String) {
        if (!canEditComposer()) return

        mutableComposerState.update {
            it.copy(caption = caption, error = null)
        }
    }

    /** Empty picker results are cancellations, so the existing draft is kept. */
    fun selectMedia(
        uris: List<Uri>,
        append: Boolean = false
    ) {
        if (!canEditComposer() || uris.isEmpty()) return

        val current = mutableComposerState.value

        if (
            append &&
            current.media.any { it.kind != PostMediaKind.IMAGE }
        ) {
            showComposerError(
                "You can add more photos only to a photo post."
            )
            return
        }

        val existingUris = if (append) {
            current.media.map { it.uri }.toSet()
        } else {
            emptySet()
        }

        val selection = uris
            .distinctBy { it.toString() }
            .filterNot { it.toString() in existingUris }

        if (selection.isEmpty()) return

        val count = selection.size +
                if (append) current.media.size else 0

        if (count > MediaPostUploadLimits.MAX_IMAGES) {
            showComposerError("You can select up to 10 photos.")
            return
        }

        cancelSelection()
        val version = selectionVersion

        mutableComposerState.update {
            it.copy(isSelecting = true, error = null)
        }

        selectionJob = viewModelScope.launch {
            try {
                val selected = inspectMedia(selection)
                ensureActive()

                if (
                    version != selectionVersion ||
                    !canEditComposer()
                ) {
                    return@launch
                }

                if (
                    append &&
                    selected.any { it.kind != PostMediaKind.IMAGE }
                ) {
                    throw MediaPostFileException(
                        "You can add only photos to this post."
                    )
                }

                val latest = mutableComposerState.value

                val existing = if (append) {
                    latest.media
                } else {
                    emptyList()
                }

                val combined = (existing + selected)
                    .distinctBy { it.uri }

                MediaPostDraft(
                    id = "selection",
                    caption = latest.caption,
                    media = combined
                ).validationError()?.let {
                    throw MediaPostFileException(it)
                }

                val index = if (
                    append &&
                    combined.size > existing.size
                ) {
                    existing.size
                } else if (append) {
                    latest.activeIndex
                } else {
                    0
                }

                mutableComposerState.value = latest.copy(
                    media = combined.toList(),
                    activeIndex = index,
                    isSelecting = false,
                    error = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: MediaPostFileException) {
                if (version == selectionVersion) {
                    showComposerError(
                        error.message ?: "Please select the media again."
                    )
                }
            } catch (_: Exception) {
                if (version == selectionVersion) {
                    showComposerError(
                        "Could not read this media. Please select it again."
                    )
                }
            } finally {
                if (version == selectionVersion) {
                    mutableComposerState.update {
                        it.copy(isSelecting = false)
                    }
                }
            }
        }
    }

    fun showMedia(index: Int) {
        if (!canEditComposer()) return

        mutableComposerState.update {
            if (index in it.media.indices) {
                it.copy(activeIndex = index)
            } else {
                it
            }
        }
    }

    fun removeMedia(id: String) {
        if (
            !canEditComposer() ||
            mutableComposerState.value.isSelecting
        ) {
            return
        }

        val current = mutableComposerState.value
        val removedIndex = current.media.indexOfFirst {
            it.id == id
        }

        if (removedIndex < 0) return

        val remaining = current.media.filterIndexed { index, _ ->
            index != removedIndex
        }

        val activeIndex = when {
            remaining.isEmpty() -> 0
            removedIndex < current.activeIndex ->
                current.activeIndex - 1

            else -> current.activeIndex.coerceAtMost(
                remaining.lastIndex
            )
        }

        mutableComposerState.value = current.copy(
            media = remaining,
            activeIndex = activeIndex,
            error = null
        )
    }

    /** Closing the composer does not cancel a submitted upload. */
    fun discardComposer() {
        cancelSelection()

        mutableComposerState.value = MediaPostComposerState(
            isResetting = mutableComposerState.value.isResetting
        )
    }

    fun dismissComposerError() {
        mutableComposerState.update {
            it.copy(error = null)
        }
    }

    /** True means the UI can close the composer and navigate to Home immediately. */
    fun submit(): Boolean {
        if (!canAcceptActions()) return false

        if (!canEditComposer()) {
            showComposerError(
                "Finish or dismiss your previous upload first."
            )
            return false
        }

        val current = mutableComposerState.value

        if (current.isSelecting) {
            showComposerError(
                "Please wait while your media is being checked."
            )
            return false
        }

        val draft = MediaPostDraft(
            id = UUID.randomUUID().toString(),
            caption = current.caption.trim(),
            media = current.media.toList()
        )

        draft.validationError()?.let {
            showComposerError(it)
            return false
        }

        cancelSelection()

        mutableComposerState.value = MediaPostComposerState()
        mutableUploadState.value = MediaPostUploadState.Preparing(draft)

        // Repository sets its initial state before its first suspension.
        uploadJob = viewModelScope.launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            repository.publish(draft)
        }

        showUploadState()
        return true
    }

    fun retryUpload() {
        if (
            !canAcceptActions() ||
            uploadJob?.isActive == true
        ) {
            return
        }

        val failed = repository.state.value
                as? MediaPostUploadState.Failed ?: return

        if (
            !failed.canRetry ||
            failed.outcomeUnknown ||
            failed.requiresSignIn
        ) {
            return
        }

        uploadJob = viewModelScope.launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            repository.retry()
        }

        showUploadState()
    }

    fun retryDelaySeconds(): Int =
        repository.retryDelaySeconds()

    /** A response lost after sending must be checked before creating another post. */
    fun restoreFailedDraft(): Boolean {
        if (!canAcceptActions()) return false

        val failed = repository.state.value
                as? MediaPostUploadState.Failed ?: return false

        if (failed.outcomeUnknown || failed.requiresSignIn) {
            return false
        }

        resetUpload(failed.draft)
        return true
    }

    fun dismissFailedUpload() {
        if (
            !canAcceptActions() ||
            repository.state.value !is MediaPostUploadState.Failed
        ) {
            return
        }

        resetUpload()
    }

    /** Home must consume the returned post before calling this method. */
    fun acknowledgeSuccess(draftId: String) {
        if (!canAcceptActions()) return

        val success = repository.state.value
                as? MediaPostUploadState.Succeeded ?: return

        if (success.draft.id != draftId) return

        mutableComposerState.update {
            it.copy(isResetting = true)
        }

        showUploadState()

        maintenanceJob = viewModelScope.launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            try {
                repository.acknowledgeSuccess(draftId)
            } finally {
                finishReset()
            }
        }
    }

    private fun resetUpload(
        draft: MediaPostDraft? = null
    ) {
        cancelSelection()
        val pendingUpload = uploadJob

        mutableComposerState.value = MediaPostComposerState(
            caption = draft?.caption.orEmpty(),
            media = draft?.media?.toList().orEmpty(),
            isResetting = true
        )

        showUploadState()

        maintenanceJob = viewModelScope.launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            try {
                pendingUpload?.cancelAndJoin()
                repository.clear()
            } finally {
                finishReset()
            }
        }
    }

    private fun finishReset() {
        if (cleared) return

        mutableComposerState.update {
            it.copy(isResetting = false)
        }

        showUploadState()
    }

    private fun canAcceptActions(): Boolean =
        sessionStarted &&
                !cleared &&
                viewModelScope.isActive &&
                !mutableComposerState.value.isResetting &&
                maintenanceJob?.isActive != true

    private fun canEditComposer(): Boolean =
        canAcceptActions() &&
                uploadJob?.isActive != true &&
                repository.state.value === MediaPostUploadState.Idle

    private fun showComposerError(message: String) {
        mutableComposerState.update {
            it.copy(error = message)
        }
    }

    private fun showUploadState(
        state: MediaPostUploadState = repository.state.value
    ) {
        mutableUploadState.value = if (
            sessionStarted &&
            !cleared &&
            !mutableComposerState.value.isResetting
        ) {
            state
        } else {
            MediaPostUploadState.Idle
        }
    }

    private fun cancelSelection() {
        selectionVersion++
        selectionJob?.cancel()
        selectionJob = null
    }

    override fun onCleared() {
        cleared = true
        sessionStarted = false

        cancelSelection()

        mutableComposerState.value = MediaPostComposerState()
        mutableUploadState.value = MediaPostUploadState.Idle

        val pendingUpload = uploadJob
        val pendingMaintenance = maintenanceJob
        val uploads = repository

        // viewModelScope is already cancelled here.
        // This scope only finishes cleanup.
        val cleanupScope = CoroutineScope(
            SupervisorJob() + cleanupDispatcher
        )

        cleanupScope.launch {
            try {
                pendingUpload?.cancelAndJoin()
                pendingMaintenance?.cancelAndJoin()
                uploads.clear()
            } finally {
                cleanupScope.cancel()
            }
        }

        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (
                !modelClass.isAssignableFrom(
                    MediaPostViewModel::class.java
                )
            ) {
                throw IllegalArgumentException(
                    "Unknown ViewModel: ${modelClass.name}"
                )
            }

            val files = MediaPostFileStore(applicationContext)

            val uploads = MediaPostUploadRepository(
                TokenStore(applicationContext),
                files
            )

            return MediaPostViewModel(uploads, files) as T
        }
    }
}
