package com.mongosky.app.downloadpost

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.HomePost
import kotlinx.coroutines.delay

internal class GalleryDownloadActions(private val request: (DownloadPlan) -> Unit, private val invalid: () -> Unit) {
    fun download(post: HomePost) { try { request(DownloadPlan.from(post)) } catch (_: Exception) { invalid() } }
    fun retry(plan: DownloadPlan) { request(plan) }
}
@Composable
internal fun rememberGalleryDownloadActions(): GalleryDownloadActions {
    val context = LocalContext.current
    val manager = remember(context.applicationContext) { PostDownloads.get(context) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val plan = pending?.let { runCatching { DownloadPlan.parse(it) }.getOrNull() }; pending = null
        if (granted && plan != null) {
            if (!manager.request(plan)) Toast.makeText(context, appText((manager.startError ?: "Could not start the download. Please try again.")), Toast.LENGTH_LONG).show()
        } else if (plan != null) Toast.makeText(context, appText("Allow storage access to save this post to Gallery."), Toast.LENGTH_LONG).show()
    }
    return remember(context, manager, permission) { GalleryDownloadActions(request = { plan ->
        try {
            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (pending == null) { pending = plan.json().toString(); permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            } else if (!manager.request(plan)) Toast.makeText(context, appText((manager.startError ?: "Could not start the download. Please try again.")), Toast.LENGTH_LONG).show()
        } catch (_: Exception) { pending = null; Toast.makeText(context, appText("This post could not be downloaded. Please try again."), Toast.LENGTH_LONG).show() }
    }, invalid = { Toast.makeText(context, appText("This post has no downloadable media. Please refresh it."), Toast.LENGTH_LONG).show() }) }
}
/** One app-root host, so route transitions never create two progress overlays. */
@Composable
internal fun PostDownloadHost() { PostDownloadStatus(rememberGalleryDownloadActions()) }
@Composable
private fun PostDownloadStatus(actions: GalleryDownloadActions) {
    val context = LocalContext.current
    val manager = remember(context.applicationContext) { PostDownloads.get(context) }.coordinator
    val rows by manager.state.collectAsState(); val key by manager.visible.collectAsState()
    val row = rows.firstOrNull { it.key == key } ?: return
    LaunchedEffect(row.key, row.phase, row.message) {
        if (row.phase == DownloadPhase.SUCCEEDED) { delay(3500); if (manager.visible.value == row.key) manager.dismiss() }
    }
    Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).widthIn(max = 440.dp).fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = AppColors.surface(androidx.compose.ui.graphics.Color.White), tonalElevation = 2.dp, shadowElevation = 6.dp) {
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(appText(when (row.phase) { DownloadPhase.QUEUED -> "Download queued"; DownloadPhase.SAVING -> "Saving to Gallery"; DownloadPhase.PUBLISHING -> "Finishing…";
                    DownloadPhase.SUCCEEDED -> row.message ?: "Saved to Gallery"; DownloadPhase.FAILED -> "Download failed"; DownloadPhase.CANCELLED -> "Download cancelled" }),
                    color = AppColors.content(FeedColors.text), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (row.active) Text(appText("${row.completed} of ${row.plan.count} files"), color = AppColors.content(FeedColors.muted), fontSize = 12.sp)
                else if (row.phase == DownloadPhase.FAILED) Text(appText(row.message ?: "Please retry."), color = AppColors.content(FeedColors.muted), fontSize = 12.sp)
                if (row.active) {
                    if (row.fraction != null) LinearProgressIndicator(progress = { row.fraction }, modifier = Modifier.fillMaxWidth(), color = AppColors.content(FeedColors.brand))
                    else LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.content(FeedColors.brand))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (row.active) TextButton(onClick = { manager.cancel(row.key) }, enabled = row.phase != DownloadPhase.PUBLISHING) { Text(appText("Cancel"), color = AppColors.content(FeedColors.brand)) }
                    else if (row.phase == DownloadPhase.FAILED || row.phase == DownloadPhase.CANCELLED) TextButton(onClick = { actions.retry(row.plan) }) { Text(appText("Retry"), color = AppColors.content(FeedColors.brand)) }
                    else if (row.saved.isNotEmpty()) TextButton(onClick = { open(context, row.saved.first()) }) { Text(appText("View"), color = AppColors.content(FeedColors.brand)) }
                    TextButton(onClick = manager::dismiss) { Text(appText(if (row.active) "Hide" else "Close"), color = AppColors.content(FeedColors.muted)) }
                }
            }
        }
    }
}
private fun open(context: Context, media: SavedMedia) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(media.uri), media.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    catch (_: Exception) { Toast.makeText(context, appText("Open your Gallery's Mongosky album to view it."), Toast.LENGTH_LONG).show() }
}
