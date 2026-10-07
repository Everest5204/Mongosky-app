package com.mongosky.app.profile

import com.mongosky.app.profile.OwnProfileDataSource
import com.mongosky.app.profile.OwnProfileException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OwnProfileConnectionsState(
    val ownerId: String = "", val kind: ProfileConnectionKind? = null,
    val items: List<ProfileConnection> = emptyList(), val hasMore: Boolean = true,
    val loading: Boolean = false, val error: String? = null, val busyIds: Set<String> = emptySet()
)

/** Scoped to the profile ViewModel; closing the sheet cancels and invalidates work. */
class OwnProfileConnectionsController internal constructor(
    private val source: OwnProfileDataSource, private val scope: CoroutineScope,
    private val onChanged: () -> Unit, private val onExpired: () -> Unit,
    private val removeUnfollowed: (ownerId: String) -> Boolean
) {
    internal constructor(source: OwnProfileDataSource, scope: CoroutineScope, onChanged: () -> Unit, onExpired: () -> Unit) :
        this(source, scope, onChanged, onExpired, { true })
    private val mutable = MutableStateFlow(OwnProfileConnectionsState())
    val state = mutable.asStateFlow()
    private var version = 0
    private var cursor: String? = null
    private val seenCursors = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()

    fun open(ownerId: String, kind: ProfileConnectionKind) {
        close()
        mutable.value = OwnProfileConnectionsState(ownerId, kind)
        loadMore()
    }
    fun close() {
        version++
        jobs.values.toList().forEach { it.cancel() }; jobs.clear()
        cursor = null; seenCursors.clear()
        mutable.value = OwnProfileConnectionsState()
    }
    fun retry() {
        if (mutable.value.loading || mutable.value.kind == null) return
        if (mutable.value.items.isNotEmpty() && !mutable.value.hasMore) {
            open(mutable.value.ownerId, requireNotNull(mutable.value.kind))
        } else loadMore()
    }
    fun loadMore() {
        val before = mutable.value
        val kind = before.kind ?: return
        if (before.loading || !before.hasMore || jobs.containsKey("list")) return
        val requestVersion = version
        mutable.value = before.copy(loading = true, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = source.connections(before.ownerId, kind, cursor)
                coroutineContext.ensureActive()
                if (version != requestVersion) return@launch
                if (page.hasMore && (page.nextCursor.isNullOrBlank() || page.nextCursor in seenCursors || page.nextCursor == cursor))
                    throw OwnProfileException("The connection list returned a repeated cursor.")
                if (page.hasMore) seenCursors.add(requireNotNull(page.nextCursor))
                cursor = page.nextCursor
                val current = mutable.value
                mutable.value = current.copy(items = (current.items + page.items).distinctBy { it.id }, hasMore = page.hasMore, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == requestVersion) {
                    mutable.value = mutable.value.copy(loading = false, error = error.message ?: "Could not load this list.")
                    if ((error as? OwnProfileException)?.requiresSignIn == true) onExpired()
                }
            } finally { if (version == requestVersion) jobs.remove("list") }
        }
        jobs["list"] = job
        job.start()
    }
    fun toggleFollowing(personId: String) {
        val person = mutable.value.items.firstOrNull { it.id == personId } ?: return
        if (person.isSelf || personId in mutable.value.busyIds || jobs.containsKey(personId)) return
        val requestVersion = version
        mutable.value = mutable.value.copy(busyIds = mutable.value.busyIds + personId, error = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = source.setFollowing(personId, !person.isFollowing)
                coroutineContext.ensureActive()
                if (version != requestVersion) return@launch
                if (result == person.isFollowing) throw OwnProfileException("Refresh the list to check this change.")
                val current = mutable.value
                val people = current.items.map { if (it.id == personId) it.copy(isFollowing = result) else it }
                    .filterNot { current.kind == ProfileConnectionKind.FOLLOWING && removeUnfollowed(current.ownerId) && it.id == personId && !result }
                mutable.value = current.copy(items = people)
                onChanged()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == requestVersion) {
                    mutable.value = mutable.value.copy(error = error.message ?: "Refresh the list to check this change.")
                    if ((error as? OwnProfileException)?.requiresSignIn == true) onExpired()
                }
            } finally {
                if (version == requestVersion) {
                    jobs.remove(personId)
                    mutable.value = mutable.value.copy(busyIds = mutable.value.busyIds - personId)
                }
            }
        }
        jobs[personId] = job
        job.start()
    }
}
