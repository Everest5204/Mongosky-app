package com.mongosky.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Read-only profile content, with independent older cursors and cancellable foreground reads. */
class UserProfileViewModel internal constructor(
    private val source: UserProfileDataSource, private val readToken: suspend () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val beginPostCountRead: () -> ((List<FeedPost>) -> Unit) = { { _: List<FeedPost> -> } }
) : ViewModel() {
    private val mutable = MutableStateFlow(UserProfileState())
    val state = mutable.asStateFlow()
    private data class HeadCheckpoint(val boundary: FeedPost?)
    private data class Cached(val pager: OwnProfilePager, val state: UserProfileState, val badgeAt: Long,
        val checkpoints: Map<FeedSource, HeadCheckpoint>)
    private val cache = LinkedHashMap<String, Cached>(4, 0.75f, true)
    private var pager = OwnProfilePager("")
    private var token: String? = null
    private var version = 0L
    private var foreground = false
    private var lastCheck = 0L
    private var badgeAt = 0L
    private var followRevision = 0L
    private val headCheckpoints = mutableMapOf<FeedSource, HeadCheckpoint>()
    private val jobs = mutableMapOf<String, Job>()

    // Reuse the native connection sheet; its owner is the viewed profile, its follow state is the viewer's.
    private val connectionSource = object : OwnProfileDataSource {
        override suspend fun profile(ownerId: String) = source.profile(session(), mutable.value.viewerId, ownerId).asPostOwner()
        override suspend fun posts(ownerId: String, source: FeedSource, cursor: String?) =
            this@UserProfileViewModel.source.posts(session(), mutable.value.viewerId, ownerId, source, cursor)
        override suspend fun connections(ownerId: String, kind: ProfileConnectionKind, cursor: String?): ProfileConnectionPage =
            connectionRequest { source.connections(session(), mutable.value.viewerId, ownerId, kind, cursor) }
        override suspend fun setFollowing(personId: String, following: Boolean): Boolean =
            connectionRequest { source.follow(session(), personId, following).isFollowing }
        override suspend fun saveBio(ownerId: String, bio: String): String = error("Public profiles cannot be edited.")
        override suspend fun uploadImage(ownerId: String, kind: ProfileImageKind, uri: String): ProfileMediaChange =
            error("Public profiles cannot be edited.")
    }
    val connections = OwnProfileConnectionsController(connectionSource, viewModelScope,
        onChanged = { checkForUpdates(force = true) }, onExpired = ::expire,
        removeUnfollowed = { it == mutable.value.viewerId })

    fun enter(viewerId: String?, userId: String?, hint: FeedAuthor? = null) {
        val viewer = profileUserId(viewerId); val target = profileUserId(userId)
        if (viewer == null || target == null) {
            endSession()
            mutable.value = UserProfileState(profileError = "This profile is unavailable.", unavailable = true)
            return
        }
        if (mutable.value.viewerId == viewer && mutable.value.userId == target && foreground) return
        if (mutable.value.viewerId.isNotBlank() && mutable.value.viewerId != viewer) endSession()
        pause()
        val saved = cache[target]
        pager = saved?.pager ?: OwnProfilePager(target)
        headCheckpoints.clear(); headCheckpoints.putAll(saved?.checkpoints ?: emptyMap())
        badgeAt = saved?.badgeAt ?: 0
        mutable.value = (saved?.state ?: UserProfileState()).copy(viewerId = viewer, userId = target,
            hint = hint?.takeIf { profileUserId(it.id) == target } ?: saved?.state?.hint,
            profileLoading = true, loadingSources = emptySet(), followingBusy = false,
            refreshing = false, checking = false, sessionExpired = false, notice = null)
        foreground = true
        val requestVersion = version
        launch("session", requestVersion) {
            try {
                val savedToken = readToken()?.takeIf { it.isNotBlank() } ?: throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED)
                coroutineContext.ensureActive()
                if (current(requestVersion)) { token = savedToken; readProfile(requestVersion, initial = true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(requestVersion)) handleProfileError(error) }
        }
    }

    fun pause() {
        val before = mutable.value
        foreground = false
        version++
        jobs.values.toList().forEach { it.cancel() }; jobs.clear()
        connections.close()
        mutable.value = before.copy(profileLoading = false, loadingSources = emptySet(), refreshing = false,
            checking = false, followingBusy = false, followingUncertain = before.followingUncertain || before.followingBusy)
        saveCache()
    }

    fun endSession() {
        pause(); cache.clear(); headCheckpoints.clear(); token = null; badgeAt = 0; lastCheck = 0
        pager = OwnProfilePager(""); mutable.value = UserProfileState()
    }

    fun selectTab(tab: OwnProfileTab) { mutable.value = mutable.value.copy(tab = tab); saveCache() }
    fun dismissNotice() { mutable.value = mutable.value.copy(notice = null) }
    fun retryProfile() { if (!foreground || mutable.value.sessionExpired) return; readProfile(version, initial = true) }
    fun refresh() = checkForUpdates(force = true, manual = true)
    fun retryPosts() { if (canRead()) mutable.value.postErrors.keys.toList().forEach { loadSource(it, latest = false) } }

    fun loadMore() {
        if (!canRead()) return
        pager.hasMoreSources.forEach { if (it !in mutable.value.postErrors) loadSource(it, latest = false) }
    }

    fun checkForUpdates(force: Boolean = false, manual: Boolean = false) {
        if (!foreground || token == null || mutable.value.sessionExpired || jobs.containsKey("profile") ||
            jobs.containsKey("session") || mutable.value.followingBusy) return
        val now = clock()
        if (!force && now - lastCheck < UPDATE_INTERVAL_MILLIS) return
        lastCheck = now
        mutable.value = mutable.value.copy(refreshing = manual, notice = null)
        readProfile(version, initial = mutable.value.profile == null)
    }

    private fun readProfile(requestVersion: Long, initial: Boolean) {
        if (!current(requestVersion) || jobs.containsKey("profile")) return
        val before = mutable.value; val revision = followRevision
        mutable.value = before.copy(profileLoading = before.profile == null || initial, profileError = null)
        launch("profile", requestVersion) {
            try {
                val result = source.profile(session(), before.viewerId, before.userId)
                coroutineContext.ensureActive()
                if (!current(requestVersion) || followRevision != revision) return@launch
                require(result.id == before.userId && result.isSelf == (before.userId == before.viewerId)) { "Invalid profile response." }
                mutable.value = mutable.value.copy(profile = result, profileLoading = false, profileError = null,
                    unavailable = false, followingUncertain = false)
                FeedSource.entries.forEach { loadSource(it, latest = it in pager.initializedSources) }
                if (badgeAt == 0L || clock() - badgeAt >= BADGE_INTERVAL_MILLIS) loadBadge(requestVersion)
                saveCache()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(requestVersion)) handleProfileError(error) }
            finally { if (current(requestVersion)) mutable.value = mutable.value.copy(profileLoading = false, refreshing = false) }
        }
    }

    private fun loadBadge(requestVersion: Long) {
        if (jobs.containsKey("badge")) return
        val id = mutable.value.userId
        launch("badge", requestVersion) {
            try {
                val result = source.verified(session(), id)
                coroutineContext.ensureActive()
                if (current(requestVersion)) { badgeAt = clock(); mutable.value = mutable.value.copy(verified = result); saveCache() }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (current(requestVersion) && expired(error)) expire() }
        }
    }

    private fun loadSource(sourceKey: FeedSource, latest: Boolean) {
        val key = sourceKey.name
        if (!canRead() || jobs.containsKey(key)) return
        val requestVersion = version; val before = mutable.value
        mutable.value = before.copy(loadingSources = before.loadingSources + sourceKey,
            postErrors = before.postErrors - sourceKey, checking = before.checking || latest)
        launch(key, requestVersion) {
            try {
                if (!latest) {
                    val acceptCounts = beginPostCountRead()
                    val page = source.posts(session(), before.viewerId, before.userId, sourceKey, pager.cursor(sourceKey))
                    coroutineContext.ensureActive()
                    if (!current(requestVersion)) return@launch
                    pager.accept(sourceKey, page)
                    acceptCounts(page.posts)
                } else {
                    // Keep the original boundary until the whole gap is covered. A partial network failure
                    // must not make the newly merged first page hide the remaining unseen updates.
                    val boundary = headCheckpoints.getOrPut(sourceKey) { HeadCheckpoint(pager.newestFromServer(sourceKey)) }.boundary
                    var cursor: String? = null; var first = true
                    val seen = mutableSetOf<String>()
                    do {
                        val acceptCounts = beginPostCountRead()
                        val page = source.posts(session(), before.viewerId, before.userId, sourceKey, cursor)
                        coroutineContext.ensureActive()
                        if (!current(requestVersion)) return@launch
                        pager.mergeLatestPage(sourceKey, page, first)
                        acceptCounts(page.posts)
                        publishPosts(latest = false)
                        val reached = boundary != null && page.posts.any { it.createdAt < boundary.createdAt ||
                            it.createdAt == boundary.createdAt && it.id <= boundary.id }
                        if (!page.hasMore || reached || boundary == null && page.posts.isNotEmpty()) break
                        val next = requireNotNull(page.nextCursor) { "Missing post cursor." }
                        require(next != cursor && seen.add(next)) { "Repeated post cursor." }
                        cursor = next; first = false
                    } while (true)
                    headCheckpoints.remove(sourceKey)
                }
                publishPosts(latest)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current(requestVersion)) {
                    mutable.value = mutable.value.copy(postErrors = mutable.value.postErrors + (sourceKey to message(error)))
                    if (expired(error)) expire()
                }
            } finally {
                if (current(requestVersion)) {
                    val remaining = mutable.value.loadingSources - sourceKey
                    mutable.value = mutable.value.copy(loadingSources = remaining, checking = remaining.isNotEmpty() && mutable.value.checking)
                    saveCache()
                }
            }
        }
    }

    private fun publishPosts(latest: Boolean) {
        mutable.value = mutable.value.copy(posts = pager.posts, hasMore = pager.hasMoreSources.isNotEmpty(),
            pageVersion = mutable.value.pageVersion + 1,
            refreshVersion = mutable.value.refreshVersion + if (latest) 1 else 0)
    }

    fun toggleFollowing() {
        val before = mutable.value; val profile = before.profile ?: return
        if (!canRead() || profile.isSelf || before.followingBusy || before.followingUncertain || jobs.containsKey("follow")) return
        val desired = !profile.isFollowing; val requestVersion = version
        followRevision++
        jobs.remove("profile")?.cancel()
        mutable.value = before.copy(followingBusy = true, profileLoading = false, refreshing = false, notice = null)
        launch("follow", requestVersion) {
            try {
                val result = source.follow(session(), profile.id, desired)
                coroutineContext.ensureActive()
                if (!current(requestVersion)) return@launch
                require(result.isFollowing == desired && result.followersCount >= 0) { "Check the profile to confirm this change." }
                mutable.value = mutable.value.copy(profile = mutable.value.profile?.copy(isFollowing = result.isFollowing,
                    followersCount = result.followersCount), followingUncertain = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current(requestVersion)) {
                    mutable.value = mutable.value.copy(followingUncertain = true,
                        notice = "Could not confirm this change. Refresh the profile before trying again.")
                    if (expired(error)) expire()
                }
            } finally {
                if (current(requestVersion)) { mutable.value = mutable.value.copy(followingBusy = false); saveCache() }
            }
        }
    }

    private fun canRead() = foreground && token != null && mutable.value.profile != null &&
        !mutable.value.sessionExpired && !mutable.value.unavailable
    private fun current(requestVersion: Long) = requestVersion == version && foreground
    private suspend fun session(): String = token ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
    private fun launch(key: String, requestVersion: Long, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() } finally { if (version == requestVersion) jobs.remove(key) }
        }
        jobs[key] = job; job.start()
    }
    private fun saveCache() {
        val snapshot = mutable.value
        if (snapshot.userId.isBlank() || snapshot.sessionExpired || snapshot.unavailable) return
        cache[snapshot.userId] = Cached(pager, snapshot, badgeAt, headCheckpoints.toMap())
        while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
    }
    private fun handleProfileError(error: Exception) {
        if (expired(error)) { expire(); return }
        val denied = (error as? FeedApiException)?.let { it.statusCode == 404 || it.statusCode == 403 } == true
        if (denied) {
            cache.remove(mutable.value.userId)
            FeedSource.entries.forEach { jobs.remove(it.name)?.cancel() }
            pager = OwnProfilePager(mutable.value.userId)
            headCheckpoints.clear()
        }
        mutable.value = mutable.value.copy(profileLoading = false, profileError = if (denied) "This profile is unavailable." else message(error),
            unavailable = denied, profile = if (denied) null else mutable.value.profile,
            posts = if (denied) emptyList() else mutable.value.posts, hasMore = if (denied) false else mutable.value.hasMore,
            loadingSources = if (denied) emptySet() else mutable.value.loadingSources)
    }
    private fun expire() {
        pause(); cache.clear(); token = null
        mutable.value = mutable.value.copy(profile = null, posts = emptyList(), hasMore = false,
            sessionExpired = true, profileError = "Please sign in again.")
    }
    private fun expired(error: Exception) = (error as? FeedApiException)?.failure == FeedApiFailure.SESSION_EXPIRED ||
        (error as? OwnProfileException)?.requiresSignIn == true
    private fun message(error: Exception) = error.message?.takeIf { it.isNotBlank() } ?: "Could not load this profile. Please try again."
    private suspend fun <T> connectionRequest(block: suspend () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { throw OwnProfileException(message(error), requiresSignIn = expired(error), cause = error) }

    companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
        private const val BADGE_INTERVAL_MILLIS = 60_000L
        private const val CACHE_SIZE = 3
    }
    class Factory(private val tokenStore: TokenStore,
        private val beginPostCountRead: () -> ((List<FeedPost>) -> Unit) = { { _: List<FeedPost> -> } }) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UserProfileViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return UserProfileViewModel(UserProfileApi(), tokenStore::read, beginPostCountRead = beginPostCountRead) as T
        }
    }
}
