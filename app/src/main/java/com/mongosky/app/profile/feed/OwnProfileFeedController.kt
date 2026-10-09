package com.mongosky.app.profile.feed

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.home.HomeDownloadState
import com.mongosky.app.home.HomeFeedActions
import com.mongosky.app.home.HomeFeedApi
import com.mongosky.app.home.HomeFeedController
import com.mongosky.app.home.HomeFeedDownloads
import com.mongosky.app.home.HomeFeedImageState
import com.mongosky.app.home.HomeFeedPolicy
import com.mongosky.app.post.HomePost
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.OwnProfileViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Own-profile state; Home's existing API, editor and image policy are reused unchanged. */
internal class OwnProfileFeedController(
    private val profile: OwnProfileViewModel,
    private val readToken: suspend () -> String?,
    private val scope: CoroutineScope,
    private val homeSessionExpired: () -> Boolean,
    private val onHomeFeedChanged: () -> Unit,
    actions: HomeFeedActions = HomeFeedApi()
) {
    private var owner = ""
    private var token: String? = null
    private var session = 0
    private var tokenJob: Job? = null
    private var downloadJob: Job? = null
    var ready by mutableStateOf(false)
        private set
    var download by mutableStateOf<HomeDownloadState?>(null)
        private set
    val images = HomeFeedImageState()
    private val expired get() = profile.state.value.sessionExpired || homeSessionExpired()
    val controls = HomeFeedController(actions, { token }, { scope }, { session }, { expired },
        profile::postActionFailed,
        onEdited = { post ->
            val accepted = when (post) {
                is HomePost.Text -> profile.recordPublishedText(owner, post.post)
                is HomePost.Media -> profile.recordPublishedMedia(owner, post.post)
            }
            if (accepted) onHomeFeedChanged()
        },
        onDeleted = { post -> if (profile.recordDeletedPost(post)) onHomeFeedChanged() })

    fun enter(ownerId: String) {
        if (owner == ownerId && ready) return
        endSession()
        if (!HomeFeedPolicy.validId(ownerId) || expired) return
        owner = ownerId
        val generation = session
        tokenJob = scope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                    ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
                ensureActive()
                if (generation == session && owner == ownerId) { token = saved; ready = true }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == session) {
                    profile.postActionFailed(OwnProfileException("Please sign in again.", requiresSignIn = true, cause = error))
                    controls.notify("Could not restore your saved session. Please sign in again.")
                }
            }
        }
    }

    fun requestDownload(post: HomePost) {
        if (!ready || expired) return
        if (download != null) { controls.notify("A download is already in progress."); return }
        controls.closeMenu()
        download = HomeDownloadState(post)
    }

    fun completeDownloadPicker(context: Context, uri: Uri?) {
        val pending = download ?: return
        if (!pending.choosing) return
        if (uri == null) { download = null; return }
        val generation = session
        download = pending.copy(choosing = false)
        controls.notify("Downloading post…")
        downloadJob = scope.launch {
            try {
                HomeFeedDownloads.write(context.applicationContext, uri, pending.post)
                ensureActive()
                if (generation == session) controls.notify("Post downloaded")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == session) controls.notify("Could not download the post. Please try again.")
            } finally { if (generation == session) download = null }
        }
    }

    fun cancelDownloadPicker() { if (download?.choosing == true) download = null }

    fun leave() {
        controls.setForeground(false)
        controls.closeOverlays()
        images.setScrolling(false)
        cancelDownloadPicker()
    }

    fun endSession() {
        session++
        tokenJob?.cancel(); tokenJob = null
        downloadJob?.cancel(); downloadJob = null
        controls.endSession(); images.clear()
        owner = ""; token = null; ready = false; download = null
    }
}
