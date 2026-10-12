package com.mongosky.app.profile.userfeed

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.home.*
import com.mongosky.app.post.HomePost
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.UserProfileViewModel
import com.mongosky.app.profile.profileUserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Menu/download state belongs to this viewer and target, never to the Home screen's controller. */
internal class UserProfileFeedController(
    private val profile: UserProfileViewModel,
    private val readToken: suspend () -> String?,
    private val scope: CoroutineScope,
    private val homeSessionExpired: () -> Boolean,
    private val onHomeFeedChanged: () -> Unit,
    actions: HomeFeedActions = HomeFeedApi()
) {
    private var viewer = ""
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
    private val sessionUnavailable get() = viewer.isBlank() || owner.isBlank() || homeSessionExpired() || profile.state.value.let {
        it.sessionExpired || it.unavailable || it.viewerId != viewer || it.userId != owner
    }
    private val expired get() = !ready || sessionUnavailable
    val controls = HomeFeedController(actions, { token }, { scope }, { session }, { expired },
        profile::postActionFailed,
        onEdited = { post ->
            if (!expired && post.author.id == owner && viewer == owner) { profile.refresh(); onHomeFeedChanged() }
        },
        onDeleted = { post ->
            if (!expired && post.author.id == owner && viewer == owner) { profile.refresh(); onHomeFeedChanged() }
        })

    fun enter(viewerId: String?, ownerId: String?) {
        val nextViewer = profileUserId(viewerId)
        val nextOwner = profileUserId(ownerId)
        if (nextViewer == viewer && nextOwner == owner && ready && !expired) return
        endSession()
        if (nextViewer == null || nextOwner == null || homeSessionExpired() || profile.state.value.sessionExpired) return
        viewer = nextViewer; owner = nextOwner
        val generation = session
        tokenJob = scope.launch {
            try {
                val saved = readToken()?.takeIf { it.isNotBlank() }
                    ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
                ensureActive()
                if (generation == session && !sessionUnavailable) { token = saved; ready = true }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == session && !sessionUnavailable) {
                    profile.postActionFailed(OwnProfileException("Please sign in again.", requiresSignIn = true, cause = error))
                }
            }
        }
    }

    fun openMenu(post: HomePost) {
        if (!expired && post.author.id == owner) controls.openMenu(post)
    }

    fun requestDownload(post: HomePost) {
        if (!ready || expired || post.author.id != owner) return
        if (download != null) { controls.notify("A download is already in progress."); return }
        controls.closeMenu()
        download = HomeDownloadState(post)
    }

    fun completeDownloadPicker(context: Context, uri: Uri?) {
        val pending = download ?: return
        if (!pending.choosing) return
        if (uri == null || expired) { download = null; return }
        val generation = session
        download = pending.copy(choosing = false)
        controls.notify("Downloading post…")
        downloadJob = scope.launch {
            try {
                HomeFeedDownloads.write(context.applicationContext, uri, pending.post)
                ensureActive()
                if (generation == session && !expired) controls.notify("Post downloaded")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == session && !expired) controls.notify("Could not download the post. Please try again.")
            } finally { if (generation == session) download = null }
        }
    }

    fun cancelDownloadPicker() { if (download?.choosing == true) download = null }
    fun leave(preservePicker: Boolean = false) {
        controls.setForeground(false); controls.closeOverlays()
        images.setScrolling(false)
        if (!preservePicker) cancelDownloadPicker()
    }
    fun endSession() {
        session++
        tokenJob?.cancel(); tokenJob = null
        downloadJob?.cancel(); downloadJob = null
        controls.endSession(); images.clear()
        viewer = ""; owner = ""; token = null; ready = false; download = null
    }
}
