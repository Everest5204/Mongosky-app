package com.mongosky.app.reactions

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mongosky.app.post.FeedColors
import com.mongosky.app.post.FeedIcons

@Composable
internal fun ReactionButton(state: PostReactionState, enabled: Boolean, onReact: (PostReaction) -> Unit, modifier: Modifier) {
    val active = state.summary.currentReaction
    val interactive = enabled && state.loaded && !state.saving
    val picker = remember { ReactionPickerState() }
    val interactions = remember { MutableInteractionSource() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val react by rememberUpdatedState(onReact)
    val quickReact by rememberUpdatedState<() -> Unit>({ if (interactive) react(active ?: PostReaction.LOVE) })
    val select by rememberUpdatedState<(PostReaction) -> Unit>({ if (interactive) react(it) })

    LaunchedEffect(interactive) { if (!interactive) picker.dismiss() }
    LaunchedEffect(picker.snapshot.hovered) {
        if (picker.snapshot.tracking && picker.snapshot.hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    DisposableEffect(lifecycle, picker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) picker.dismiss()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); picker.dismiss() }
    }
    BackHandler(enabled = picker.snapshot.open) { picker.dismiss() }

    Box(modifier) {
        Row(Modifier.fillMaxWidth().height(54.dp)
            .onGloballyPositioned { coordinates = it }
            .indication(interactions, ripple())
            .reactionButtonGestures(interactive, picker, interactions,
                toWindow = { point -> coordinates?.takeIf { it.isAttached }?.localToWindow(point) },
                onQuickReact = { quickReact() },
                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onSelect = { select(it) })
            .onKeyEvent { event ->
                when {
                    !interactive -> false
                    event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> { picker.showChoices(); true }
                    event.isReactionActivate() && event.type == KeyEventType.KeyUp -> { picker.dismiss(); quickReact(); true }
                    else -> event.isReactionActivate()
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = when {
                    state.saving -> "Saving reaction"
                    active == null -> "Love. Hold and slide to choose a reaction"
                    else -> "${active.label} selected. Hold and slide to choose a reaction"
                }
                if (!interactive) disabled()
                else {
                    onClick { picker.dismiss(); quickReact(); true }
                    onLongClick(label = "Choose reaction") { picker.showChoices(); true }
                }
            }.focusable(interactive, interactions),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (active == null) Icon(FeedIcons.heart, null, modifier = Modifier.size(22.dp), tint = AppColors.content(FeedColors.muted))
            else Text(active.emoji, fontSize = 21.sp)
            Spacer(Modifier.width(6.dp))
            Text(active?.label ?: "Love", color = AppColors.content(if (active != null) FeedColors.brand else FeedColors.muted),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (interactive) ReactionPicker(picker, active, onSelect = { select(it) })
    }
}
