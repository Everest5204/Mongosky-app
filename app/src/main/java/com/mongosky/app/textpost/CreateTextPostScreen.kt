@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.textpost

import com.mongosky.app.profile.LocalProfileViewerId
import com.mongosky.app.profile.profileLink
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.shared.ProfileAvatar
import com.mongosky.app.textpost.TextPostBackgroundPicker
import com.mongosky.app.textpost.TextPostEditor
import com.mongosky.app.textpost.TextPostLimits
import com.mongosky.app.textpost.TextPostPublication

/** Web text composer: author, centered text canvas, background presets and confirmed submission. */
@Composable
fun CreateTextPostScreen(
    viewModel: TextPostViewModel,
    userName: String,
    profileImageUrl: String?,
    onClose: () -> Unit,
    onCheckFeed: () -> Unit,
    onSignInAgain: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length)))
    }
    var discardDialog by rememberSaveable { mutableStateOf(false) }
    var emojiSheet by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.text) {
        if (value.text != state.text) value = TextFieldValue(state.text, TextRange(state.text.length))
    }
    LaunchedEffect(Unit) {
        if (state.editable) focusRequester.requestFocus()
    }

    fun changeValue(next: TextFieldValue) {
        if (!state.editable) return
        val limited = TextPostLimits.limit(next.text)
        value = next.copy(text = limited, selection = TextRange(
            next.selection.start.coerceIn(0, limited.length), next.selection.end.coerceIn(0, limited.length)
        ), composition = next.composition?.takeIf { it.end <= limited.length })
        viewModel.updateText(limited)
    }

    fun close() {
        if (state.busy) return
        if (state.text.isNotBlank() || state.uncertain) discardDialog = true
        else if (viewModel.discard()) { keyboard?.hide(); onClose() }
    }

    BackHandler { if (emojiSheet) emojiSheet = false else close() }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            val canvasHeight = (maxWidth * 1.15f).coerceIn(280.dp, 488.dp)
            Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close, enabled = !state.busy) { Icon(Icons.Default.Close, "Close create post") }
                    Text("Create post", modifier = Modifier.weight(1f), color = Color(0xFF020617),
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Button(
                        onClick = { keyboard?.hide(); viewModel.submit() }, enabled = state.canPost,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White,
                            disabledContainerColor = Color(0xFF86EFAC), disabledContentColor = Color.White)
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (state.busy) "posting" else "post", fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
                    Row(Modifier.fillMaxWidth().profileLink(LocalProfileViewerId.current).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        ProfileAvatar(profileImageUrl, Modifier.size(46.dp))
                        Text(userName.ifBlank { "Mongosky User" }, color = Color(0xFF020617), fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextPostEditor(
                        value = value, onValueChange = ::changeValue, preset = state.preset,
                        placeholder = "What's on your mind${userName.trim().substringBefore(' ').takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()}?",
                        enabled = state.editable, height = canvasHeight, focusRequester = focusRequester,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    val error = state.publication as? TextPostPublication.Failed
                    if (error != null) {
                        Surface(Modifier.fillMaxWidth().padding(18.dp), color = Color(0xFFFFF1F2), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(error.message, color = Color(0xFF991B1B), fontSize = 14.sp, lineHeight = 20.sp)
                                if (error.requiresSignIn) TextButton(onClick = onSignInAgain) { Text("Sign in again") }
                                else if (error.outcomeUnknown) TextButton(onClick = { keyboard?.hide(); onCheckFeed() }) { Text("Check Home feed") }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFEEF0F3))
                TextPostBackgroundPicker(state.preset, state.remaining, state.editable,
                    onSelect = viewModel::selectPreset, onBack = ::close,
                    onEmoji = { keyboard?.hide(); emojiSheet = true })
            }
        }
    }

    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false },
        title = { Text("Discard this draft?") },
        text = { Text(if (state.uncertain) "This post may already be on your feed. Check before submitting it again." else "Your text and selected background will be cleared.") },
        confirmButton = { TextButton(onClick = {
            discardDialog = false
            if (viewModel.discard()) { keyboard?.hide(); onClose() }
        }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardDialog = false }) { Text(if (state.uncertain) "Keep draft" else "Keep editing") } }
    )

    if (emojiSheet && state.editable) ModalBottomSheet(onDismissRequest = { emojiSheet = false }, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add emoji", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(EMOJIS, key = { it }) { emoji ->
                        TextButton(onClick = {
                            val start = value.selection.min
                            val end = value.selection.max
                            val text = value.text.replaceRange(start, end, emoji)
                            changeValue(TextFieldValue(text, TextRange(start + emoji.length)))
                            emojiSheet = false
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }) { Text(emoji, fontSize = 24.sp) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private val EMOJIS = listOf("😀", "😊", "😂", "🥰", "😍", "😎", "❤️", "💙", "💚", "✨", "🔥", "🎉", "👍", "👏", "🙌", "🤝", "💪", "🙏", "🌸", "🌿", "🌞", "🌙", "💭", "🚀")
