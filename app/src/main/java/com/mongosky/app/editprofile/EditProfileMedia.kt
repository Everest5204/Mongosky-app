package com.mongosky.app.editprofile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.mongosky.app.R
import com.mongosky.app.darkmode.AppColors
import com.mongosky.app.profile.ProfileImageKind

@Composable
internal fun EditProfileMedia(state: EditProfileState, onImage: (ProfileImageKind) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val small = maxWidth < 380.dp
        val inset = if (small) 8.dp else 12.dp
        val avatarSize = if (small) 116.dp else 128.dp
        val overlap = if (small) 70.dp else 78.dp
        val coverHeight = (maxWidth - inset * 2) / 2.2f
        val changeCover = stringResource(R.string.edit_profile_change_cover)
        val changePhoto = stringResource(R.string.edit_profile_change_photo)
        Layout(modifier = Modifier.fillMaxWidth().padding(top = if (small) 8.dp else 10.dp), content = {
            Box(Modifier.padding(horizontal = inset).fillMaxWidth().height(coverHeight).clip(RoundedCornerShape(if (small) 15.dp else 18.dp))
                .background(Brush.linearGradient(AppColors.surfaces(listOf(Color(0xFFDCE8EF), Color(0xFFD7EEE4)))))) {
                state.coverPreview?.let { AsyncImage(it, stringResource(R.string.edit_profile_cover_description), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0x660F172A)))))
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 7.dp).widthIn(min = 82.dp)
                    .clip(RoundedCornerShape(12.dp)).clickable(enabled = state.canEdit, role = Role.Button, onClickLabel = changeCover) { onImage(ProfileImageKind.COVER) }
                    .padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CameraBadge(Modifier.size(if (small) 39.dp else 42.dp), state.canEdit)
                    Text(changeCover, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp).widthIn(max = 110.dp))
                }
            }
            Column(Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(16.dp))
                .clickable(enabled = state.canEdit, role = Role.Button, onClickLabel = changePhoto) { onImage(ProfileImageKind.AVATAR) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(avatarSize + 30.dp).height(avatarSize), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(avatarSize).shadow(8.dp, CircleShape).clip(CircleShape).background(AppColors.surface(Color.White))
                        .padding(5.dp).clip(CircleShape).background(Color(0xFF07927C)), contentAlignment = Alignment.Center) {
                        if (state.avatarPreview != null) AsyncImage(state.avatarPreview, stringResource(R.string.edit_profile_photo_description),
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(EditProfileIcons.user, null, Modifier.size(48.dp), tint = Color(0xFFEAFFF7))
                    }
                    CameraBadge(Modifier.align(Alignment.BottomEnd).size(if (small) 38.dp else 40.dp)
                        .border(1.dp, AppColors.outline(EditProfileColors.border), CircleShape), state.canEdit)
                }
                Text(changePhoto, Modifier.padding(top = 10.dp, bottom = 2.dp), fontSize = 14.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = AppColors.content(EditProfileColors.muted))
            }
        }) { children, constraints ->
            val cover = children[0].measure(Constraints.fixed(constraints.maxWidth, coverHeight.roundToPx()))
            val avatar = children[1].measure(Constraints(maxWidth = constraints.maxWidth))
            val top = cover.height - overlap.roundToPx()
            // Actual text height keeps large fonts and Bengali labels away from the Bio card.
            layout(constraints.maxWidth, top + avatar.height) {
                cover.placeRelative(0, 0); avatar.placeRelative((constraints.maxWidth - avatar.width) / 2, top)
            }
        }
    }
}
@Composable
private fun CameraBadge(modifier: Modifier, enabled: Boolean) {
    Box(modifier.clip(CircleShape).background(AppColors.surface(Color.White)), contentAlignment = Alignment.Center) {
        Icon(EditProfileIcons.camera, null, Modifier.size(20.dp), tint = AppColors.content(if (enabled) EditProfileColors.text else EditProfileColors.muted))
    }
}
