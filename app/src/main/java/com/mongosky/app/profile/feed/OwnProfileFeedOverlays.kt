package com.mongosky.app.profile.feed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mongosky.app.home.HomeDeleteDialog
import com.mongosky.app.home.HomeMenuAction
import com.mongosky.app.home.HomePostEditor
import com.mongosky.app.home.HomePostMenu
import com.mongosky.app.post.HomePost

/** One screen-owned sheet, using Home's exact menu rows, editor and delete confirmation. */
@Composable
internal fun OwnProfileFeedOverlays(feed: OwnProfileFeedController, enabled: Boolean, onCopy: (HomePost) -> Unit) {
    val context = LocalContext.current
    var launchedDownloadKey by rememberSaveable { mutableStateOf<String?>(null) }
    val downloadPicker = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("*/*") {
        override fun createIntent(context: Context, input: String): Intent =
            super.createIntent(context, input).setType(feed.download?.mimeType ?: "application/octet-stream")
    }) { uri ->
        launchedDownloadKey = null
        feed.completeDownloadPicker(context, uri)
    }
    val download = feed.download
    LaunchedEffect(feed, download?.post?.key, download?.choosing) {
        if (download?.choosing == true && launchedDownloadKey != download.post.key) {
            launchedDownloadKey = download.post.key
            try { downloadPicker.launch(download.fileName) }
            catch (_: ActivityNotFoundException) {
                launchedDownloadKey = null; feed.cancelDownloadPicker()
                feed.controls.notify("No file picker is available.")
            }
        }
    }
    if (enabled && feed.ready) {
        val controls = feed.controls
        controls.menu?.let { post ->
            HomePostMenu(post, controls::closeMenu) { action -> when (action) {
                HomeMenuAction.COPY -> { controls.closeMenu(); onCopy(post) }
                HomeMenuAction.DOWNLOAD -> feed.requestDownload(post)
                HomeMenuAction.EDIT -> controls.openEditor(post)
                HomeMenuAction.DELETE -> controls.openDelete(post)
                else -> controls.unavailable(action)
            } }
        }
        controls.editor?.let { HomePostEditor(it, controls) }
        controls.deletion?.let { HomeDeleteDialog(it, controls::closeDelete, controls::confirmDelete) }
    }
}
