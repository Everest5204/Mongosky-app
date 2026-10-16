package com.mongosky.app.comments

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Anchored native dropdown opens beneath the three dots; Back dismisses this menu first. */
@Composable
internal fun CommentMenu(comment: PostComment, state: CommentsState, controls: CommentsController, enabled: Boolean) {
    val expanded = state.menuId == comment.id
    Box {
        Box(Modifier.size(28.dp).clip(CircleShape)
            .background(AppColors.surface(if (expanded) CommentColors.line else Color.Transparent))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = appText("Comment options")) {
                if (expanded) controls.closeMenu() else controls.openMenu(comment)
            }.semantics { contentDescription = appText("Comment options") }, contentAlignment = Alignment.Center) {
            Icon(CommentIcons.more, null, Modifier.size(18.dp), tint = AppColors.content(Color(0xFF6B7280)))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = controls::closeMenu,
            offset = DpOffset(0.dp,6.dp), modifier = Modifier.widthIn(min = 150.dp),
            shape = RoundedCornerShape(12.dp), containerColor = AppColors.surface(Color.White), tonalElevation = 0.dp) {
            DropdownMenuItem(text = { Text(appText("Edit"), fontSize = 13.sp) },
                leadingIcon = { Icon(CommentIcons.edit, null, Modifier.size(17.dp)) }, enabled = enabled,
                onClick = { controls.closeMenu(); controls.editComment(comment) })
            DropdownMenuItem(text = { Text(appText("Delete"), color = AppColors.content(CommentColors.active), fontSize = 13.sp) },
                leadingIcon = { Icon(CommentIcons.delete, null, Modifier.size(17.dp), tint = AppColors.content(CommentColors.active)) }, enabled = enabled,
                onClick = { controls.closeMenu(); controls.deleteComment(comment) })
        }
    }
}
