@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mongosky.app.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.profile.OwnProfileState
import com.mongosky.app.shared.ProfileAvatar

@Composable
internal fun EditOwnProfileSheet(
    state: OwnProfileState, onBio: (String) -> Unit, onSave: () -> Unit,
    onImage: (ProfileImageKind) -> Unit, onDismiss: () -> Unit
) {
    var editor by rememberSaveable(state.ownerId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.bioDraft, TextRange(state.bioDraft.length)))
    }
    LaunchedEffect(state.bioDraft) {
        if (editor.text != state.bioDraft) editor = editor.copy(text = state.bioDraft,
            selection = TextRange(editor.selection.start.coerceIn(0, state.bioDraft.length), editor.selection.end.coerceIn(0, state.bioDraft.length)))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.savingBio })) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OwnProfileColors.text, modifier = Modifier.weight(1f))
                IconButton(onDismiss, enabled = !state.savingBio) { Icon(FeedIcons.close, "Close editor", tint = OwnProfileColors.text) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileAvatar(state.profile?.profileImageUrl, Modifier.size(64.dp))
                Text(state.profile?.displayName.orEmpty(), fontSize = 17.sp, color = OwnProfileColors.text, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ onImage(ProfileImageKind.AVATAR) }, enabled = !state.busy && !state.uploadUncertain && !state.sessionExpired,
                    modifier = Modifier.weight(1f)) { Text("Change photo", color = OwnProfileColors.brand) }
                TextButton({ onImage(ProfileImageKind.COVER) }, enabled = !state.busy && !state.uploadUncertain && !state.sessionExpired,
                    modifier = Modifier.weight(1f)) { Text("Change cover", color = OwnProfileColors.brand) }
            }
            OutlinedTextField(editor, onValueChange = { proposed ->
                val limited = ProfileBio.limit(proposed.text)
                editor = proposed.copy(text = limited,
                    selection = TextRange(proposed.selection.start.coerceIn(0, limited.length), proposed.selection.end.coerceIn(0, limited.length)),
                    composition = if (limited == proposed.text) proposed.composition else null)
                onBio(limited)
            }, enabled = !state.busy && !state.sessionExpired, modifier = Modifier.fillMaxWidth(),
                label = { Text("Bio") }, minLines = 3, maxLines = 5,
                supportingText = { Text("${ProfileBio.count(editor.text)} / ${ProfileBio.MAX_LENGTH}", modifier = Modifier.fillMaxWidth()) })
            state.operationError?.let { Text(it, color = OwnProfileColors.brand, fontSize = 14.sp, lineHeight = 21.sp) }
            Button(onSave, enabled = !state.busy && !state.sessionExpired && state.profile != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OwnProfileColors.brand, contentColor = Color.White)) {
                if (state.savingBio) { OwnProfileSpinner(color = Color.White); Spacer(Modifier.width(10.dp)) }
                Text(if (state.savingBio) "Saving…" else "Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}
