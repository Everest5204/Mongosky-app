package com.mongosky.app.editprofile

import android.content.Context
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns drafts, partial saves and cancellable work independently of the profile feed. */
internal class EditProfileViewModel(private val source: EditProfileDataSource,
    private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val mutable = MutableStateFlow(EditProfileState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var readVersion = 0
    private var changeVersion = 0L
    private var readJob: Job? = null
    private var saveJob: Job? = null
    private var bioEdited = false

    fun open(ownerId: String, initial: EditableProfile?) {
        val owner = profileObjectId(ownerId)
        if (owner == null || (initial != null && initial.id != owner)) { blockSession(); return }
        if (mutable.value.ownerId == owner && !mutable.value.closeRequested) return
        cancelWork()
        val restore = saved.get<String>(OWNER) == owner
        if (!restore) clearSaved()
        saved[OWNER] = owner
        bioEdited = restore && saved.contains(BIO)
        mutable.value = EditProfileState(ownerId = owner, profile = initial,
            bio = if (bioEdited) ProfileBio.limit(saved.get<String>(BIO).orEmpty()) else initial?.bio.orEmpty(),
            avatarUri = if (restore) saved[AVATAR] else null, coverUri = if (restore) saved[COVER] else null,
            uncertainImages = ProfileImageKind.entries.filterTo(mutableSetOf()) { restore && saved.get<Boolean>(pending(it)) == true })
        load()
    }
    fun updateBio(value: String) {
        if (!mutable.value.canEdit) return
        val bio = ProfileBio.limit(value); bioEdited = true; saved[BIO] = bio
        mutable.value = mutable.value.copy(bio = bio, error = null)
    }
    fun selectImage(kind: ProfileImageKind, uri: String) {
        if (!mutable.value.canEdit || uri.length > 8192 || (!uri.startsWith("content://") && !uri.startsWith("file://"))) return
        saved[draft(kind)] = uri; saved[pending(kind)] = false
        mutable.value = withUri(mutable.value, kind, uri).copy(error = null, uncertainImages = mutable.value.uncertainImages - kind)
    }
    fun pickerFailed() { if (!mutable.value.saving) mutable.value = mutable.value.copy(error = "Could not open the photo picker. Please try again.") }

    fun load() {
        val before = mutable.value
        if (before.ownerId.isBlank() || before.saving || before.sessionExpired) return
        readJob?.cancel()
        val attempt = generation; val request = ++readVersion
        mutable.value = before.copy(loading = true, error = null)
        readJob = viewModelScope.launch {
            try {
                val result = source.load(before.ownerId)
                ensureCurrent(attempt)
                if (request != readVersion) return@launch
                checkOwner(result, before.ownerId)
                val current = mutable.value
                val reviewing = current.uncertainImages.isNotEmpty()
                mutable.value = current.copy(profile = result, bio = if (bioEdited) current.bio else result.bio,
                    reviewingSavedImages = reviewing)
                saved[PENDING_BIO] = false
                if (reviewing) publishChange(media = true, markSaved = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attempt == generation && request == readVersion) failed(e) }
            finally { if (attempt == generation && request == readVersion) mutable.value = mutable.value.copy(loading = false) }
        }
    }
    fun save() {
        val before = mutable.value
        if (!before.canSave) return
        val attempt = generation
        readVersion++; readJob?.cancel()
        mutable.value = before.copy(saving = true, loading = false, error = null, confirmDiscard = false)
        saveJob = viewModelScope.launch {
            var confirmedChanges = false
            var changedMedia = false
            try {
                if (before.bioChanged) {
                    saved[PENDING_BIO] = true
                    val bio = ProfileBio.normalize(before.bio)
                    val result = source.saveBio(before.ownerId, bio)
                    ensureCurrent(attempt); checkOwner(result, before.ownerId)
                    if (result.bio != bio) throw OwnProfileException("Check your saved bio before trying again.")
                    saved[BIO] = result.bio; saved[PENDING_BIO] = false
                    mutable.value = mutable.value.copy(profile = result, bio = result.bio)
                    confirmedChanges = true
                }
                supervisorScope {
                    listOfNotNull(before.avatarUri?.let { ProfileImageKind.AVATAR to it }, before.coverUri?.let { ProfileImageKind.COVER to it })
                        .map { (kind, uri) -> async { saveImage(before.ownerId, kind, uri, attempt) {
                            confirmedChanges = true; changedMedia = true
                        } } }.awaitAll()
                }
                ensureCurrent(attempt)
                if (mutable.value.error == null && mutable.value.uncertainImages.isEmpty() && !mutable.value.dirty)
                    mutable.value = mutable.value.copy(closeRequested = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attempt == generation) { saved[PENDING_BIO] = false; failed(e) } }
            finally {
                if (attempt == generation) {
                    // One event per save batch avoids restarting the Home refresh for each photo.
                    if (confirmedChanges) publishChange(media = changedMedia)
                    mutable.value = mutable.value.copy(saving = false)
                }
            }
        }
    }
    private suspend fun saveImage(ownerId: String, kind: ProfileImageKind, uri: String, attempt: Int, onConfirmed: () -> Unit) {
        saved[pending(kind)] = true
        try {
            val result = source.uploadImage(ownerId, kind, uri)
            ensureCurrent(attempt)
            if (result.ownerId != ownerId || result.kind != kind || result.imageUrl.isBlank())
                throw OwnProfileException("Upload may have finished. Check saved photos before choosing again.", outcomeUnknown = true)
            val current = mutable.value.profile ?: return
            val profile = if (kind == ProfileImageKind.AVATAR) current.copy(profileImageUrl = result.imageUrl) else current.copy(coverImageUrl = result.imageUrl)
            saved.remove<String>(draft(kind)); saved[pending(kind)] = false
            mutable.value = withUri(mutable.value, kind, null).copy(profile = profile)
            onConfirmed()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            ensureCurrent(attempt)
            val unknown = (e as? OwnProfileException)?.outcomeUnknown == true
            if (!unknown) saved[pending(kind)] = false
            mutable.value = mutable.value.copy(uncertainImages = if (unknown) mutable.value.uncertainImages + kind else mutable.value.uncertainImages,
                reviewingSavedImages = false)
            failed(e, if (kind == ProfileImageKind.AVATAR) "Profile photo" else "Cover photo")
        }
    }
    fun useSavedPhotos() {
        val before = mutable.value
        if (!before.reviewingSavedImages || before.loading || before.saving) return
        var next = before
        before.uncertainImages.forEach { kind -> saved.remove<String>(draft(kind)); saved[pending(kind)] = false; next = withUri(next, kind, null) }
        mutable.value = next.copy(uncertainImages = emptySet(), reviewingSavedImages = false, error = null)
    }
    fun requestClose() {
        val s = mutable.value
        if (s.saving) return
        mutable.value = if (s.dirty || s.uncertainImages.isNotEmpty()) s.copy(confirmDiscard = true) else s.copy(closeRequested = true)
    }
    fun keepEditing() { mutable.value = mutable.value.copy(confirmDiscard = false) }
    fun discard() { if (!mutable.value.saving) mutable.value = mutable.value.copy(confirmDiscard = false, closeRequested = true) }
    fun acknowledgeChange(version: Long) { if (mutable.value.change?.version == version) mutable.value = mutable.value.copy(change = null) }
    fun blockSession() { cancelWork(); mutable.value = mutable.value.copy(sessionExpired = true, saving = false, loading = false, error = "Please sign in again.") }
    fun endSession() { cancelWork(); clearSaved(); mutable.value = EditProfileState() }
    private fun publishChange(media: Boolean, markSaved: Boolean = true) {
        val profile = mutable.value.profile ?: return
        mutable.value = mutable.value.copy(hasSavedChanges = mutable.value.hasSavedChanges || markSaved,
            change = EditProfileChange(profile, ++changeVersion, media))
    }
    private fun failed(e: Exception, label: String? = null) {
        val message = e.message?.takeIf(String::isNotBlank) ?: "Could not update your profile. Please try again."
        val detail = if (label == null) message else "$label: $message"
        mutable.value = mutable.value.copy(error = listOfNotNull(mutable.value.error, detail).distinct().joinToString("\n"),
            sessionExpired = mutable.value.sessionExpired || (e as? OwnProfileException)?.requiresSignIn == true)
    }
    private fun checkOwner(p: EditableProfile, owner: String) { if (p.id != owner) throw OwnProfileException("Profile account changed. Please sign in again.", requiresSignIn = true) }
    private suspend fun ensureCurrent(attempt: Int) { currentCoroutineContext().ensureActive(); if (attempt != generation) throw CancellationException("Editor closed") }
    private fun cancelWork() { generation++; readVersion++; readJob?.cancel(); saveJob?.cancel(); readJob = null; saveJob = null }
    private fun clearSaved() { listOf(OWNER, BIO, AVATAR, COVER, PENDING_BIO, pending(ProfileImageKind.AVATAR), pending(ProfileImageKind.COVER)).forEach { saved.remove<Any>(it) } }
    private fun draft(k: ProfileImageKind) = if (k == ProfileImageKind.AVATAR) AVATAR else COVER
    private fun pending(k: ProfileImageKind) = "editProfile.pending.${k.name}"
    private fun withUri(s: EditProfileState, k: ProfileImageKind, uri: String?) = if (k == ProfileImageKind.AVATAR) s.copy(avatarUri = uri) else s.copy(coverUri = uri)
    override fun onCleared() { cancelWork(); super.onCleared() }
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val app = context.applicationContext
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == EditProfileViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return EditProfileViewModel(EditProfileRepository(TokenStore(app), ProfileImagePreparer(app)), extras.createSavedStateHandle()) as T
        }
    }
    companion object {
        private const val OWNER = "editProfile.owner"
        private const val BIO = "editProfile.bio"
        private const val AVATAR = "editProfile.avatar"
        private const val COVER = "editProfile.cover"
        private const val PENDING_BIO = "editProfile.pending.bio"
    }
}
