package com.mongosky.app.reactions

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mongosky.app.post.FeedColors
import kotlin.math.roundToInt

@Composable
internal fun ReactionPicker(picker: ReactionPickerState, active: PostReaction?, onSelect: (PostReaction) -> Unit) {
    val snapshot = picker.snapshot
    if (!snapshot.open) return
    val density = LocalDensity.current
    val width = minOf(280.dp, (LocalConfiguration.current.screenWidthDp.dp - 16.dp).coerceAtLeast(120.dp))
    val provider = remember(picker, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val position = reactionPopupPosition(anchorBounds, windowSize, popupContentSize,
                    with(density) { 8.dp.roundToPx() }, with(density) { 8.dp.roundToPx() }, layoutDirection == LayoutDirection.Rtl)
                picker.position(position)
                return position
            }
        }
    }
    val select by rememberUpdatedState(onSelect)
    Popup(popupPositionProvider = provider, onDismissRequest = picker::dismiss,
        properties = PopupProperties(focusable = !snapshot.tracking, dismissOnBackPress = true,
            dismissOnClickOutside = true, clippingEnabled = true)) {
        Box(Modifier.width(width).height(112.dp)
            .reactionPickerGestures(picker, toWindow = { point -> pickerOrigin(picker, point) }, onSelect = { select(it) })
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) { picker.dismiss(); true } else false
            }
            .semantics { paneTitle = "Choose reaction" }) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp)).background(AppColors.surface(Color.White), RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PostReaction.entries.forEach { reaction ->
                    val highlighted = snapshot.hovered == reaction
                    val scale by animateFloatAsState(if (highlighted) 1.52f else 1f,
                        tween(110, easing = FastOutSlowInEasing), label = "reaction scale")
                    val lift by animateFloatAsState(if (highlighted) 10f else 0f,
                        tween(110, easing = FastOutSlowInEasing), label = "reaction lift")
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .onGloballyPositioned { picker.measure(reaction, it.boundsInRoot(), with(density) { 8.dp.toPx() }) }
                        .onFocusChanged { if (it.isFocused) picker.focus(reaction) else picker.blur(reaction) }
                        .onKeyEvent { event ->
                            if (event.isReactionActivate() && event.type == KeyEventType.KeyUp) {
                                picker.choose(reaction)?.let { select(it) }; true
                            } else event.isReactionActivate()
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = reaction.label
                            selected = active == reaction
                            onClick(label = "React with ${reaction.label}") {
                                picker.choose(reaction)?.let { select(it) }; true
                            }
                        }.focusable(), contentAlignment = Alignment.Center) {
                        if (active == reaction) Box(Modifier.size(38.dp).background(AppColors.surface(FeedColors.line), CircleShape))
                        // Emoji is a graphic: maintain its dp size while the readable tooltip respects font scaling.
                        Text(reaction.emoji, fontSize = (32f / density.fontScale).sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationY = -lift * density.density
                            }.clearAndSetSemantics {})
                    }
                }
            }
            snapshot.hovered?.let { reaction ->
                ReactionTooltip(reaction.label, picker.localCenter(reaction) ?: with(density) { width.toPx() / 2f })
            }
        }
    }
}

@Composable
private fun ReactionTooltip(label: String, center: Float) {
    Layout(content = {
        Text(appText(label), color = AppColors.content(Color.White), fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.background(AppColors.surface(Color(0xE6222222)), RoundedCornerShape(14.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp).clearAndSetSemantics {})
    }, modifier = Modifier.fillMaxWidth().height(48.dp)) { measurables, constraints ->
        val tooltip = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            tooltip.place((center - tooltip.width / 2f).roundToInt().coerceIn(0, constraints.maxWidth - tooltip.width), 0)
        }
    }
}

internal fun KeyEvent.isReactionActivate(): Boolean = key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar || key == Key.DirectionCenter

private fun pickerOrigin(picker: ReactionPickerState, point: Offset): Offset? = picker.toWindow(point)
