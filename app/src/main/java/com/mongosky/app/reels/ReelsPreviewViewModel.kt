package com.mongosky.app.reels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class ReelsPreviewState(
    val reels: List<Reel> = emptyList(),
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

/** Ten metadata rows only. Home previews never allocate a video player or a video buffer. */
class ReelsPreviewViewModel(
    private val readToken: suspend () -> String?,
    private val api: ReelsDataSource = ReelsApi()
) : ViewModel() {
    var state by mutableStateOf(ReelsPreviewState())
        private set
    private var ownerId: String? = null
    private var token: String? = null
    private var session = 0
    private var epoch = 0
    private var foreground = false
    private var work = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val scope get() = CoroutineScope(viewModelScope.coroutineContext + work)
    private var tokenJob: Job? = null
    private var readJob: Job? = null
    private var failures = 0
    private var pollAfterNanos = 0L
    private val publications = linkedMapOf<String, Reel>()

    fun enter(userId: String?) {
        val id = userId?.trim()?.lowercase()
        if (ownerId == id && (token != null || tokenJob?.isActive == true)) return
        endSession()
        if (id == null || !Regex("[a-f0-9]{24}").matches(id)) {
            state = ReelsPreviewState(sessionExpired = true, error = "Please sign in again.")
            return
        }
        ownerId = id
        val generation = session
        tokenJob = scope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                if (generation != session) return@launch
                if (saved == null) expire() else { token = saved; if (foreground) refresh() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (generation == session) expire() }
        }
    }

    fun endSession() {
        session++; epoch++; work.cancel()
        work = SupervisorJob(viewModelScope.coroutineContext[Job])
        token = null; ownerId = null; tokenJob = null; readJob = null
        failures = 0; pollAfterNanos = 0; publications.clear()
        state = ReelsPreviewState()
    }

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        if (visible) refresh() else {
            epoch++; readJob?.cancel(); readJob = null
            state = state.copy(loading = false)
        }
    }

    fun checkForUpdates() {
        if (foreground && System.nanoTime() >= pollAfterNanos) refresh()
    }

    fun refresh() {
        val auth = token ?: return
        if (!foreground || state.sessionExpired || readJob?.isActive == true) return
        val generation = session
        val request = ++epoch
        state = state.copy(loading = state.reels.isEmpty(), error = null)
        readJob = scope.launch {
            try {
                val page = api.preview(auth)
                if (generation != session || request != epoch || !foreground) return@launch
                page.reels.forEach { publications.remove(it.id) }
                val rows = (publications.values + page.reels).distinctBy { it.id }
                    .sortedWith(compareByDescending<Reel> { it.createdAt }.thenByDescending { it.id })
                    .take(ReelsApi.PREVIEW_SIZE)
                state = state.copy(reels = rows, initialized = true, loading = false, error = null)
                failures = 0; pollAfterNanos = 0
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (generation == session && request == epoch && foreground) {
                    failures = (failures + 1).coerceAtMost(4)
                    pollAfterNanos = System.nanoTime() + (5_000L shl failures) * 1_000_000L
                    if (error is FeedApiException && error.failure == FeedApiFailure.SESSION_EXPIRED) expire()
                    else state = state.copy(initialized = true, loading = false,
                        error = "Could not load Reels. Check your connection and try again.")
                }
            }
        }
    }

    fun recordPublished(owner: String?, post: MediaPost) {
        if (ownerId == null || ownerId != owner?.lowercase() || post.userId.lowercase() != ownerId ||
            post.type != MediaPostType.VIDEO || state.sessionExpired) return
        val video = post.media.firstOrNull { it.type == FeedMediaType.VIDEO } ?: return
        val row = Reel(post.id, post.author, video.url, post.createdAt, post.updatedAt, post.caption,
            likesCount = post.likesCount, commentsCount = post.commentsCount)
        publications[row.id] = row
        // Pending server echoes are bounded just like the visible preview.
        while (publications.size > ReelsApi.PREVIEW_SIZE) publications.remove(publications.keys.first())
        state = state.copy(reels = (listOf(row) + state.reels).distinctBy { it.id }
            .sortedWith(compareByDescending<Reel> { it.createdAt }.thenByDescending { it.id }).take(ReelsApi.PREVIEW_SIZE))
    }

    private fun expire() { state = state.copy(loading = false, sessionExpired = true, error = "Please sign in again.") }

    class Factory(private val tokenStore: TokenStore) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReelsPreviewViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ReelsPreviewViewModel(tokenStore::read) as T
        }
    }
}
