package com.mongosky.app.getverified

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
internal fun GetVerifiedIllustration() {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 192.dp)
        val scale = side / 192.dp
        Box(Modifier.size(side).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val unit = size.width / 192f
                drawCircle(Color(0xFFFFF1F2), radius = 96f * unit)
                drawCircle(Color(0xFFFFE4E6), radius = 76f * unit, style = Stroke(width = unit))
                drawCircle(Color(0xFFFFCCD3), radius = 6f * unit, center = Offset(22f * unit, 26f * unit))
                drawCircle(Color(0xFFFFA1AD), radius = 4f * unit, center = Offset(176f * unit, 36f * unit))
                drawCircle(Color(0xFFFFA1AD), radius = 4f * unit, center = Offset(12f * unit, 164f * unit))
            }
            Icon(GetVerifiedIcons.badge, contentDescription = null, tint = Color.Unspecified,
                modifier = Modifier.size(96.dp * scale))
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 20.dp * scale).size(48.dp * scale)
                .shadow(1.dp, RoundedCornerShape(16.dp * scale))
                .background(Color.White, RoundedCornerShape(16.dp * scale))
                .border(1.dp, Color(0xFFFFE4E6), RoundedCornerShape(16.dp * scale)),
                contentAlignment = Alignment.Center) {
                Icon(GetVerifiedIcons.users, contentDescription = null, tint = Color(0xFFEC003F),
                    modifier = Modifier.size(23.dp * scale))
            }
        }
    }
}
