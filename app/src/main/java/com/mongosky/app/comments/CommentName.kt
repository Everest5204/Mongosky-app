package com.mongosky.app.comments

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mongosky.app.profile.profileLink

@Composable
internal fun CommentName(comment: PostComment, verified: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val name = comment.author.displayName.ifBlank { "Mongosky User" }
    val label = remember(name, verified) { buildAnnotatedString {
        append(name)
        if (verified) { append("\u00a0"); appendInlineContent("verified") }
    } }
    val size = with(LocalDensity.current) { 14.sp.toDp() }
    val inline = if (!verified) emptyMap() else mapOf("verified" to InlineTextContent(
        Placeholder(1.1.em,1.1.em,PlaceholderVerticalAlign.TextCenter)) {
        Icon(CommentIcons.verified,"Verified account",Modifier.size(size),tint=Color.Unspecified)
    })
    Text(label, inlineContent = inline, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold,
        color = CommentColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = modifier.profileLink(comment.author.id,comment.author,beforeOpen=onOpen))
}
