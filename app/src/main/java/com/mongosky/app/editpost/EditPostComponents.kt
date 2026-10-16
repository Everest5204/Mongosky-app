package com.mongosky.app.editpost

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.home.HomeFeedIcons
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.shared.ProfileAvatar

internal object EditPostColors {
    val text = Color(0xFF111827)
    val muted = Color(0xFF64748B)
    val green = Color(0xFF16A34A)
    val mediaGreen = Color(0xFF22C55E)
    val line = Color(0xFFE5E7EB)
    val soft = Color(0xFFEEF1F3)
    val error = Color(0xFFDC2626)
}

@Composable
internal fun EditPostHeader(state: EditPostState, onClose: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(80.dp)) {
            IconButton(onClose, enabled = !state.saving, modifier = Modifier.size(48.dp)) {
                Icon(FeedIcons.close, appDescription("Close edit post"), tint = AppColors.content(EditPostColors.text), modifier = Modifier.size(24.dp))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(appText("Edit post"), color = AppColors.content(EditPostColors.text), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onSave, enabled = state.valid, modifier = Modifier.width(80.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.content(EditPostColors.green),
                disabledContentColor = AppColors.content(EditPostColors.green.copy(alpha = 0.36f)))) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.size(14.dp), color = AppColors.content(EditPostColors.green), strokeWidth = 2.dp)
                Spacer(Modifier.width(5.dp))
            }
            Text(appText(if (state.saving) "Saving…" else "Save"), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun EditPostAuthor(state: EditPostState, isText: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(state.post.author.profileImageUrl, Modifier.size(if (isText) 42.dp else 47.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(state.post.author.displayName.ifBlank { appText("Mongosky User") }, color = AppColors.content(Color(0xFF273449)),
                fontSize = if (isText) 16.sp else 17.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (state.verified) Icon(HomeFeedIcons.verified, appDescription("Verified account"), tint = Color.Unspecified, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun EditPostError(state: EditPostState, onReload: () -> Unit, onSave: () -> Unit) {
    val message = state.error ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        .background(AppColors.surface(Color(0xFFFEF2F2)), RoundedCornerShape(12.dp)).padding(12.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(appText(message), color = AppColors.content(Color(0xFFB91C1C)), fontSize = 13.sp, lineHeight = 19.sp)
        if (state.needsReload || state.valid) TextButton(if (state.needsReload) onReload else onSave,
            enabled = !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(appText(if (state.needsReload) "Reload latest post" else "Try again"), color = AppColors.content(Color(0xFFB91C1C)), fontWeight = FontWeight.Bold)
        }
    }
}

internal object EditPostIcons {
    val palette by lazy { vector("Palette", "M12 3a9 9 0 1 0 0 18h2a2 2 0 0 0 0-4h-1a2 2 0 0 1 0-4h3a5 5 0 0 0 5-5c0-3-4-5-9-5 M7 9h.01 M10 6h.01 M15 6h.01 M18 9h.01") }
    val grid by lazy { vector("More backgrounds", "M3 3h5v5H3z M10 3h5v5h-5z M17 3h4v5h-4z M3 10h5v5H3z M10 10h5v5h-5z M17 10h4v5h-4z M3 17h5v4H3z M10 17h5v4h-5z M17 17h4v4h-4z") }
    val none by lazy { vector("No background", "M4 4l16 16 M21 12a9 9 0 0 0-14-7 M3 12a9 9 0 0 0 14 7 M3 12a9 9 0 0 1 2-6 M21 12a9 9 0 0 1-2 6") }
    val check by lazy { vector("Selected", "M5 12l4 4L19 6") }
    val addMedia by lazy { vector("Add media", "M11 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-6 M15 5h6 M18 2v6 M7 8h.01 M3 17l5-5 4 4 3-3 6 6") }
    val play by lazy { vector("Play", "M9 5l11 7-11 7z") }
    val pause by lazy { vector("Pause", "M8 5v14 M16 5v14") }
    private fun vector(name: String, path: String) = ImageVector.Builder("EditPost$name", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
}

/** Same confirmation UI for each independent editor. */
@Composable
internal fun EditPostDiscardConfirmation(state: EditPostState, onKeep: () -> Unit, onDiscard: (Boolean) -> Unit) {
        if (state.discardRequested) AlertDialog(
            onDismissRequest = onKeep,
            title = { Text(appText(if (state.reloadRequested) "Reload latest post?" else "Discard changes?")) },
            text = { Text(appText("Your unsaved changes will be discarded.")) },
            confirmButton = { TextButton({ onDiscard(state.reloadRequested) }) {
                Text(appText(if (state.reloadRequested) "Reload" else "Discard"), color = AppColors.content(EditPostColors.error))
            } },
            dismissButton = { TextButton(onKeep) { Text(appText("Keep editing"), color = AppColors.content(EditPostColors.text)) } },
            containerColor = AppColors.surface(Color.White))
}
