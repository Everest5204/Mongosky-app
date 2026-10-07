package com.mongosky.app.profile

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedSource
import com.mongosky.app.profile.OwnProfileDataSource
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.OwnProfileRepository
import com.mongosky.app.profile.ProfileImagePreparer
import com.mongosky.app.textpost.TextPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnProfileState(
    val ownerId: String = "", val profile: OwnProfile? = null, val verified: Boolean = false,
    val profileLoading: Boolean = false, val profileError: String? = null,
    val posts: List<FeedPost> = emptyList(), val loadingSources: Set<FeedSource> = emptySet(),
    val initializedSources: Set<FeedSource> = emptySet(), val hasMoreSources: Set<FeedSource> = FeedSource.entries.toSet(),
    val postErrors: Map<FeedSource, String> = emptyMap(), val refreshing: Boolean = false,
    val postPageVersion: Long = 0, val updatesError: String? = null,
    val updatingSources: Set<FeedSource> = emptySet(),
    val tab: OwnProfileTab = OwnProfileTab.ALL, val editorOpen: Boolean = false, val bioDraft: String = "",
    val savingBio: Boolean = false, val uploading: ProfileImageKind? = null,
    val operationError: String? = null, val uploadUncertain: Boolean = false,
    val sessionExpired: Boolean = false, val changed: ProfileChanged? = null
) {
    val busy: Boolean get() = savingBio || uploading != null
    val hasMore: Boolean get() = if (tab == OwnProfileTab.ALL) hasMoreSources.isNotEmpty() else FeedSource.MEDIA in hasMoreSources
    val postsLoading: Boolean get() = if (tab == OwnProfileTab.ALL) loadingSources.isNotEmpty() else FeedSource.MEDIA in loadingSources
    val activePostErrors: Map<FeedSource, String> get() = if (tab == OwnProfileTab.ALL) postErrors else postErrors.filterKeys { it == FeedSource.MEDIA }
    val canLoadMore: Boolean get() = hasMoreSources.any {
        it !in postErrors && it !in updatingSources && (tab == OwnProfileTab.ALL || it == FeedSource.MEDIA)
    }
}

