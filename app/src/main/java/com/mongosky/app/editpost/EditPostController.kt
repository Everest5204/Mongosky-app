package com.mongosky.app.editpost

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mongosky.app.edittextpost.EditTextPostController
import com.mongosky.app.editmediapost.EditMediaPostController
import com.mongosky.app.home.*
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset
import kotlinx.coroutines.CoroutineScope

/** Compatibility router: existing Home/Profile integration files stay unchanged. */
internal class EditPostController(
    actions: HomeFeedActions,
    private val token: () -> String?,
    scope: () -> CoroutineScope,
    session: () -> Int,
    private val expired: () -> Boolean,
    onSessionFailure: (Exception) -> Unit,
    onEdited: (HomePost) -> Unit,
    notify: (String) -> Unit,
    verified: (HomePost) -> Boolean = { false }
) {
    private val environment = EditPostEnvironment(actions, token, scope, session, expired, onSessionFailure, onEdited, notify, verified)
    internal val textEditor = EditTextPostController(environment)
    internal val mediaEditor = EditMediaPostController(environment)
    private var active by mutableStateOf<EditPostSessionController?>(null)
    val state get() = active?.state

    fun open(post: HomePost) {
        if (!post.editable || expired() || token().isNullOrBlank() || state?.saving == true) return
        reset()
        active = when (post) { is HomePost.Text -> textEditor; is HomePost.Media -> mediaEditor }
        active?.open(post)
    }
    fun reload() { active?.reload() }
    fun text(value: String) { when (active) { textEditor -> textEditor.text(value); mediaEditor -> mediaEditor.text(value) } }
    fun preset(value: TextPostPreset) { if (active === textEditor) textEditor.preset(value) }
    fun style(value: FeedTextStyle) { if (active === textEditor) textEditor.style(value) }
    fun removeMedia(key: String) { if (active === mediaEditor) mediaEditor.removeMedia(key) }
    fun beginPicker(): String? = if (active === mediaEditor) mediaEditor.beginPicker() else null
    fun selectMedia(ticket: String, inspect: suspend () -> List<SelectedPostMedia>) {
        if (active === mediaEditor) mediaEditor.selectMedia(ticket, inspect)
    }
    fun save(files: MediaPostFileStore? = null) { when (active) { textEditor -> textEditor.save(); mediaEditor -> mediaEditor.save(files) } }
    internal fun savePrepared(prepare: suspend (EditPostState) -> PreparedMediaPost,
                              cleanup: suspend (PreparedMediaPost) -> Unit) {
        active?.submit({ draft -> if (draft.selectedMedia.isEmpty()) null else prepare(draft) }, cleanup)
    }
    fun requestClose() { active?.requestClose() }
    fun keepEditing() { active?.keepEditing() }
    fun discard(reload: Boolean) { active?.discard(reload) }
    fun closeForHost() = requestClose()
    fun reset() { textEditor.reset(); mediaEditor.reset(); active = null }
}
