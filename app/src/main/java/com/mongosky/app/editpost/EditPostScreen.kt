package com.mongosky.app.editpost

import androidx.compose.runtime.Composable
import com.mongosky.app.edittextpost.EditTextPostScreen
import com.mongosky.app.edittextpost.EditTextPostState
import com.mongosky.app.editmediapost.EditMediaPostScreen
import com.mongosky.app.editmediapost.EditMediaPostState

/** Compatibility screen router; all feature UI lives in its own section folder. */
@Composable
internal fun EditPostScreen(state: EditPostState, controls: EditPostController) {
    when (state) {
        is EditTextPostState -> EditTextPostScreen(state, controls.textEditor)
        is EditMediaPostState -> EditMediaPostScreen(state, controls.mediaEditor)
    }
}