/** Cached per account, independently loaded sections, cancellable reads and one mutation. */
class OwnProfileViewModel(
    private val source: OwnProfileDataSource,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    private val mutable = MutableStateFlow(OwnProfileState())
    val state = mutable.asStateFlow()
    private val mutableChanges = MutableStateFlow<ProfileChanged?>(null)
    val changes = mutableChanges.asStateFlow()
    val connections = OwnProfileConnectionsController(source, viewModelScope,
        onChanged = { loadProfile(force = true) }, onExpired = { expire() })
    private var sessionVersion = 0
    private var feedVersion = 0
    private var headVersion = 0
    private var profileRevision = 0
    private var profileRequest = 0
    private var mutationVersion = 0
    private var changeVersion = 0L
    private var pager: OwnProfilePager? = null
    private var retainedPosts = emptyList<FeedPost>()
    private var publicationOwner: String? = null
    private val unseenPublished = mutableMapOf<String, FeedPost>()
    private var profileJob: Job? = null
    private var mutationJob: Job? = null
    private var badgeJob: Job? = null
    private var lastBadgeCheck = Long.MIN_VALUE
    private val readJobs = mutableMapOf<FeedSource, Job>()
    private val headJobs = mutableMapOf<FeedSource, Job>()
    private var lastCheck = Long.MIN_VALUE

    fun enter(userId: String?) {
        val id = userId?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf {
            it.matches(Regex("[a-f0-9]{24}")) && it.any { c -> c != '0' }
        }
        if (id == null) { expire(); return }
        if (mutable.value.ownerId == id) {
            if (mutable.value.profile == null && !mutable.value.profileLoading) refresh()
            else checkForUpdates(force = true)
            return
        }
        cancelWork()
        if (publicationOwner != id) { publicationOwner = null; unseenPublished.clear() }
        val sameSavedOwner = saved.get<String>(OWNER) == id
        if (!sameSavedOwner) clearSaved()
        saved[OWNER] = id
        val uncertain = sameSavedOwner && saved.get<Boolean>(PENDING_IMAGE) == true
        mutable.value = OwnProfileState(ownerId = id,
            tab = if (sameSavedOwner) OwnProfileTab.entries.firstOrNull { it.name == saved.get<String>(TAB) } ?: OwnProfileTab.ALL else OwnProfileTab.ALL,
            editorOpen = sameSavedOwner && saved.get<Boolean>(EDITOR) == true,
            bioDraft = if (sameSavedOwner) saved.get<String>(DRAFT).orEmpty() else "",
            uploadUncertain = uncertain,
            operationError = if (uncertain) "Upload may have finished. Refresh your profile to check it." else null)
        mutableChanges.value = null
        pager = OwnProfilePager(id)
        retainedPosts = emptyList()
        publishPosts(requireNotNull(pager))
        lastCheck = clock()
        loadProfile(force = true)
        loadBadge(force = true)
        FeedSource.entries.forEach(::load)
    }
    fun pause() { connections.close(); cancelHeadReads() }
    fun endSession() {
        cancelWork(); connections.close(); clearSaved()
        publicationOwner = null; unseenPublished.clear()
        pager = null; retainedPosts = emptyList(); mutable.value = OwnProfileState(); mutableChanges.value = null
    }
    private fun cancelWork() {
        sessionVersion++; feedVersion++; profileRequest++; mutationVersion++
        profileJob?.cancel(); mutationJob?.cancel(); badgeJob?.cancel()
        profileJob = null; mutationJob = null; badgeJob = null; lastBadgeCheck = Long.MIN_VALUE
        readJobs.values.toList().forEach { it.cancel() }; readJobs.clear()
        cancelHeadReads()
    }
    private fun cancelHeadReads() {
        headVersion++
        headJobs.values.toList().forEach { it.cancel() }; headJobs.clear()
        mutable.value = mutable.value.copy(updatingSources = emptySet())
    }
    private fun clearSaved() { listOf(OWNER, DRAFT, EDITOR, PENDING_IMAGE, TAB).forEach { saved.remove<Any>(it) } }

    fun recordPublishedMedia(userId: String?, post: MediaPost): Boolean =
        post.toOwnProfilePost()?.let { recordPublished(userId, it) } ?: false

    fun recordPublishedText(userId: String?, post: TextPost): Boolean =
        post.toOwnProfilePost()?.let { recordPublished(userId, it) } ?: false

    private fun recordPublished(userId: String?, post: FeedPost): Boolean {
        val owner = userId?.trim()?.lowercase(java.util.Locale.ROOT) ?: return false
        val idPattern = Regex("[a-f0-9]{24}")
        if (!owner.matches(idPattern) || owner.all { it == '0' } || !post.id.matches(idPattern) ||
            post.id.all { it == '0' } || post.userId != owner || post.author.id != owner || mutable.value.sessionExpired ||
            (mutable.value.ownerId.isNotBlank() && mutable.value.ownerId != owner)) return false
        if (publicationOwner != owner) { publicationOwner = owner; unseenPublished.clear() }
        val key = "${post.source}:${post.id}"
        val existing = unseenPublished[key] ?: pager?.existing(post.source, post.id)
        if (existing != null && existing.updatedAt >= post.updatedAt) return true
        unseenPublished[key] = post
        if (mutable.value.ownerId == owner) pager?.let(::publishPosts)
        return true
    }
    private fun reconcilePublications(posts: List<FeedPost>) {
        posts.forEach { returned ->
            val key = "${returned.source}:${returned.id}"
            unseenPublished[key]?.takeIf { returned.updatedAt >= it.updatedAt }?.let { unseenPublished.remove(key) }
        }
    }

    fun selectTab(tab: OwnProfileTab) { saved[TAB] = tab.name; mutable.value = mutable.value.copy(tab = tab) }
    fun refresh() {
        val before = mutable.value
        if (before.ownerId.isEmpty() || before.busy || before.sessionExpired) return
        feedVersion++
        readJobs.values.toList().forEach { it.cancel() }; readJobs.clear()
        cancelHeadReads()
        retainedPosts = before.posts
        pager = OwnProfilePager(before.ownerId)
        mutable.value = before.copy(refreshing = before.posts.isNotEmpty(), loadingSources = emptySet(),
            initializedSources = emptySet(), postErrors = emptyMap(), updatesError = null,
            updatingSources = emptySet(), hasMoreSources = FeedSource.entries.toSet())
        publishPosts(requireNotNull(pager))
        lastCheck = clock()
        loadProfile(force = true, checkedUpload = true)
        loadBadge()
        FeedSource.entries.forEach(::load)
    }
    fun retryPosts() {
        if (mutable.value.sessionExpired) return
        mutable.value.postErrors.keys.toList().forEach(::load)
    }
    fun loadMore() {
        if (mutable.value.sessionExpired) return
        val sources = if (mutable.value.tab == OwnProfileTab.ALL) FeedSource.entries else listOf(FeedSource.MEDIA)
        sources.filter { it in mutable.value.hasMoreSources && it !in mutable.value.postErrors }.forEach(::load)
    }
    private fun load(feedSource: FeedSource) {
        val activePager = pager ?: return
        if (readJobs.containsKey(feedSource) || headJobs.containsKey(feedSource) ||
            feedSource !in activePager.hasMoreSources || mutable.value.sessionExpired) return
        val owner = mutable.value.ownerId
        val session = sessionVersion; val feed = feedVersion
        val cursor = activePager.cursor(feedSource)
        mutable.value = mutable.value.copy(loadingSources = mutable.value.loadingSources + feedSource,
            postErrors = mutable.value.postErrors - feedSource)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = source.posts(owner, feedSource, cursor)
                coroutineContext.ensureActive()
                if (session != sessionVersion || feed != feedVersion) return@launch
                activePager.accept(feedSource, page)
                reconcilePublications(page.posts)
                publishPosts(activePager)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && feed == feedVersion) {
                    mutable.value = mutable.value.copy(postErrors = mutable.value.postErrors + (feedSource to message(error)))
                    if (requiresSignIn(error)) expire()
                }
            } finally {
                if (session == sessionVersion && feed == feedVersion) {
                    readJobs.remove(feedSource)
                    mutable.value = mutable.value.copy(loadingSources = mutable.value.loadingSources - feedSource,
                        refreshing = mutable.value.refreshing && readJobs.isNotEmpty())
                }
            }
        }
        readJobs[feedSource] = job
        job.start()
    }
    private fun publishPosts(activePager: OwnProfilePager) {
        if (publicationOwner == mutable.value.ownerId) FeedSource.entries.forEach { source ->
            activePager.mergeLatest(source, unseenPublished.values.filter { it.source == source })
        }
        val retained = retainedPosts.filter(activePager::keepWhileReloading)
        val incoming = activePager.posts
        val visible = if (retained.isEmpty()) incoming else
            (incoming + retained).distinctBy { "${it.source}:${it.id}" }
                .sortedWith(compareByDescending<FeedPost> { it.createdAt }.thenByDescending { it.id }.thenBy { it.source })
        retainedPosts = retained
        mutable.value = mutable.value.copy(posts = visible, initializedSources = activePager.initializedSources,
            hasMoreSources = activePager.hasMoreSources, postPageVersion = mutable.value.postPageVersion + 1)
    }

    fun checkForUpdates(force: Boolean = false) {
        if (mutable.value.ownerId.isEmpty() || mutable.value.busy || mutable.value.sessionExpired) return
        val now = clock()
        if (!force && lastCheck != Long.MIN_VALUE && now - lastCheck < UPDATE_INTERVAL_MILLIS) return
        lastCheck = now
        loadProfile()
        loadBadge()
        val activePager = pager ?: return
        val owner = mutable.value.ownerId; val session = sessionVersion; val feed = feedVersion; val head = headVersion
        mutable.value = mutable.value.copy(updatesError = null)
        FeedSource.entries.forEach { feedSource ->
            if (headJobs.containsKey(feedSource) || readJobs.containsKey(feedSource)) return@forEach
            if (feedSource !in activePager.initializedSources) { load(feedSource); return@forEach }
            val boundary = activePager.newestFromServer(feedSource)
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    var cursor: String? = null
                    val seen = HashSet<String>()
                    while (true) {
                        val page = source.posts(owner, feedSource, cursor)
                        coroutineContext.ensureActive()
                        if (session != sessionVersion || feed != feedVersion || head != headVersion) return@launch
                        if (page.hasMore && (page.nextCursor.isNullOrBlank() || page.nextCursor == cursor || !seen.add(page.nextCursor)))
                            throw OwnProfileException("Could not update your posts. Please try again.")
                        activePager.mergeLatestPage(feedSource, page, firstPage = cursor == null)
                        reconcilePublications(page.posts)
                        publishPosts(activePager)
                        if (!page.hasMore || boundary == null || page.posts.any {
                            it.createdAt < boundary.createdAt || (it.createdAt == boundary.createdAt && it.id <= boundary.id)
                        }) break
                        cursor = page.nextCursor
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (session == sessionVersion && feed == feedVersion && head == headVersion) {
                        mutable.value = mutable.value.copy(updatesError = message(error))
                        if (requiresSignIn(error)) expire()
                    }
                }
                finally {
                    if (session == sessionVersion && feed == feedVersion && head == headVersion) {
                        headJobs.remove(feedSource)
                        mutable.value = mutable.value.copy(updatingSources = mutable.value.updatingSources - feedSource)
                    }
                }
            }
            headJobs[feedSource] = job
            mutable.value = mutable.value.copy(updatingSources = mutable.value.updatingSources + feedSource)
            job.start()
        }
    }
    fun retryProfile() { if (!mutable.value.sessionExpired) loadProfile(force = true, checkedUpload = true) }
    private fun loadBadge(force: Boolean = false) {
        val owner = mutable.value.ownerId; val session = sessionVersion
        if (owner.isBlank() || mutable.value.sessionExpired || badgeJob?.isActive == true) return
        val now = clock()
        if (!force && lastBadgeCheck != Long.MIN_VALUE && now - lastBadgeCheck < 300_000) return
        lastBadgeCheck = now
        badgeJob = viewModelScope.launch {
            try {
                val verified = source.isVerified(owner)
                coroutineContext.ensureActive()
                if (session == sessionVersion) mutable.value = mutable.value.copy(verified = verified)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (session == sessionVersion) mutable.value = mutable.value.copy(verified = false) }
        }
    }
    private fun loadProfile(force: Boolean = false, checkedUpload: Boolean = false) {
        val owner = mutable.value.ownerId
        if (owner.isEmpty() || mutable.value.sessionExpired) return
        if (!force && profileJob?.isActive == true) return
        profileJob?.cancel()
        val session = sessionVersion; val revision = profileRevision; val request = ++profileRequest
        mutable.value = mutable.value.copy(profileLoading = true, profileError = null)
        profileJob = viewModelScope.launch {
            try {
                val profile = source.profile(owner)
                coroutineContext.ensureActive()
                if (session != sessionVersion || request != profileRequest || revision != profileRevision) return@launch
                if (profile.id != owner) throw OwnProfileException("Profile account changed. Please sign in again.", requiresSignIn = true)
                mutable.value = mutable.value.copy(profile = profile,
                    operationError = if (checkedUpload) null else mutable.value.operationError,
                    uploadUncertain = if (checkedUpload) false else mutable.value.uploadUncertain)
                if (checkedUpload) saved[PENDING_IMAGE] = false
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion && request == profileRequest) {
                    mutable.value = mutable.value.copy(profileError = message(error))
                    if (requiresSignIn(error)) expire()
                }
            } finally {
                if (session == sessionVersion && request == profileRequest) mutable.value = mutable.value.copy(profileLoading = false)
            }
        }
    }
    fun openEditor() {
        if (mutable.value.profile == null || mutable.value.busy || mutable.value.sessionExpired) return
        val bio = mutable.value.profile?.bio.orEmpty()
        saved[DRAFT] = bio; saved[EDITOR] = true
        mutable.value = mutable.value.copy(editorOpen = true, bioDraft = bio, operationError = null)
    }
    fun closeEditor() {
        if (mutable.value.savingBio) return
        saved[EDITOR] = false
        mutable.value = mutable.value.copy(editorOpen = false)
    }
    fun updateBio(text: String) {
        if (mutable.value.busy || mutable.value.sessionExpired) return
        val value = ProfileBio.limit(text)
        saved[DRAFT] = value
        mutable.value = mutable.value.copy(bioDraft = value, operationError = null)
    }
    fun saveBio(): Boolean {
        val before = mutable.value
        if (!before.editorOpen || before.profile == null || before.busy || before.sessionExpired) return false
        val bio = ProfileBio.normalize(before.bioDraft)
        if (ProfileBio.count(bio) > ProfileBio.MAX_LENGTH) return false
        if (bio == before.profile.bio) { closeEditor(); return true }
        val session = sessionVersion; val mutation = ++mutationVersion
        profileRevision++; profileRequest++; profileJob?.cancel()
        mutable.value = before.copy(savingBio = true, profileLoading = false, operationError = null)
        mutationJob = viewModelScope.launch {
            try {
                val confirmed = source.saveBio(before.ownerId, bio)
                coroutineContext.ensureActive()
                if (session != sessionVersion || mutation != mutationVersion) return@launch
                require(confirmed == bio) { "Refresh your profile to check the saved bio." }
                saved[DRAFT] = confirmed; saved[EDITOR] = false
                mutable.value = mutable.value.copy(profile = mutable.value.profile?.copy(bio = confirmed),
                    bioDraft = confirmed, editorOpen = false)
                changed(media = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (session == sessionVersion) mutationError(error) }
            finally {
                if (session == sessionVersion && mutation == mutationVersion) mutable.value = mutable.value.copy(savingBio = false)
            }
        }
        return true
    }
    fun uploadImage(kind: ProfileImageKind, uri: String): Boolean {
        val before = mutable.value
        if (before.profile == null || before.busy || before.uploadUncertain || before.sessionExpired || uri.isBlank()) return false
        val session = sessionVersion; val mutation = ++mutationVersion
        profileRevision++; profileRequest++; profileJob?.cancel()
        saved[PENDING_IMAGE] = true
        mutable.value = before.copy(uploading = kind, profileLoading = false, operationError = null)
        mutationJob = viewModelScope.launch {
            try {
                val result = source.uploadImage(before.ownerId, kind, uri)
                coroutineContext.ensureActive()
                if (session != sessionVersion || mutation != mutationVersion) return@launch
                if (result.ownerId != before.ownerId || result.kind != kind || result.imageUrl.isBlank())
                    throw OwnProfileException("Upload may have finished. Refresh your profile to check it.", outcomeUnknown = true)
                val current = mutable.value.profile ?: return@launch
                val profile = if (kind == ProfileImageKind.AVATAR) current.copy(profileImageUrl = result.imageUrl)
                    else current.copy(coverImageUrl = result.imageUrl)
                mutable.value = mutable.value.copy(profile = profile)
                saved[PENDING_IMAGE] = false
                changed(media = true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (session == sessionVersion) {
                    mutationError(error)
                    if ((error as? OwnProfileException)?.outcomeUnknown != true) saved[PENDING_IMAGE] = false
                }
            } finally {
                if (session == sessionVersion && mutation == mutationVersion) {
                    mutable.value = mutable.value.copy(uploading = null)
                    if (mutable.value.operationError == null) refresh()
                }
            }
        }
        return true
    }
    private fun changed(media: Boolean) {
        val event = ProfileChanged(mutable.value.ownerId, ++changeVersion, media)
        mutable.value = mutable.value.copy(changed = event)
        mutableChanges.value = event
    }
    fun acknowledgeChange(version: Long) {
        if (mutableChanges.value?.version == version) mutableChanges.value = null
    }
    private fun mutationError(error: Exception) {
        mutable.value = mutable.value.copy(operationError = message(error),
            uploadUncertain = (error as? OwnProfileException)?.outcomeUnknown == true)
        if (requiresSignIn(error)) expire()
    }
    private fun expire() {
        mutable.value = mutable.value.copy(sessionExpired = true, profileError = "Please sign in again.")
        connections.close()
    }
    private fun requiresSignIn(error: Exception) = (error as? OwnProfileException)?.requiresSignIn == true ||
        (error as? FeedApiException)?.failure == FeedApiFailure.SESSION_EXPIRED
    private fun message(error: Exception) = error.message?.takeIf { it.isNotBlank() } ?: "Could not load your profile. Please try again."
    override fun onCleared() { cancelWork(); connections.close(); super.onCleared() }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val context = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(OwnProfileViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return OwnProfileViewModel(OwnProfileRepository(TokenStore(context), ProfileImagePreparer(context)), extras.createSavedStateHandle()) as T
        }
    }
    companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
        private const val OWNER = "ownProfile.owner"
        private const val DRAFT = "ownProfile.bio"
        private const val EDITOR = "ownProfile.editor"
        private const val PENDING_IMAGE = "ownProfile.pendingImage"
        private const val TAB = "ownProfile.tab"
    }
}
