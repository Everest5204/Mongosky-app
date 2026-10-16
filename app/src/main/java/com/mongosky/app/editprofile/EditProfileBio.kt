package com.mongosky.app.editprofile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.profile.ProfileBio

@Composable
internal fun EditProfileBio(state: EditProfileState, onBio: (String) -> Unit) {
    var editor by rememberSaveable(state.ownerId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.bio, TextRange(state.bio.length))) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(state.bio) {
        if (editor.text != state.bio) editor = editor.copy(text = state.bio, composition = null,
            selection = TextRange(editor.selection.start.coerceIn(0, state.bio.length), editor.selection.end.coerceIn(0, state.bio.length)))
    }
    val count = ProfileBio.count(state.bio)
    val label = stringResource(R.string.edit_profile_bio)
    val remaining = stringResource(R.string.edit_profile_bio_remaining, (ProfileBio.MAX_LENGTH - count).coerceAtLeast(0))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.surface(Color.White))
        .border(1.dp, AppColors.outline(EditProfileColors.border), RoundedCornerShape(16.dp))) {
        Text(label, Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp), fontSize = 16.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.ExtraBold, color = AppColors.content(EditProfileColors.text))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp).fillMaxWidth().heightIn(min = 116.dp)
            .clip(RoundedCornerShape(12.dp)).background(AppColors.surface(if (focused) Color.White else EditProfileColors.field))
            .border(1.dp, AppColors.outline(if (focused) EditProfileColors.green else Color(0xFFD9E0E7)), RoundedCornerShape(12.dp))) {
            BasicTextField(editor, { value ->
                val text = ProfileBio.limit(value.text)
                editor = value.copy(text = text, selection = TextRange(value.selection.start.coerceIn(0, text.length), value.selection.end.coerceIn(0, text.length)),
                    composition = if (text == value.text) value.composition else null)
                onBio(text)
            }, enabled = state.canEdit, minLines = 3, maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 5.dp)
                    .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.content(EditProfileColors.text), fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(AppColors.content(EditProfileColors.green)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field -> Box { if (editor.text.isEmpty()) Text(stringResource(R.string.edit_profile_bio_hint),
                    fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.content(Color(0xFF98A2B3))); field() } })
            Text(stringResource(R.string.edit_profile_bio_counter, count, ProfileBio.MAX_LENGTH),
                Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 10.dp).semantics { contentDescription = remaining },
                fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                color = AppColors.content(if (ProfileBio.MAX_LENGTH - count <= 10) EditProfileColors.warning else Color(0xFF475569)))
        }
        HorizontalDivider(color = AppColors.outline(Color(0xFFEDF1F5)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(AppColors.surface(Color(0xFFF1F5F9))), contentAlignment = Alignment.Center) {
                Icon(EditProfileIcons.globe, null, Modifier.size(18.dp), tint = AppColors.content(Color(0xFF475569)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.edit_profile_public), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.content(Color(0xFF334155)))
                Text(stringResource(R.string.edit_profile_public_hint), fontSize = 12.sp, lineHeight = 17.sp, color = AppColors.content(EditProfileColors.muted))
            }
        }
    }
}
