package com.mongosky.app.edittextpost

import com.mongosky.app.editpost.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

/** Text editor screen: composer/background controls only. */
@Composable
internal fun EditTextPostScreen(state: EditTextPostState, controls: EditTextPostController) {
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = controls::requestClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                EditPostHeader(state, controls::requestClose) { keyboard?.hide(); controls.save() }
                if (state.loading || state.selecting || state.saving) LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(2.dp), color = EditPostColors.green, trackColor = Color(0xFFF0FDF4))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    EditPostError(state, controls::reload, controls::save)
                    EditPostAuthor(state, isText = true)
                    EditTextPostComposer(state, controls::text)
                    Spacer(Modifier.height(18.dp))
                }
                EditTextPostBackgroundPicker(state, controls::preset, controls::requestClose)
            }
        }
        EditPostDiscardConfirmation(state, controls::keepEditing, controls::discard)
    }
}
