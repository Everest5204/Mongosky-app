package com.mongosky.app.personaldetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.TokenStore

@Composable
internal fun PersonalDetailsRoute(host: PersonalDetailsHost, onBack: () -> Unit) {
    val activity = checkNotNull(LocalContext.current.detailsActivity())
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    val model = remember(owner, host.sessionKey, host.sessionRevision) {
        ViewModelProvider(owner, PersonalDetailsViewModel.Factory(host.sessionKey, TokenStore(activity.applicationContext)::read))
            .get("MongoskyDetails:${host.sessionKey}:${host.sessionRevision}", PersonalDetailsViewModel::class.java)
    }
    val state by model.state.collectAsState()
    val nameUpdated by rememberUpdatedState(host.onNameUpdated)
    DisposableEffect(model, activity) {
        model.enter()
        onDispose { if (!activity.isChangingConfigurations) model.pause() }
    }
    LaunchedEffect(state.nameEvent, state.nameEventPending) {
        if (state.nameEventPending) { nameUpdated(); model.acknowledgeName() }
    }
    PersonalDetailsScreen(state, host, onBack = { if (!host.busy && model.requestBack()) onBack() },
        onRefresh = model::refresh, onEdit = model::edit, onDraft = model::update, onSave = model::save,
        onDiscard = model::discard, onKeepEditing = model::keepEditing, dirty = model.dirty())
}
private fun Context.detailsActivity(): Activity? {
    var cursor = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        val next = cursor.baseContext
        if (next === cursor) break
        cursor = next
    }
    return cursor as? Activity
}
