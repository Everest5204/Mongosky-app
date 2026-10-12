package com.mongosky.app.home

import androidx.compose.runtime.Composable
import com.mongosky.app.editpost.EditPostScreen

/** All three feeds open the same native, section-owned editor. */
@Composable
internal fun HomePostEditor(state: HomeEditState, controls: HomeFeedController) {
    EditPostScreen(state, controls.postEditor)
}
