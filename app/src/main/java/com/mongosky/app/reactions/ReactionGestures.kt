package com.mongosky.app.reactions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** One pointer owns the whole long-press/slide/release sequence, including outside the button bounds. */
internal fun Modifier.reactionButtonGestures(
    enabled: Boolean,
    picker: ReactionPickerState,
    interactions: MutableInteractionSource,
    toWindow: (Offset) -> Offset?,
    onQuickReact: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: (PostReaction) -> Unit
): Modifier = if (!enabled) this else pointerInput(enabled, picker) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val press = PressInteraction.Press(down.position)
        interactions.tryEmit(press)
        down.consume()
        var releasedNormally = false
        var dragging = false
        try {
            // The pair distinguishes scroll/cancel (a completed null) from the native long-press timeout.
            val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                true to waitForUpOrCancellation()
            }
            if (tap != null) {
                tap.second?.let { up ->
                    up.consume()
                    releasedNormally = true
                    picker.dismiss()
                    onQuickReact()
                }
            } else {
                val held = currentEvent.changes.firstOrNull { it.id == down.id && it.pressed }
                val point = held?.position?.let(toWindow)
                if (point != null && currentEvent.changes.count { it.pressed } == 1) {
                    dragging = true
                    picker.begin(point, viewConfiguration.touchSlop)
                    onLongPress()
                    val up = trackReactionPointer(down.id, picker, toWindow)
                    if (up != null) {
                        releasedNormally = true
                        val releasePoint = toWindow(up.position)
                        if (releasePoint == null) picker.dismiss()
                        else picker.release(releasePoint)?.let(onSelect)
                    }
                }
            }
        } finally {
            interactions.tryEmit(if (releasedNormally) PressInteraction.Release(press) else PressInteraction.Cancel(press))
            if (dragging && !releasedNormally) picker.dismiss()
        }
    }
}

/** A picker left open after a stationary hold also supports an ordinary tap or a new slide. */
internal fun Modifier.reactionPickerGestures(
    picker: ReactionPickerState,
    toWindow: (Offset) -> Offset?,
    onSelect: (PostReaction) -> Unit
): Modifier = pointerInput(picker) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val point = toWindow(down.position)
        if (point == null || picker.snapshot.tracking) {
            picker.dismiss()
            return@awaitEachGesture
        }
        picker.begin(point, viewConfiguration.touchSlop)
        var releasedNormally = false
        try {
            val up = trackReactionPointer(down.id, picker, toWindow)
            if (up != null) {
                releasedNormally = true
                val releasePoint = toWindow(up.position)
                if (releasePoint == null) picker.dismiss()
                else picker.release(releasePoint)?.let(onSelect)
            }
        } finally {
            if (!releasedNormally) picker.dismiss()
        }
    }
}

internal suspend fun AwaitPointerEventScope.trackReactionPointer(
    owner: PointerId, picker: ReactionPickerState, toWindow: (Offset) -> Offset?
): PointerInputChange? {
    while (picker.snapshot.open && picker.snapshot.tracking) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.any { it.id != owner && it.pressed }) return null
        val change = event.changes.firstOrNull { it.id == owner } ?: return null
        if (change.isConsumed) return null
        val point = toWindow(change.position) ?: return null
        picker.move(point)
        change.consume()
        if (!change.pressed) return change
    }
    return null
}
