package com.mongosky.app.editprofile

import android.app.Activity
import android.content.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppAppearance
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.profile.OwnProfile
import com.mongosky.app.profile.ProfileImageKind

@Composable
internal fun EditProfileHost(ownerId: String, initial: OwnProfile?, blocked: Boolean,
    onConfirmed: (EditableProfile, Boolean) -> Unit, onClose: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { checkNotNull(context.editorActivity()) }
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val model = remember(owner) { ViewModelProvider(owner, EditProfileViewModel.Factory(context.applicationContext)).get("MongoskyEditProfile", EditProfileViewModel::class.java) }
    val current by model.state.collectAsState()
    val cached = remember(initial) { initial?.let(EditableProfile::from) }
    val state = if (current.ownerId == ownerId) current else EditProfileState(ownerId = ownerId, profile = cached, bio = cached?.bio.orEmpty(), loading = cached == null)
    val confirmed by rememberUpdatedState(onConfirmed)
    val close by rememberUpdatedState(onClose)
    var requested by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val kind = requested?.let { name -> ProfileImageKind.entries.firstOrNull { it.name == name } }; requested = null
        if (uri != null && kind != null && !blocked) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Older providers may grant access only for this process. */ }
            model.selectImage(kind, uri.toString())
        }
    }
    fun choose(kind: ProfileImageKind) {
        if (requested != null || !state.canEdit || blocked) return
        requested = kind.name
        try { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
        catch (_: Exception) { requested = null; model.pickerFailed() }
    }
    LaunchedEffect(model, ownerId) { model.open(ownerId, cached) }
    LaunchedEffect(model, blocked) { if (blocked) model.blockSession() }
    LaunchedEffect(ownerId, state.change?.version, state.closeRequested) {
        state.change?.let { confirmed(it.profile, it.mediaChanged); model.acknowledgeChange(it.version) }
        if (state.closeRequested) close()
    }
    DisposableEffect(model, activity, ownerId) { onDispose { if (!activity.isChangingConfigurations) model.endSession() } }
    Dialog(onDismissRequest = model::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        val dark = AppAppearance.isDark
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        } } }
        EditProfileScreen(state, model::updateBio, ::choose, model::save, model::requestClose, model::load, model::useSavedPhotos, onSignIn)
        if (state.confirmDiscard) AlertDialog(onDismissRequest = model::keepEditing,
            title = { Text(stringResource(R.string.edit_profile_discard_title), color = AppColors.content(EditProfileColors.text)) },
            text = { Text(stringResource(if (state.hasSavedChanges || state.uncertainImages.isNotEmpty()) R.string.edit_profile_discard_partial_body else R.string.edit_profile_discard_body), color = AppColors.content(EditProfileColors.muted)) },
            confirmButton = { TextButton(model::discard) { Text(stringResource(R.string.edit_profile_discard), color = AppColors.content(EditProfileColors.green)) } },
            dismissButton = { TextButton(model::keepEditing) { Text(stringResource(R.string.edit_profile_keep_editing), color = AppColors.content(EditProfileColors.text)) } },
            containerColor = AppColors.surface(androidx.compose.ui.graphics.Color.White))
    }
}
private fun Context.editorActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext; if (next === current) break; current = next
    }
    return current as? Activity
}
