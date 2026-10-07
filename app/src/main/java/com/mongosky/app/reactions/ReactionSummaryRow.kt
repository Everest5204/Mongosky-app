package com.mongosky.app.reactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Web's reserved 34px summary row, including overlapping reaction circles. */
@Composable
internal fun ReactionSummaryRow(summary: ReactionSummary, enabled: Boolean, onClick: () -> Unit) {
    if (summary.total <= 0) return
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 34.dp)) {
        val mobile = maxWidth <= 480.dp
        val inset = if (mobile) 10.dp else 14.dp
        val icons = summary.summaryIcons()
        val iconSize = if (mobile) 19.dp else 20.dp
        val gap = if (mobile) 6.dp else 7.dp
        val iconWidth = if (icons.isEmpty()) 0.dp else iconSize + (iconSize - 6.dp) * (icons.size - 1)
        val textLimit = when {
            maxWidth <= 360.dp -> 180.dp
            mobile -> minOf(240.dp, (maxWidth - 80.dp).coerceAtLeast(0.dp))
            else -> 360.dp
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${summary.summaryText()}, view reactions" }
            .padding(start = inset, end = inset, top = if (mobile) 8.dp else 7.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.width(iconWidth).height(22.dp)) {
                icons.forEachIndexed { index, reaction ->
                    Box(Modifier.align(Alignment.CenterStart).offset(x = (iconSize - 6.dp) * index)
                        .size(iconSize).shadow(1.dp, CircleShape, clip = false)
                        .drawBehind { drawCircle(Color.White, radius = size.minDimension / 2 + 2.dp.toPx()) }
                        .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(reaction.emoji, fontSize = if (mobile) 13.sp else 14.sp, lineHeight = 16.sp)
                    }
                }
            }
            Text(summary.summaryText(), modifier = Modifier.widthIn(max = textLimit),
                color = Color(0xFF475569), fontSize = if (mobile) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (mobile) 15.6.sp else 16.8.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
