package com.mongosky.app.mediapost

import com.mongosky.app.profile.LocalProfileViewerId
import com.mongosky.app.profile.profileLink
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.mediapost.MediaPostPreview
import com.mongosky.app.mediapost.MediaPostUploadLimits
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.shared.ProfileAvatar

private val ComposerGreen = Color(0xFF22C55E)
private val ComposerText = Color(0xFF111827)
private val ComposerMuted = Color(0xFF6B7280)
private val ComposerBorder = Color(0xFFE5E7EB)
private val ComposerSoft = Color(0xFFF3F4F6)

/** onSubmitted must close this screen and navigate to Home without awaiting upload. */
@Composable
fun CreateMediaPostScreen(
    viewModel: MediaPostViewModel,
    userName: String,
    onClose: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    profileImageUrl: String? = null
) {
    val state by viewModel.composerState.collectAsState()
    val upload by viewModel.uploadState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberLazyListState()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val canEdit = !leaving && !state.isResetting && upload === MediaPostUploadState.Idle
    val canChangeMedia = canEdit && !state.isSelecting && !pickerOpen
    val remainingSlots = (MediaPostUploadLimits.MAX_IMAGES - state.media.size)
        .coerceAtLeast(0)

    val initialPickerContract = remember {
        PickMultipleVisualMedia(MediaPostUploadLimits.MAX_IMAGES)
    }
    val initialPicker = rememberLauncherForActivityResult(initialPickerContract) { uris ->
        pickerOpen = false
        if (!leaving && uris.isNotEmpty()) viewModel.selectMedia(uris)
    }

    val morePickerContract = remember(remainingSlots) {
        PickMultipleVisualMedia(remainingSlots.coerceAtLeast(2))
    }
    val morePicker = rememberLauncherForActivityResult(morePickerContract) { uris ->
        pickerOpen = false
        if (!leaving && uris.isNotEmpty()) viewModel.selectMedia(uris, append = true)
    }
    val singlePickerContract = remember { PickVisualMedia() }
    val singleImagePicker = rememberLauncherForActivityResult(singlePickerContract) { uri ->
        pickerOpen = false
        if (!leaving && uri != null) viewModel.selectMedia(listOf(uri), append = true)
    }

    val closeComposer: () -> Unit = {
        if (!leaving) {
            leaving = true
            focusManager.clearFocus()
            keyboard?.hide()
            viewModel.discardComposer()
            onClose()
        }
    }
    val selectInitialMedia: () -> Unit = {
        if (canChangeMedia) {
            focusManager.clearFocus()
            keyboard?.hide()
            pickerOpen = true
            initialPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        }
    }
    val selectMoreImages: () -> Unit = {
        if (canChangeMedia && state.canAddMore && remainingSlots > 0) {
            focusManager.clearFocus()
            keyboard?.hide()
            pickerOpen = true
            val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
            if (remainingSlots == 1) singleImagePicker.launch(request)
            else morePicker.launch(request)
        }
    }

    BackHandler(enabled = !leaving, onBack = closeComposer)

    Box(
        modifier = modifier.fillMaxSize().background(Color.White)
            .safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp)
                .fillMaxWidth().fillMaxHeight()
        ) {
            ComposerHeader(
                canPost = canEdit && state.canPost && !pickerOpen,
                onClose = closeComposer,
                onPost = {
                    if (!leaving && !pickerOpen && viewModel.submit()) {
                        leaving = true
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onSubmitted()
                    }
                }
            )

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val previewHeight = (maxHeight * 0.6f).coerceIn(240.dp, 600.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState
                ) {
                    item(key = "author") {
                        ComposerAuthor(userName, profileImageUrl)
                    }
                    item(key = "caption") {
                        ComposerCaption(
                            caption = state.caption,
                            enabled = canEdit && !pickerOpen,
                            onChange = viewModel::updateCaption
                        )
                    }
                    state.error?.let { message ->
                        item(key = "error") {
                            ComposerError(message, viewModel::dismissComposerError)
                        }
                    }
                    if (state.isResetting || state.isSelecting) {
                        item(key = "checking") {
                            ComposerChecking(
                                if (state.isResetting) "Restoring your draft…"
                                else "Checking media…"
                            )
                        }
                    }
                    if (state.media.isEmpty()) {
                        item(key = "choose") {
                            EmptyMediaSelection(
                                enabled = canChangeMedia,
                                onSelect = selectInitialMedia
                            )
                        }
                    } else {
                        item(key = "preview") {
                            MediaPostPreview(
                                media = state.media,
                                activeIndex = state.activeIndex,
                                enabled = canChangeMedia,
                                maxPreviewHeight = previewHeight,
                                onActiveIndexChange = viewModel::showMedia,
                                onRemove = { id ->
                                    viewModel.removeMedia(id)
                                    if (viewModel.composerState.value.media.isEmpty()) {
                                        closeComposer()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (state.canAddMore) {
                        item(key = "add_more") {
                            AddMoreMediaButton(
                                count = state.media.size,
                                enabled = canChangeMedia,
                                onClick = selectMoreImages
                            )
                        }
                    }
                    item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ComposerHeader(
    canPost: Boolean,
    onClose: () -> Unit,
    onPost: () -> Unit
) {
    Column {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, "Close composer", tint = ComposerText)
            }
            Text(
                text = "Create post",
                color = ComposerText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp)
            )
            TextButton(
                onClick = onPost,
                enabled = canPost,
                modifier = Modifier.align(Alignment.CenterEnd),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ComposerGreen,
                    disabledContentColor = ComposerGreen.copy(alpha = 0.45f)
                )
            ) {
                Text("Post", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        ComposerDivider()
    }
}

@Composable
private fun ComposerAuthor(name: String, profileImageUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().profileLink(LocalProfileViewerId.current).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileAvatar(imageUrl = profileImageUrl, modifier = Modifier.size(44.dp))
        Text(
            text = name.trim().ifBlank { "Mongosky User" },
            color = Color(0xFF374151),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComposerCaption(caption: String, enabled: Boolean, onChange: (String) -> Unit) {
    BasicTextField(
        value = caption,
        onValueChange = onChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        textStyle = TextStyle(color = ComposerText, fontSize = 16.sp, lineHeight = 24.sp),
        minLines = 3,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = KeyboardType.Text
        ),
        cursorBrush = SolidColor(ComposerGreen),
        decorationBox = { innerField ->
            Box {
                if (caption.isEmpty()) {
                    Text("write a caption…", color = ComposerMuted, fontSize = 16.sp)
                }
                innerField()
            }
        }
    )
}

@Composable
private fun ComposerError(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF1F2))
            .padding(start = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = Color(0xFFB91C1C), fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Dismiss error", tint = Color(0xFFB91C1C))
        }
    }
}

@Composable
private fun ComposerChecking(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp), color = ComposerGreen, strokeWidth = 2.dp
        )
        Text(message, color = ComposerMuted, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyMediaSelection(enabled: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp)
            .background(ComposerSoft).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(ComposerImagePlus, null, tint = ComposerMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Choose photos or a video", color = ComposerText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Up to 10 photos or one video", color = ComposerMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSelect,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposerGreen)
        ) {
            Text("Select media", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddMoreMediaButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    Column {
        ComposerDivider()
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(ComposerSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(ComposerImagePlus, null, tint = Color(0xFF374151), modifier = Modifier.size(22.dp))
            }
            Text("Add more", color = Color(0xFF374151), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$count/${MediaPostUploadLimits.MAX_IMAGES} selected", color = ComposerMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ComposerDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ComposerBorder))
}

private val ComposerImagePlus: ImageVector = ImageVector.Builder(
    name = "ComposerImagePlus",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M16 5H22 M19 2V8 M21 11V19 A2 2 0 0 1 19 21H5 " +
                "A2 2 0 0 1 3 19V5 A2 2 0 0 1 5 3H13 " +
                "M11 9 A2 2 0 1 1 7 9 A2 2 0 1 1 11 9 " +
                "M3 17L8 12L13 17L17 13L21 17"
        ).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}.build()
