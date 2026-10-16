package com.mongosky.app.profile

import androidx.compose.runtime.Composable
import com.mongosky.app.editprofile.EditableProfile
import com.mongosky.app.editprofile.EditProfileHost

/** Compatibility entry point; the editor is now a full-screen native page in editprofile/. */
@Composable
internal fun EditOwnProfileSheet(state: OwnProfileState, onConfirmed: (EditableProfile, Boolean) -> Unit,
    onDismiss: () -> Unit, onSignIn: () -> Unit, signingOut: Boolean = false) {
    EditProfileHost(state.ownerId, state.profile, signingOut || state.sessionExpired, onConfirmed, onDismiss, onSignIn)
}
