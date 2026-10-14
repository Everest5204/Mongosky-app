@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.mongosky.app.saveditems

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow

/** Shared by post menus, reels and Saved Items; every response belongs to one account epoch. */
internal class SavedItemsViewModel(private val readToken: suspend () -> String?,private val source: SavedRepository = SavedApi(),
    private val newRequestId: () -> String = { UUID.randomUUID().toString() }) : ViewModel() {
    var state by mutableStateOf(SavedListState()); private set
    var ready by mutableStateOf(false); private set
    var sessionExpired by mutableStateOf(false); private set
    var generation by mutableStateOf(0L); private set
    val cells = mutableStateMapOf<String,SavedCell>()
    private val events = Channel<SavedNotice>(Channel.BUFFERED)
    val notices = events.receiveAsFlow()
    private var owner = "";private var revision = Long.MIN_VALUE;private var token: String? = null
    private var sessionJob: Job? = null;private var pageJob: Job? = null;private var searchJob: Job? = null
    private var targetJob: Job? = null;private var liveJob: Job? = null;private var pollJob: Job? = null
    private var visibleJob: Job? = null;private var visibleVersion = 0L
    private var visibleIds = emptySet<String>();private var ownerRevision = -1L
    private val statusVersions = mutableMapOf<String,Long>()
    private val statusJobs = mutableMapOf<String,Job>();private val writeJobs = mutableMapOf<String,Job>()
    private val menuJobs = mutableMapOf<String,Job>()
    private val observed = linkedMapOf<String,String?>();private val readAt = linkedMapOf<String,Long>()
    private var queryVersion = 0L;private var targetVersion = 0L;private var cursor: String? = null
    private var foreground = false;private var browsing = false;private var initialized = false;private var dirty = false
    fun cell(id: String) = cells[id] ?: SavedCell()
    private fun current(epoch: Long) = epoch == generation && ready && !sessionExpired
    fun bind(ownerId: String,sessionRevision: Long) {
        if (owner == ownerId && revision == sessionRevision && (ready || sessionJob?.isActive == true || sessionExpired)) return
        endSession();owner = ownerId;revision = sessionRevision
        if (!SavedPolicy.validId(ownerId)) { state = state.copy(loading = false,error = "Your account is still loading. Please try again.");return }
        restore()
    }
    private fun restore() {
        if (sessionJob?.isActive == true) return
        val epoch = generation
        sessionJob = viewModelScope.launch {
            try {
                val saved = readToken();ensureActive();if (epoch != generation) return@launch
                if (saved.isNullOrBlank()) { expire();return@launch }
                token = saved;ready = true
                observed.values.filterNotNull().distinct().forEach { ensureStatus(it,true) }
                if (pageActive()) refresh()
                startLive()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (epoch == generation) state = state.copy(loading = false,error = "Could not restore your session. Please try again.") }
        }
    }
    fun retrySession() { if (!ready && !sessionExpired && SavedPolicy.validId(owner)) restore() }
    fun endSession() {
        generation++;queryVersion++;targetVersion++
        listOf(sessionJob,pageJob,searchJob,targetJob,liveJob,pollJob,visibleJob).forEach { it?.cancel() }
        statusJobs.values.forEach { it.cancel() };writeJobs.values.forEach { it.cancel() };menuJobs.values.forEach { it.cancel() }
        statusJobs.clear();writeJobs.clear();menuJobs.clear();cells.clear();readAt.clear();observed.clear();statusVersions.clear()
        sessionJob = null;pageJob = null;searchJob = null;targetJob = null;liveJob = null;pollJob = null
        token = null;owner = "";revision = Long.MIN_VALUE;ready = false;sessionExpired = false
        cursor = null;initialized = false;dirty = false;browsing = false;state = SavedListState()
        visibleJob = null;visibleVersion++;visibleIds = emptySet();ownerRevision = -1
        while (events.tryReceive().isSuccess) { /* Discard feedback from the previous account. */ }
    }
    private fun expire() {
        val id = owner;val rev = revision;endSession();owner = id;revision = rev;sessionExpired = true
        state = state.copy(loading = false,error = "Your session has expired. Please sign in again.")
        notice("Your session has expired. Please sign in again.")
    }
    private fun failedSession(e: Exception): Boolean {
        if (e is FeedApiException && e.failure == FeedApiFailure.SESSION_EXPIRED) { expire();return true };return false
    }
    fun observe(key: String,postId: String? = null,revalidate: Boolean = true) {
        observed[key] = postId
        if (ready && postId != null) ensureStatus(postId,revalidate)
        if (ready && key == PAGE) { if (!initialized || dirty) refresh() else checkHead() }
        startLive()
    }
    fun stopObserving(key: String) {
        observed.remove(key)
        if (key == PAGE) { targetJob?.cancel();targetVersion++;state = state.copy(targetLoading = null) }
        if (observed.isEmpty()) stopLive()
    }
    private fun pageActive() = PAGE in observed
    fun setForeground(active: Boolean) {
        foreground = active
        if (active) { startLive();if (ready) reconcile() } else stopLive()
    }
    private fun stopLive() { liveJob?.cancel();pollJob?.cancel();visibleJob?.cancel();visibleVersion++;liveJob = null;pollJob = null;visibleJob = null }
    fun ensureStatus(id: String,force: Boolean = false) {
        val saved = token ?: return
        if (!SavedPolicy.validId(id) || !ready || sessionExpired || cell(id).saving || statusJobs[id]?.isActive == true) return
        if (!force && cell(id).status != null && System.currentTimeMillis() - (readAt[id] ?: 0) < 20_000) return
        val epoch = generation;val request = (statusVersions[id] ?: 0) + 1;statusVersions[id] = request
        cells[id] = cell(id).copy(loading = true,error = null)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { val status = source.state(saved,owner,id);ensureActive();if (current(epoch) && statusVersions[id] == request) accept(status) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(epoch) && statusVersions[id] == request && !failedSession(e)) cells[id] = cell(id).copy(loading = false,error = message(e)) }
            finally { if (statusJobs[id] === coroutineContext[Job]) statusJobs.remove(id) }
        };statusJobs[id] = job;job.start()
    }
    private fun accept(status: SavedStatus) {
        val existing = cell(status.postId).status
        if (existing != null && existing.revision > status.revision) { cells[status.postId] = cell(status.postId).copy(loading = false);return }
        cells[status.postId] = SavedCell(status);readAt[status.postId] = System.currentTimeMillis()
        if (!status.saved) {
            val opening = state.targetLoading == status.postId
            if (opening) { targetVersion++;targetJob?.cancel() }
            state = state.copy(items = state.items.filterNot { it.postId == status.postId },
                target = state.target?.takeUnless { it.postId == status.postId },targetLoading = if (opening) null else state.targetLoading)
        }
        trimCells()
    }
    private fun trimCells() { while (readAt.size > 500) {
        val id = readAt.keys.firstOrNull { it !in observed.values && it !in writeJobs && it !in menuJobs } ?: break
        readAt.remove(id);cells.remove(id);statusVersions.remove(id)
    } }
    fun toggle(id: String,expectedGeneration: Long = generation) {
        if (expectedGeneration != generation) return
        val before = cell(id)
        if (before.loading || before.saving) return
        if (before.error != null || before.status == null) { ensureStatus(id,true);return }
        change(before.status,!before.status.saved)
    }
    /** Apply the visible label's explicit intent, even if the initial status read is still running. */
    fun setFromMenu(id: String,desired: Boolean,expectedGeneration: Long = generation) {
        if (!current(expectedGeneration) || !SavedPolicy.validId(id) || cell(id).saving || menuJobs[id]?.isActive == true) return
        val before = cell(id)
        if (before.status != null && before.error == null) {
            if (before.status.saved != desired) change(before.status,desired)
            return
        }
        val epoch = generation;val pendingRead = statusJobs[id]
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Reuse a prefetch already in flight. Resolve status before any mutation,
                // then use the clicked Save/Unsave intent rather than a delayed toggle.
                if (pendingRead != null) pendingRead.join()
                else { ensureStatus(id,cell(id).error != null);statusJobs[id]?.join() }
                ensureActive()
                if (!current(epoch)) return@launch
                val latest = cell(id)
                if (latest.status == null || latest.error != null) {
                    notice(latest.error ?: "Could not confirm saved status. Please check your connection.")
                } else if (latest.status.saved != desired && !latest.saving) change(latest.status,desired)
            } finally { if (menuJobs[id] === coroutineContext[Job]) menuJobs.remove(id) }
        }
        menuJobs[id] = job;job.start()
    }
    fun remove(item: SavedItem,expectedGeneration: Long = generation) {
        if (expectedGeneration != generation) return
        if (state.items.none { it.postId == item.postId } || cell(item.postId).saving) return
        val known = cell(item.postId).status
        if (known != null && known.revision >= item.revision) {
            if (!known.saved) { accept(known);return };change(known,false,item.available)
        } else change(SavedStatus(item.postId,true,item.revision,item.savedAt),false,item.available)
    }
    fun undo(value: SavedUndo) {
        if (!current(value.generation)) return
        val known = cell(value.postId).status ?: return
        if (known.revision != value.expectedRevision || known.saved) { notice("Saved status changed. Please check the post options.");return }
        change(known,true)
    }
    private fun change(before: SavedStatus,desired: Boolean,allowUndo: Boolean = true) {
        val saved = token ?: return;val id = before.postId
        if (!ready || sessionExpired || !SavedPolicy.validId(id) || cell(id).saving || writeJobs[id]?.isActive == true) return
        val epoch = generation;val attempt = SaveAttempt(id,desired,before.revision,newRequestId())
        statusVersions[id] = (statusVersions[id] ?: 0) + 1
        statusJobs.remove(id)?.cancel();cells[id] = SavedCell(before,saving = true)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val status = source.update(saved,owner,attempt);ensureActive();if (!current(epoch)) return@launch
                accept(status);dirty = true
                notice(if (status.saved) "Post saved" else "Removed from saved",if (!status.saved && allowUndo) SavedUndo(id,status.revision,epoch) else null)
                if (pageActive()) checkHead()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current(epoch) || failedSession(e)) return@launch
                // A lost write response is reconciled by GET. The old intent is
                // never automatically replayed over another device's update.
                try {
                    val latest = source.state(saved,owner,id);ensureActive();if (!current(epoch)) return@launch;accept(latest);dirty = true
                    notice(if ((e as? FeedApiException)?.statusCode == 409) "Saved status changed. Check the post options and try again."
                        else if (latest.saved == desired) if (desired) "Post is saved" else "Post is no longer saved"
                        else if ((e as? FeedApiException)?.statusCode in setOf(400,403,404,429)) message(e)
                        else "Could not confirm the change. Please try again.")
                    if (pageActive()) checkHead()
                } catch (readError: CancellationException) { throw readError }
                catch (readError: Exception) { if (current(epoch) && !failedSession(readError)) {
                    cells[id] = SavedCell(before,error = "Could not confirm saved status. Refresh before trying again.")
                    notice("Could not confirm saved status. Please check your connection.")
                } }
            } finally { if (writeJobs[id] === coroutineContext[Job]) writeJobs.remove(id) }
        };writeJobs[id] = job;job.start()
    }
    fun select(kind: SavedKind) { if (kind != state.filter.kind) applyFilter(state.filter.copy(kind = kind),state.searchDraft,false) }
    fun search(text: String) { val draft = text.take(80);if (draft == state.searchDraft) return;applyFilter(state.filter.copy(search = SavedPolicy.query(draft)),draft,true) }
    fun clearFilters() = applyFilter(SavedFilter(),"",false)
    private fun applyFilter(filter: SavedFilter,draft: String,debounced: Boolean) {
        if (sessionExpired) return
        searchJob?.cancel();pageJob?.cancel();queryVersion++;cursor = null;initialized = false
        state = state.copy(filter = filter,searchDraft = draft,items = emptyList(),loading = true,refreshing = false,loadingMore = false,
            hasMore = false,error = null,pageError = null,updatesAvailable = false)
        if (debounced) searchJob = viewModelScope.launch { delay(350);refresh() } else refresh()
    }
    fun refresh() {
        if (sessionExpired) return
        if (!ready) { if (SavedPolicy.validId(owner)) restore();return }
        pageJob?.cancel();queryVersion++;cursor = null;dirty = false
        state = state.copy(loading = state.items.isEmpty(),refreshing = state.items.isNotEmpty(),loadingMore = false,error = null,pageError = null,updatesAvailable = false)
        load(false)
    }
    fun loadMore() {
        if (!ready || sessionExpired || pageJob?.isActive == true || searchJob?.isActive == true || !state.hasMore || cursor == null || state.target != null) return
        state = state.copy(loadingMore = true,pageError = null);load(true)
    }
    private fun load(append: Boolean) {
        val saved = token ?: return;val epoch = generation;val request = queryVersion;val filter = state.filter;val next = if (append) cursor else null
        pageJob = viewModelScope.launch {
            try {
                val page = source.page(saved,owner,filter,next);ensureActive();if (!current(epoch) || request != queryVersion) return@launch
                if (append && page.items.isNotEmpty() && state.items.isNotEmpty() && !SavedPolicy.ordered(state.items.last(),page.items.first())) throw FeedApiException("Refresh saved items to load more.",FeedApiFailure.INVALID_RESPONSE)
                val rows = visible(page.items);ingest(rows)
                ownerRevision = maxOf(ownerRevision,page.changeRevision)
                state = state.copy(items = if (append) (state.items + rows).distinctBy { it.postId } else rows,loading = false,refreshing = false,loadingMore = false,
                    hasMore = page.hasMore,error = null,pageError = null);cursor = page.cursor;initialized = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(epoch) && request == queryVersion && !failedSession(e)) {
                val invalid = append && e is FeedApiException && (e.statusCode == 400 || e.failure == FeedApiFailure.INVALID_RESPONSE)
                if (invalid) cursor = null
                state = state.copy(loading = false,refreshing = false,loadingMore = false,hasMore = if (invalid) false else state.hasMore,
                    error = if (append) state.error else message(e),pageError = if (invalid) "Refresh saved items to load more." else if (append) message(e) else null,
                    updatesAvailable = state.updatesAvailable || invalid)
            } }
        }
    }
    private fun visible(rows: List<SavedItem>) = rows.filter { row ->
        val known = cell(row.postId).status
        known == null || known.revision <= row.revision && (known.saved || known.revision < row.revision)
    }
    private fun ingest(rows: List<SavedItem>) { rows.forEach { row -> if (!cell(row.postId).saving) {
        val known = cell(row.postId).status
        if (known == null || known.revision <= row.revision) { cells[row.postId] = SavedCell(SavedStatus(row.postId,true,row.revision,row.savedAt));readAt[row.postId] = System.currentTimeMillis() }
    } };trimCells() }
    fun setBrowsing(value: Boolean) { val settled = browsing && !value;browsing = value;if (settled) refreshVisible() }
    fun checkHead() {
        val saved = token ?: return
        if (!current(generation) || !pageActive() || pageJob?.isActive == true || searchJob?.isActive == true || state.target != null) return
        val epoch = generation;val request = queryVersion;val filter = state.filter
        pageJob = viewModelScope.launch {
            try {
                val page = source.page(saved,owner,filter,null);ensureActive();if (!current(epoch) || request != queryVersion) return@launch
                val rows = visible(page.items)
                val changedAnywhere = ownerRevision >= 0 && page.changeRevision > ownerRevision
                if (browsing && state.items.isNotEmpty()) { if (changedAnywhere || rows != state.items.take(20)) state = state.copy(updatesAvailable = true) }
                else if (!initialized || rows != state.items.take(20) || state.error != null) {
                    ingest(rows);cursor = page.cursor;initialized = true;dirty = false
                    state = state.copy(items = rows,hasMore = page.hasMore,loading = false,refreshing = false,loadingMore = false,error = null,pageError = null,updatesAvailable = false)
                }
                ownerRevision = maxOf(ownerRevision,page.changeRevision)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(epoch) && request == queryVersion && !failedSession(e) && state.items.isEmpty()) state = state.copy(loading = false,error = message(e)) }
        }
    }
    fun open(item: SavedItem,expectedGeneration: Long = generation) {
        if (expectedGeneration != generation) return
        val saved = token ?: return;if (!ready || !item.available || sessionExpired || cell(item.postId).saving) return
        targetJob?.cancel();val epoch = generation;val request = ++targetVersion;state = state.copy(targetLoading = item.postId,target = null)
        targetJob = viewModelScope.launch { try {
            val result = source.post(saved,owner,item.postId);ensureActive();if (current(epoch) && request == targetVersion) state = state.copy(target = result,targetLoading = null)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (current(epoch) && request == targetVersion && !failedSession(e)) { state = state.copy(targetLoading = null);notice(message(e));dirty = true }
        } }
    }
    fun closeTarget() { targetVersion++;targetJob?.cancel();state = state.copy(target = null,targetLoading = null) }
    fun visible(ids: Set<String>) {
        val next = ids.filter(SavedPolicy::validId).take(12).toSet()
        if (visibleIds == next) return
        visibleIds = next;visibleVersion++;visibleJob?.cancel();visibleJob = null
        if (!browsing) refreshVisible()
    }
    private fun refreshVisible() {
        val saved = token ?: return
        if (!foreground || !pageActive() || !ready || visibleIds.isEmpty() || state.target != null || visibleJob?.isActive == true) return
        val epoch = generation;val request = visibleVersion;val ids = visibleIds;val query = queryVersion
        visibleJob = viewModelScope.launch {
            try {
                val rows = source.resolve(saved,owner,ids);ensureActive()
                if (!current(epoch) || request != visibleVersion || query != queryVersion) return@launch
                val byId = rows.associateBy { it.postId }
                val changed = state.items.filter { it.postId in ids }.any { byId[it.postId] != it }
                if (changed) {
                    // Keep the visible row identity/position; promptly redact content
                    // which became unavailable. Membership/order refresh is explicit.
                    state = state.copy(items = state.items.map { old ->
                        byId[old.postId]?.takeIf { it.revision >= old.revision }?.copy(savedAt = old.savedAt) ?: old
                    },updatesAvailable = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(epoch) && request == visibleVersion && query == queryVersion) failedSession(e) }
        }
    }
    private fun reconcile() {
        if (!foreground || !ready || sessionExpired || observed.isEmpty()) return
        observed.values.filterNotNull().distinct().forEach { ensureStatus(it,true) };dirty = true
        if (pageActive()) { checkHead();refreshVisible() }
    }
    private fun startLive() {
        val saved = token ?: return;if (!foreground || !ready || sessionExpired || observed.isEmpty()) return
        val epoch = generation
        if (liveJob?.isActive != true) liveJob = viewModelScope.launch {
            var backoff = 1000L
            while (isActive && current(epoch) && foreground && observed.isNotEmpty()) {
                try { source.changes(saved,owner).debounce(250).collect { value -> if (current(epoch)) {
                    if (ownerRevision >= 0 && value > ownerRevision && pageActive() && browsing) state = state.copy(updatesAvailable = true)
                    reconcile();backoff = 1000
                } } }
                catch (e: CancellationException) { throw e } catch (e: Exception) { if (!current(epoch) || failedSession(e)) break }
                delay(backoff);backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
        if (pollJob?.isActive != true) pollJob = viewModelScope.launch { while (isActive && current(epoch) && foreground && observed.isNotEmpty()) { delay(20_000);reconcile() } }
    }
    private fun notice(message: String,undo: SavedUndo? = null) { events.trySend(SavedNotice(message,generation,undo)) }
    private fun message(e: Exception) = (e as? FeedApiException)?.message ?: "Could not load saved items. Please try again."
    override fun onCleared() { endSession();events.close() }
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val tokens = TokenStore(context.applicationContext)
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T { require(modelClass.isAssignableFrom(SavedItemsViewModel::class.java));return SavedItemsViewModel(tokens::read) as T }
    }
    companion object { const val PAGE = "saved-page" }
}
