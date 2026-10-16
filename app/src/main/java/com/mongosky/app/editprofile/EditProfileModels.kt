package com.mongosky.app.editprofile

import com.mongosky.app.profile.OwnProfile
import com.mongosky.app.profile.ProfileBio
import com.mongosky.app.profile.ProfileImageKind

internal data class EditableProfile(val id: String, val firstName: String, val lastName: String,
    val bio: String = "", val profileImageUrl: String? = null, val coverImageUrl: String? = null) {
    companion object {
        fun from(p: OwnProfile) = EditableProfile(p.id, p.firstName, p.lastName, p.bio, p.profileImageUrl, p.coverImageUrl)
    }
}
internal data class EditProfileChange(val profile: EditableProfile, val version: Long, val mediaChanged: Boolean)
internal data class EditProfileState(
    val ownerId: String = "", val profile: EditableProfile? = null, val bio: String = "",
    val avatarUri: String? = null, val coverUri: String? = null,
    val loading: Boolean = false, val saving: Boolean = false, val error: String? = null,
    val sessionExpired: Boolean = false, val uncertainImages: Set<ProfileImageKind> = emptySet(),
    val reviewingSavedImages: Boolean = false, val confirmDiscard: Boolean = false,
    val closeRequested: Boolean = false, val hasSavedChanges: Boolean = false, val change: EditProfileChange? = null
) {
    val bioChanged get() = profile != null && ProfileBio.normalize(bio) != ProfileBio.normalize(profile.bio)
    val dirty get() = bioChanged || avatarUri != null || coverUri != null
    val canEdit get() = !saving && !sessionExpired && profile != null
    val canSave get() = canEdit && dirty && uncertainImages.isEmpty() && ProfileBio.count(bio) <= ProfileBio.MAX_LENGTH
    val avatarPreview get() = if (reviewingSavedImages && ProfileImageKind.AVATAR in uncertainImages)
        profile?.profileImageUrl else avatarUri ?: profile?.profileImageUrl
    val coverPreview get() = if (reviewingSavedImages && ProfileImageKind.COVER in uncertainImages)
        profile?.coverImageUrl else coverUri ?: profile?.coverImageUrl
}
