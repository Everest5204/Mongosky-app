package com.mongosky.app.personaldetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PersonalDetailsViewModel(
    private val owner: String, private val readToken: suspend () -> String?,
    private val source: PersonalDetailsSource = PersonalDetailsRepository()
) : ViewModel() {
    private val mutable = MutableStateFlow(PersonalDetailsState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var active = false
    private var nameEvents = 0L
    // Bind the confirmed account data to the session that loaded it. A queued
    // save must never send another account's newer token with an older draft.
    private var confirmedToken: String? = null

    fun enter() { active = true; refresh() }
    fun pause() {
        active = false; generation++; job?.cancel(); job = null
        mutable.value = mutable.value.copy(loading = false, saving = false, editor = null, draft = DetailsDraft(),
            draftError = null, discardOpen = false, uncertain = false)
    }
    fun refresh() {
        val s = mutable.value
        if (!active || s.loading || s.saving || s.expired || s.sessionChanged) return
        val generation = this.generation
        val pendingEditor = s.editor; val pendingDraft = s.draft; val uncertain = s.uncertain
        mutable.value = s.copy(loading = true, error = null, notice = null)
        launch {
            val token = token() ?: return@launch
            try {
                val result = source.load(token, owner)
                ensureActive()
                if (!current(token, generation)) return@launch
                requireOwner(result)
                confirmedToken = token
                if (uncertain && pendingEditor != null && DetailsPolicy.matches(result, pendingEditor, pendingDraft)) saved(result, pendingEditor)
                else mutable.value = mutable.value.copy(details = result, loading = false, uncertain = false,
                    draftError = if (uncertain) "Review the latest details before saving again." else mutable.value.draftError)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(token, generation)) failed(e, false) }
        }
    }
    fun edit(editor: DetailsEditor) {
        val s = mutable.value; val d = s.details ?: return
        if (s.loading || s.saving || s.expired || s.sessionChanged || (editor == DetailsEditor.NAME && !d.nameCanChange)) return
        mutable.value = s.copy(editor = editor, draft = DetailsDraft(d.firstName, d.lastName, d.birthDate, d.gender),
            draftError = null, uncertain = false, notice = null, discardOpen = false)
    }
    fun update(draft: DetailsDraft) {
        val s = mutable.value
        if (s.editor == null || s.loading || s.saving || s.uncertain || s.expired || s.sessionChanged) return
        mutable.value = s.copy(draft = draft, draftError = null, notice = null)
    }
    fun requestBack(): Boolean {
        val s = mutable.value
        if (s.saving || s.loading && s.editor != null) return false
        if (s.editor == null) return true
        if (dirty()) mutable.value = s.copy(discardOpen = true)
        else discard()
        return false
    }
    fun keepEditing() { mutable.value = mutable.value.copy(discardOpen = false) }
    fun discard() { mutable.value = mutable.value.copy(editor = null, draft = DetailsDraft(), draftError = null, discardOpen = false, uncertain = false) }
    fun acknowledgeName() { mutable.value = mutable.value.copy(nameEventPending = false) }
    fun dirty(): Boolean { val s = mutable.value; return s.editor?.let { editor -> s.details?.let { !DetailsPolicy.matches(it, editor, s.draft) } } == true }
    fun save() {
        val s = mutable.value; val d = s.details ?: return; val editor = s.editor ?: return
        if (s.loading || s.saving || s.expired || s.sessionChanged || s.uncertain || !dirty()) return
        val draft = DetailsPolicy.normalized(s.draft)
        val error = when (editor) {
            DetailsEditor.NAME -> if (!d.nameCanChange) "You can change your name once every 30 days." else DetailsPolicy.nameError(draft.firstName, draft.lastName)
            DetailsEditor.BIRTHDAY -> DetailsPolicy.birthdayError(draft.birthDate)
            DetailsEditor.GENDER -> if (draft.gender in setOf("", "male", "female", "other")) null else "Choose a gender."
        }
        if (error != null) { mutable.value = s.copy(draftError = error); return }
        val generation = this.generation
        mutable.value = s.copy(saving = true, draft = draft, draftError = null, notice = null)
        launch {
            val token = token() ?: return@launch
            try {
                val result = if (editor == DetailsEditor.NAME) source.saveName(token, owner, draft.firstName, draft.lastName)
                else source.saveDetails(token, owner, d.revision, editor, if (editor == DetailsEditor.BIRTHDAY) draft.birthDate else draft.gender)
                ensureActive()
                if (!current(token, generation)) return@launch
                requireOwner(result)
                if (!DetailsPolicy.matches(result, editor, draft)) throw PersonalDetailsException(message = "Refresh your details to check the update.")
                saved(result, editor)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(token, generation)) failed(e, true) }
        }
    }
    private fun saved(details: PersonalDetails, editor: DetailsEditor) {
        if (editor == DetailsEditor.NAME) nameEvents++
        mutable.value = mutable.value.copy(details = details, loading = false, saving = false, editor = null,
            draft = DetailsDraft(), draftError = null, uncertain = false, notice = "Details updated.",
            nameEvent = nameEvents, nameEventPending = editor == DetailsEditor.NAME)
    }
    private fun requireOwner(details: PersonalDetails) { if (details.id != owner) throw PersonalDetailsException(401, "Please sign in again.") }
    private suspend fun token(): String? {
        val token = try { readToken()?.takeIf { it.isNotBlank() } } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed(PersonalDetailsException(message = "Could not read your session. Try again."), false); return null }
        if (token == null) failed(PersonalDetailsException(401, "Please sign in again."), false)
        if (token != null && confirmedToken != null && confirmedToken != token) { changedSession(); return null }
        return token
    }
    private suspend fun current(token: String, version: Int): Boolean {
        if (!active || version != generation) return false
        val now = try { readToken() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (now != token) { changedSession(); return false }
        return true
    }
    private fun changedSession() {
        confirmedToken = null
        mutable.value = PersonalDetailsState(sessionChanged = true, error = "Your account changed. Close and reopen Personal Details.")
    }
    private fun failed(error: Exception, mutation: Boolean) {
        val status = (error as? PersonalDetailsException)?.status
        if (status == 401) { confirmedToken = null; mutable.value = PersonalDetailsState(expired = true, error = "Please sign in again."); return }
        val message = (error as? PersonalDetailsException)?.message ?: "Could not update your details. Please try again."
        mutable.value = mutable.value.copy(loading = false, saving = false,
            error = if (mutation) mutable.value.error else message,
            draftError = if (mutation) message else mutable.value.draftError,
            uncertain = if (mutation) status == null || status >= 500 || status == 403 || status == 409 else mutable.value.uncertain)
    }
    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        val next = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block() } finally { if (job === coroutineContext[Job]) job = null }
        }
        job = next; next.start()
    }
    override fun onCleared() { pause(); confirmedToken = null; mutable.value = PersonalDetailsState(); super.onCleared() }
    class Factory(private val owner: String, private val readToken: suspend () -> String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T { require(modelClass.isAssignableFrom(PersonalDetailsViewModel::class.java)); return PersonalDetailsViewModel(owner, readToken) as T }
    }
}
