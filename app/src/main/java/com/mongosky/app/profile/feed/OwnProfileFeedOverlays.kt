package com.mongosky.app.profile.feed

import androidx.compose.runtime.Composable
import com.mongosky.app.downloadpost.rememberGalleryDownloadActions
import com.mongosky.app.home.HomeDeleteDialog
import com.mongosky.app.home.HomeMenuAction
import com.mongosky.app.home.HomePostEditor
import com.mongosky.app.home.HomePostMenu
import com.mongosky.app.post.HomePost
import com.mongosky.app.reportpost.PostReportSheet
import com.mongosky.app.pinpost.PinPostDialog

/** One screen-owned sheet, using Home's exact menu rows, editor and delete confirmation. */
@Composable
internal fun OwnProfileFeedOverlays(feed: OwnProfileFeedController, enabled: Boolean, onCopy: (HomePost) -> Unit) {
    val galleryDownloads = rememberGalleryDownloadActions()
    if (enabled && feed.ready) {
        val controls = feed.controls
        controls.menu?.let { post ->
            HomePostMenu(post, controls::closeMenu, pins = controls.pins) { action -> when (action) {
                HomeMenuAction.COPY -> { controls.closeMenu(); onCopy(post) }
                HomeMenuAction.DOWNLOAD -> { controls.closeMenu(); galleryDownloads.download(post) }
                HomeMenuAction.EDIT -> controls.openEditor(post)
                HomeMenuAction.DELETE -> controls.openDelete(post)
                HomeMenuAction.REPORT -> controls.openReport(post)
                HomeMenuAction.PIN -> controls.openPin(post)
                else -> controls.unavailable(action)
            } }
        }
        controls.editor?.let { HomePostEditor(it, controls) }
        controls.deletion?.let { HomeDeleteDialog(it, controls::closeDelete, controls::confirmDelete) }
        PostReportSheet(controls.reports)
        PinPostDialog(controls.pins)
    }
}
