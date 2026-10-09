package com.mongosky.app.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.profileObjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One lightweight account cache. No feed/gallery requests or hidden-screen polling. */
internal class DrawerViewModel(
    private val source: DrawerSource, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    private val mutable = MutableStateFlow(DrawerState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var profileRevision = 0
    private var badgeRevision = 0
    private var active = false
    private var profileJob: Job? = null
    private var badgeJob: Job? = null
    private var profileReadAt = Long.MIN_VALUE
    private var nextBadgeReadAt = Long.MIN_VALUE

    fun startSession(userId: String?) {
        val id = userId?.let(::profileObjectId).orEmpty()
        if (id == mutable.value.ownerId) return
        endSession()
        mutable.value = DrawerState(ownerId = id)
    }

    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            profileRevision++
            badgeRevision++
            if (profileJob != null) profileReadAt = Long.MIN_VALUE
            if (badgeJob != null) nextBadgeReadAt = Long.MIN_VALUE
            profileJob?.cancel(); profileJob = null
            badgeJob?.cancel(); badgeJob = null
            mutable.value = mutable.value.copy(loading = false)
        }
    }

    /** Profile changes already confirmed elsewhere should appear immediately, without loading its posts. */
    fun adoptProfile(profile: DrawerProfile?) {
        if (profile == null || profileObjectId(profile.id) != mutable.value.ownerId ||
            mutable.value.ownerId.isBlank() || mutable.value.sessionExpired || profile == mutable.value.profile) return
        profileRevision++
        profileJob?.cancel(); profileJob = null
        profileReadAt = clock()
        mutable.value = mutable.value.copy(profile = profile, loading = false, error = null)
    }

    fun refresh(force: Boolean = false) {
        val owner = mutable.value.ownerId
        if (!active || owner.isBlank() || mutable.value.sessionExpired) return
        val now = clock()
        if (profileJob == null && (force || profileReadAt == Long.MIN_VALUE ||
                now - profileReadAt >= DrawerPolicy.PROFILE_INTERVAL_MILLIS)) loadProfile(owner, now)
        if (badgeJob == null && (force || now >= nextBadgeReadAt)) loadBadge(owner, now)
    }

    private fun loadProfile(owner: String, now: Long) {
        val session = generation; val revision = profileRevision
        profileReadAt = now
        mutable.value = mutable.value.copy(loading = true, error = null)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val profile = source.profile(owner)
                ensureActive()
                if (profileObjectId(profile.id) != owner)
                    throw OwnProfileException("Please sign in again.", requiresSignIn = true)
                if (session == generation && revision == profileRevision && active)
                    mutable.value = mutable.value.copy(profile = profile, error = null)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == generation && revision == profileRevision && active) {
                    if (error is OwnProfileException && error.requiresSignIn) expire()
                    else mutable.value = mutable.value.copy(error = "Could not update your profile. Please try again.")
                }
            } finally {
                if (profileJob === coroutineContext[Job]) {
                    profileJob = null
                    mutable.value = mutable.value.copy(loading = false)
                }
            }
        }
        profileJob = job; job.start()
    }

    private fun loadBadge(owner: String, now: Long) {
        val session = generation; val revision = badgeRevision
        nextBadgeReadAt = now + DrawerPolicy.PROFILE_INTERVAL_MILLIS
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val verified = source.verified(owner)
                ensureActive()
                if (session == generation && revision == badgeRevision && active) {
                    nextBadgeReadAt = clock() + DrawerPolicy.BADGE_INTERVAL_MILLIS
                    mutable.value = mutable.value.copy(verified = verified, badgeKnown = true)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == generation && revision == badgeRevision && active && error is OwnProfileException && error.requiresSignIn) expire()
                // Optional verification never blocks the header or invents a badge on failure.
            } finally { if (badgeJob === coroutineContext[Job]) badgeJob = null }
        }
        badgeJob = job; job.start()
    }

    private fun expire() {
        generation++; profileRevision++
        profileJob?.cancel(); profileJob = null
        badgeJob?.cancel(); badgeJob = null
        mutable.value = DrawerState(ownerId = mutable.value.ownerId, sessionExpired = true,
            error = "Please sign in again.")
    }

    fun endSession() {
        generation++; profileRevision++; active = false
        profileJob?.cancel(); profileJob = null
        badgeJob?.cancel(); badgeJob = null
        profileReadAt = Long.MIN_VALUE; nextBadgeReadAt = Long.MIN_VALUE
        mutable.value = DrawerState()
    }
    override fun onCleared() { endSession(); super.onCleared() }

    class Factory(private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DrawerViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DrawerViewModel(DrawerRepository(readToken)) as T
        }
    }
}
