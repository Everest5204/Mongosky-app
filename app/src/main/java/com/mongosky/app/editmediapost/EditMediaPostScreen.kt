package com.mongosky.app.editmediapost

import com.mongosky.app.editpost.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.mongosky.app.mediapost.MediaPostFileStore
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.post.HomePost

/** Media editor screen: caption, system picker, previews and upload controls only. */
@Composable
internal fun EditMediaPostScreen(state: EditMediaPostState, controls: EditMediaPostController) {
    val app = LocalContext.current.applicationContext
    val files = remember(app) { MediaPostFileStore(app) }
    val keyboard = LocalSoftwareKeyboardController.current
    var pickerTicket by rememberSaveable(state.post.id) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val ticket = pickerTicket
        pickerTicket = null
        if (ticket != null) controls.selectMedia(ticket) { if (uris.isEmpty()) emptyList() else files.inspect(uris) }
    }
    val openPicker = {
        controls.beginPicker()?.let { ticket ->
            pickerTicket = ticket
            keyboard?.hide()
            try {
                picker.launch(PickVisualMediaRequest(if (state.mediaKind == PostMediaKind.IMAGE)
                    ActivityResultContracts.PickVisualMedia.ImageOnly else ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            } catch (_: Exception) { controls.selectMedia(ticket) { error("Could not open the photo picker. Please try again.") } }
        }
        Unit
    }
    Dialog(onDismissRequest = controls::requestClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 580.dp).fillMaxSize()) {
                EditPostHeader(state, controls::requestClose) { keyboard?.hide(); controls.save(files) }
                if (state.loading || state.selecting || state.saving) LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(2.dp), color = EditPostColors.green, trackColor = Color(0xFFF0FDF4))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                    EditPostAuthor(state, isText = false)
                    EditMediaPostEditor(state, controls::text, controls::removeMedia, openPicker)
                    EditPostError(state, controls::reload) { controls.save(files) }
                }
            }
        }
        EditPostDiscardConfirmation(state, controls::keepEditing, controls::discard)
    }
}
