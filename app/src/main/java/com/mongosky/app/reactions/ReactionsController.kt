package com.mongosky.app.reactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.HomePost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One reaction cache and mutation flow shared by every post surface. */
internal class ReactionsController(
    private val actions: ReactionActions,
    private val currentToken: () -> String?,
    private val currentScope: () -> CoroutineScope,
    private val expired: () -> Boolean,
    private val currentSession: () -> Int,
    private val onSessionFailure: (Exception) -> Unit
) {
    private val token get() = currentToken()
    private val scope get() = currentScope()
    val sessionExpired get() = expired()
    private val sessionVersion get() = currentSession()
    private fun handleSessionFailure(error: Exception) = onSessionFailure(error)
    val reactions = mutableStateMapOf<String, PostReactionState>()
    var peopleState by mutableStateOf<ReactionPeopleState?>(null)
        private set
    private val reactionJobs = mutableMapOf<String, Job>()
    private val reactionVersions = mutableMapOf<String, Int>()
    private val summarySlots = Semaphore(3)
    private var peopleJob: Job? = null
    private var peopleVersion = 0L
    private data class BadgeCache(val verified: Boolean, val readAt: Long)
    private val badgeCache = linkedMapOf<String, BadgeCache>()

    fun endSession() {
        reactionJobs.values.toList().forEach { it.cancel() }
        reactionJobs.clear(); reactionVersions.clear(); reactions.clear()
        closeReactionPeople()
        badgeCache.clear()
    }
    fun onFeedRefresh() {
        reactions.keys.toList().forEach { key ->
            val state = reactions[key] ?: return@forEach
            if (!state.saving) {
                reactionJobs.remove(key)?.cancel()
                reactionVersions[key] = (reactionVersions[key] ?: 0) + 1
                reactions[key] = state.copy(loaded = false, loading = false, error = null)
            }
        }
    }

    /** Home polling refreshes only settled visible rows and never races a saving reaction. */
    fun refreshVisible(posts: List<HomePost>) {
        posts.forEach { post ->
            val current = reactions[post.key]
            if (current?.saving == true || current?.loading == true) return@forEach
            reactionJobs.remove(post.key)?.cancel()
            reactionVersions[post.key] = (reactionVersions[post.key] ?: 0) + 1
            if (current != null) reactions[post.key] = current.copy(loaded = false, error = null)
            ensureReaction(post)
        }
    }

    fun ensureReaction(post: HomePost) {
        val auth = token ?: return
        if (sessionExpired) return
        val current = reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
        if (current.loaded || current.loading || current.saving) return
        val version = reactionVersions[post.key] ?: 0
        val session = sessionVersion
        reactions[post.key] = current.copy(loading = true, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val summary = summarySlots.withPermit { actions.summary(auth, post.id) }
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) {
                    reactions[post.key] = PostReactionState(summary = summary, loaded = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) {
                    handleSessionFailure(error)
                    reactions[post.key] = current.copy(loading = false, error = message(error))
                }
            } finally {
                if (session == sessionVersion && reactionVersions[post.key].orZero() == version) reactionJobs.remove(post.key)
            }
        }
        reactionJobs[post.key] = job
        job.start()
    }

    fun react(post: HomePost, selected: PostReaction = PostReaction.LOVE) {
        val auth = token ?: return
        if (sessionExpired || reactions[post.key]?.saving == true) return
        reactionJobs.remove(post.key)?.cancel()
        val version = reactionVersions[post.key].orZero() + 1
        reactionVersions[post.key] = version
        val session = sessionVersion
        val before = reactions[post.key] ?: PostReactionState(ReactionSummary(total = post.likesCount))
        reactions[post.key] = before.copy(saving = true, loading = false, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var baseline = before.summary
            try {
                if (!before.loaded) baseline = summarySlots.withPermit { actions.summary(auth, post.id) }
                if (session != sessionVersion || reactionVersions[post.key] != version) return@launch
                val next = if (baseline.currentReaction == selected) null else selected
                reactions[post.key] = PostReactionState(baseline.withReaction(next), loaded = true, saving = true)
                val confirmed = actions.react(auth, post.id, next)
                if (session == sessionVersion && reactionVersions[post.key] == version) reactions[post.key] = PostReactionState(confirmed, loaded = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session == sessionVersion && reactionVersions[post.key] == version) {
                    handleSessionFailure(error)
                    // A timeout can follow a committed write. The next tap reads
                    // the server state before choosing POST versus DELETE.
                    reactions[post.key] = PostReactionState(baseline, loaded = false, error = message(error))
                }
            } finally {
                if (session == sessionVersion && reactionVersions[post.key] == version) reactionJobs.remove(post.key)
            }
        }
        reactionJobs[post.key] = job
        job.start()
    }

    fun openReactionPeople(post: HomePost) {
        if (token == null || sessionExpired) return
        closeReactionPeople()
        peopleState = ReactionPeopleState(post.id)
        loadReactionPeople()
    }

    fun selectReactionPeopleFilter(filter: PostReaction?) {
        val state = peopleState ?: return
        if (state.filter == filter) return
        peopleState = state.copy(filter = filter)
        loadReactionPeople()
    }

    fun retryReactionPeople() = loadReactionPeople()

    private fun loadReactionPeople() {
        val auth = token ?: return
        if (sessionExpired) return
        val before = peopleState ?: return
        peopleJob?.cancel()
        val request = ++peopleVersion
        val session = sessionVersion
        peopleState = before.copy(loading = true, people = emptyList(), error = null)
        fun current() = request == peopleVersion && session == sessionVersion && peopleState?.postId == before.postId
        peopleJob = scope.launch {
            try {
                val result = actions.reactionDetails(auth, before.postId, before.filter)
                ensureActive()
                if (!current()) return@launch
                val ids = result.people.map { it.author.id.lowercase() }.distinct()
                val now = System.nanoTime()
                val cached = ids.filter { id -> badgeCache[id]?.let { now - it.readAt < 60_000_000_000L } == true }
                peopleState = before.copy(loading = false, people = result.people, error = null,
                    total = result.total, counts = result.counts,
                    verifiedAuthors = cached.filter { badgeCache[it]?.verified == true }.toSet())
                // Names/photos render first; one batch resolves up to 200 badges.
                for (batch in (ids - cached.toSet()).chunked(200)) {
                    try {
                        val verified = actions.verifiedPeople(auth, batch)
                        ensureActive()
                        if (!current()) return@launch
                        val readAt = System.nanoTime()
                        batch.forEach { id -> badgeCache[id] = BadgeCache(id in verified, readAt) }
                        while (badgeCache.size > 500) badgeCache.remove(badgeCache.keys.first())
                        peopleState = peopleState?.let { it.copy(verifiedAuthors = (it.verifiedAuthors - batch.toSet()) + verified) }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (current()) handleSessionFailure(error)
                        // A badge lookup failure must not hide loaded reactors.
                        break
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!current()) return@launch
                handleSessionFailure(error)
                if (current()) peopleState = peopleState?.copy(loading = false, people = emptyList(), error = message(error))
            }
        }
    }

    fun closeReactionPeople() { peopleVersion++; peopleJob?.cancel(); peopleJob = null; peopleState = null }
    private fun message(error: Exception): String = if (error is FeedApiException) error.message ?: "Please try again."
        else "Could not complete the request. Please try again."
    private fun Int?.orZero() = this ?: 0

}
