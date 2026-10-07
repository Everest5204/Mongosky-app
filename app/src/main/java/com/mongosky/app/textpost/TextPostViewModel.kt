package com.mongosky.app.textpost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.textpost.TextPostComposerState
import com.mongosky.app.textpost.TextPostCreateApi
import com.mongosky.app.textpost.TextPostCreateException
import com.mongosky.app.textpost.TextPostCreateRepository
import com.mongosky.app.textpost.TextPostDraft
import com.mongosky.app.textpost.TextPostLimits
import com.mongosky.app.textpost.TextPostPreset
import com.mongosky.app.textpost.TextPostPublication
import com.mongosky.app.textpost.TextPostPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One account's draft. No network requests are made while typing or selecting a background. */
class TextPostViewModel(
    private val publisher: TextPostPublisher,
    private val saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private var owner: String? = saved[OWNER]
    private var version = 0
    private var publishJob: Job? = null
    private val state = MutableStateFlow(TextPostComposerState(
        text = TextPostLimits.limit(saved[TEXT] ?: ""),
        preset = TextPostPreset.fromId(saved[PRESET]),
        publication = if (saved.get<Boolean>(PENDING) == true) uncertainFailure() else TextPostPublication.Idle
    ))
    val uiState = state.asStateFlow()

    fun startSession(userId: String?) {
        val validId = userId?.takeIf { it.matches(Regex("^[0-9a-f]{24}$")) }
        if (validId == null) {
            endSession()
            return
        }
        if (owner != validId) {
            endSession()
            owner = validId
            saved[OWNER] = validId
        }
        state.value = state.value.copy(sessionReady = true)
    }

    fun updateText(value: String) {
        if (!state.value.editable) return
        val text = TextPostLimits.limit(value)
        saved[TEXT] = text
        state.value = state.value.copy(text = text, publication = TextPostPublication.Idle)
    }

    fun selectPreset(preset: TextPostPreset) {
        if (!state.value.editable) return
        saved[PRESET] = preset.id
        state.value = state.value.copy(preset = preset, publication = TextPostPublication.Idle)
    }

    fun submit(): Boolean {
        val account = owner ?: return false
        val current = state.value
        if (!current.canPost || publishJob?.isActive == true) return false
        val draft = TextPostDraft(account, current.text.trim(), current.preset)
        val sessionVersion = version
        // If the process is restored mid-request, checking the feed is safer than resending.
        saved[PENDING] = true
        state.value = current.copy(publication = TextPostPublication.Publishing)
        publishJob = viewModelScope.launch {
            try {
                val post = publisher.publish(draft)
                ensureActive()
                if (version != sessionVersion || owner != account) return@launch
                if (!post.isSelf || post.userId != account || post.text != draft.text) {
                    state.value = state.value.copy(publication = uncertainFailure())
                } else {
                    state.value = state.value.copy(publication = TextPostPublication.Published(post))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TextPostCreateException) {
                if (version != sessionVersion || owner != account) return@launch
                saved[PENDING] = error.outcomeUnknown
                state.value = state.value.copy(publication = TextPostPublication.Failed(
                    message = error.message ?: "Could not publish this post.", canRetry = error.canRetry,
                    outcomeUnknown = error.outcomeUnknown, requiresSignIn = error.requiresSignIn
                ))
            } catch (_: Exception) {
                if (version == sessionVersion && owner == account) {
                    state.value = state.value.copy(publication = uncertainFailure())
                }
            }
        }
        return true
    }

    fun acknowledgePublished(postId: String) {
        val publication = state.value.publication as? TextPostPublication.Published ?: return
        if (publication.post.id == postId) clearDraft()
    }

    /** Call after the user confirms discarding; active publication cannot be dismissed. */
    fun discard(): Boolean {
        if (state.value.busy) return false
        clearDraft()
        return true
    }

    fun endSession() {
        version++
        publishJob?.cancel()
        publishJob = null
        owner = null
        saved.remove<String>(OWNER)
        clearDraft()
        state.value = state.value.copy(sessionReady = false)
    }

    private fun clearDraft() {
        saved[TEXT] = ""
        saved[PRESET] = TextPostPreset.PINK_PURPLE.id
        saved[PENDING] = false
        state.value = TextPostComposerState(sessionReady = owner != null)
    }

    class Factory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(TextPostViewModel::class.java))
            return TextPostViewModel(TextPostCreateRepository(tokenStore), extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        private const val OWNER = "textPost.owner"
        private const val TEXT = "textPost.text"
        private const val PRESET = "textPost.preset"
        private const val PENDING = "textPost.pending"
        private fun uncertainFailure() = TextPostPublication.Failed(TextPostCreateApi.UNKNOWN_RESULT, outcomeUnknown = true)
    }
}
