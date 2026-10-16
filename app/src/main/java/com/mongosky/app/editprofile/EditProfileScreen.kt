package com.mongosky.app.editprofile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.language.appProfileErrorText
import com.mongosky.app.profile.ProfileImageKind

@Composable
internal fun EditProfileScreen(state: EditProfileState, onBio: (String) -> Unit, onImage: (ProfileImageKind) -> Unit,
    onSave: () -> Unit, onClose: () -> Unit, onReload: () -> Unit, onUseSaved: () -> Unit, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppColors.surface(EditProfileColors.background)), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().background(AppColors.surface(Color.White))
            .statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose, enabled = !state.saving, modifier = Modifier.size(48.dp)) {
                    Icon(EditProfileIcons.back, stringResource(R.string.edit_profile_back), Modifier.size(25.dp), tint = AppColors.content(EditProfileColors.text))
                }
                Text(stringResource(R.string.edit_profile_title), Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.content(EditProfileColors.text))
                Spacer(Modifier.size(48.dp))
            }
            HorizontalDivider(color = AppColors.outline(EditProfileColors.border))
            if (state.profile == null) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = AppColors.content(EditProfileColors.green), strokeWidth = 2.dp)
                        Text(stringResource(R.string.edit_profile_loading), Modifier.padding(top = 12.dp), color = AppColors.content(EditProfileColors.muted))
                    } else {
                        Text(stringResource(R.string.edit_profile_load_error), fontWeight = FontWeight.Bold, color = AppColors.content(EditProfileColors.text))
                        state.error?.let { Text(appProfileErrorText(it), Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, color = AppColors.content(EditProfileColors.muted)) }
                        TextButton(if (state.sessionExpired) onSignIn else onReload) {
                            Text(stringResource(if (state.sessionExpired) R.string.edit_profile_sign_in else R.string.edit_profile_retry), color = AppColors.content(EditProfileColors.green))
                        }
                    }
                }
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val inset = if (maxWidth < 380.dp) 8.dp else 12.dp
                // SpaceBetween anchors short forms; all controls remain scrollable with a keyboard or large fonts.
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.fillMaxWidth()) {
                        EditProfileMedia(state, onImage)
                        Box(Modifier.fillMaxWidth().padding(horizontal = inset).padding(top = 14.dp)) { EditProfileBio(state, onBio) }
                        if (state.error != null || state.uncertainImages.isNotEmpty()) EditProfileNotice(state, onReload, onUseSaved, onSignIn,
                            Modifier.padding(horizontal = inset).padding(top = 12.dp))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = inset).padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.fill(EditProfileColors.green), contentColor = Color.White,
                                disabledContainerColor = AppColors.fill(if (state.saving) EditProfileColors.green else EditProfileColors.disabled),
                                disabledContentColor = if (state.saving) Color.White else AppColors.content(EditProfileColors.disabledText))) {
                            if (state.saving) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(stringResource(if (state.saving) R.string.edit_profile_saving else R.string.edit_profile_save), fontSize = 16.sp,
                                lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        }
                        TextButton(onClose, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(R.string.edit_profile_cancel), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = AppColors.content(EditProfileColors.muted))
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun EditProfileNotice(state: EditProfileState, onReload: () -> Unit, onUseSaved: () -> Unit, onSignIn: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(AppColors.surface(Color(0xFFFEF2F2)), RoundedCornerShape(11.dp))
        .border(1.dp, AppColors.outline(Color(0xFFFECACA)), RoundedCornerShape(11.dp)).padding(13.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (state.hasSavedChanges && state.error != null) Text(stringResource(R.string.edit_profile_partial),
            color = AppColors.content(Color(0xFF991B1B)), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        state.error?.let { Text(appProfileErrorText(it), color = AppColors.content(Color(0xFF991B1B)), fontSize = 13.sp, lineHeight = 19.sp) }
        if (state.uncertainImages.isNotEmpty()) {
            Text(stringResource(if (state.reviewingSavedImages) R.string.edit_profile_reviewed else R.string.edit_profile_uncertain),
                color = AppColors.content(Color(0xFF991B1B)), fontSize = 13.sp, lineHeight = 19.sp)
            TextButton(onReload, enabled = !state.loading && !state.saving && !state.sessionExpired) {
                Text(stringResource(if (state.loading) R.string.edit_profile_checking else R.string.edit_profile_check_saved), color = AppColors.content(EditProfileColors.green))
            }
            if (state.reviewingSavedImages) TextButton(onUseSaved, enabled = !state.loading && !state.saving && !state.sessionExpired) {
                Text(stringResource(R.string.edit_profile_use_saved), color = AppColors.content(EditProfileColors.green))
            }
        }
        if (state.sessionExpired) TextButton(onSignIn, enabled = !state.saving) { Text(stringResource(R.string.edit_profile_sign_in), color = AppColors.content(EditProfileColors.green)) }
    }
}
@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Preview(name = "Small phone large font", widthDp = 320, heightDp = 568, fontScale = 1.5f)
@Preview(name = "Tablet", widthDp = 800, heightDp = 1000)
@Composable
private fun EditorPreview() { MaterialTheme { EditProfileScreen(EditProfileState(ownerId = "507f1f77bcf86cd799439011",
    profile = EditableProfile("507f1f77bcf86cd799439011", "Mongosky", "User")), {}, {}, {}, {}, {}, {}, {}) } }
