package com.mongosky.app.editmediapost

import com.mongosky.app.editpost.*
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import java.util.UUID
import kotlinx.coroutines.*

/** Owns caption, retained media, picker tickets, file preparation and media-only save. */
internal class EditMediaPostController(environment: EditPostEnvironment) : EditPostSessionController(environment) {
    override val state get() = super.state as? EditMediaPostState
    override fun accepts(post: HomePost) = post is HomePost.Media
    private var selectionJob: Job? = null
    private var picker: String? = null
    override fun onBeginRead() { picker = null }
    override fun onReset() { picker = null; selectionJob?.cancel(); selectionJob = null }
    fun text(value: String) = change { (it as EditMediaPostState).copy(text = EditMediaPostPolicy.limitCaption(value)) }
    fun removeMedia(key: String) = change { (it as EditMediaPostState).copy(
        keptMedia = it.keptMedia.filterNot { media -> EditMediaPostPolicy.mediaKey(media) == key },
        selectedMedia = it.selectedMedia.filterNot { media -> media.id == key }) }

    fun beginPicker(): String? {
        if (state?.canAddMedia != true || expired() || picker != null) return null
        return UUID.randomUUID().toString().also { picker = it }
    }

    /** Inspect content-provider metadata off Main, without storing an Activity or the media bytes. */
    fun selectMedia(ticket: String, inspect: suspend () -> List<SelectedPostMedia>) {
        if (picker != ticket) return
        picker = null
        val current = state ?: return
        if (!current.canAddMedia || expired()) return
        val request = version; val generation = session()
        currentState = current.copy(selecting = true, error = null)
        selectionJob = scope().launch {
            try {
                val chosen = inspect().distinctBy { it.uri }.filter { next -> current.selectedMedia.none { it.uri == next.uri } }
                ensureActive()
                if (!active(request, generation)) return@launch
                val latest = state ?: return@launch
                if (chosen.isEmpty()) { currentState = latest.copy(selecting = false); return@launch }
                val selected = latest.selectedMedia + chosen
                val error = EditMediaPostPolicy.mediaError(latest.keptMedia, selected)
                if (error == null) {
                    revision++
                    currentState = latest.copy(selecting = false, selectedMedia = selected, error = null)
                } else currentState = latest.copy(selecting = false, error = error)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (active(request, generation)) currentState = state?.copy(selecting = false,
                    error = error.message ?: "Could not read this media. Select it again.")
            }
        }
    }

    fun save(files: MediaPostFileStore? = null) = submit(
        prepare = { draft -> if (draft.selectedMedia.isEmpty()) null else requireNotNull(files).prepare(MediaPostDraft("edit-${UUID.randomUUID()}", draft.text,
            draft.selectedMedia), draft.post.author.id) },
        cleanup = { upload -> files?.delete(upload) })

}
