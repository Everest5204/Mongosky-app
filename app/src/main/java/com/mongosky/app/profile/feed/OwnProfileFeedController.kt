package com.mongosky.app.profile.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.home.HomeFeedActions
import com.mongosky.app.home.HomeFeedApi
import com.mongosky.app.home.HomeFeedController
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
    var ready by mutableStateOf(false)
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

    fun leave() {
        controls.setForeground(false)
        controls.closeOverlays()
        images.setScrolling(false)
    }

    fun endSession() {
        session++
        tokenJob?.cancel(); tokenJob = null
        controls.endSession(); images.clear()
        owner = ""; token = null; ready = false
    }
}
