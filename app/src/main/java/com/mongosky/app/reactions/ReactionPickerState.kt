package com.mongosky.app.reactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal data class ReactionPickerSnapshot(
    val open: Boolean = false,
    val tracking: Boolean = false,
    val hovered: PostReaction? = null
)

/** A selection is committed only on release over a measured target, or an explicit accessible click. */
internal class ReactionPickerState {
    var snapshot by mutableStateOf(ReactionPickerSnapshot())
        private set
    private var popupOrigin: Offset? = null
    private val cells = arrayOfNulls<Rect>(PostReaction.entries.size)
    private var lastPoint: Offset? = null
    private var startPoint: Offset? = null
    private var touchSlop = 0f
    private var moved = false
    private var visitedChoice = false

    fun begin(point: Offset, slop: Float) {
        if (!snapshot.open) clearGeometry()
        lastPoint = point
        startPoint = point
        touchSlop = slop
        moved = false
        visitedChoice = false
        snapshot = ReactionPickerSnapshot(open = true, tracking = true)
        move(point)
    }

    fun showChoices() {
        if (!snapshot.open) clearGeometry()
        lastPoint = null
        startPoint = null
        moved = false
        visitedChoice = false
        snapshot = ReactionPickerSnapshot(open = true)
    }

    fun position(origin: IntOffset) {
        if (!snapshot.open) return
        popupOrigin = Offset(origin.x.toFloat(), origin.y.toFloat())
        refreshHover()
    }

    fun measure(reaction: PostReaction, bounds: Rect, upwardReach: Float) {
        if (!snapshot.open) return
        // A fixed hit target includes the raised emoji; animation never changes target boundaries.
        cells[reaction.ordinal] = Rect(bounds.left, bounds.top - upwardReach, bounds.right, bounds.bottom)
        refreshHover()
    }

    fun localCenter(reaction: PostReaction): Float? = cells[reaction.ordinal]?.center?.x

    fun toWindow(point: Offset): Offset? = popupOrigin?.let { point + it }

    fun move(point: Offset) {
        if (!snapshot.open || !snapshot.tracking) return
        lastPoint = point
        startPoint?.let { if ((point - it).getDistanceSquared() > touchSlop * touchSlop) moved = true }
        refreshHover()
    }

    private fun refreshHover() {
        if (!snapshot.open || !snapshot.tracking) return
        val hovered = lastPoint?.let(::hit)
        if (hovered != null) visitedChoice = true
        if (snapshot.hovered != hovered) snapshot = snapshot.copy(hovered = hovered)
    }

    private fun hit(point: Offset): PostReaction? {
        val origin = popupOrigin ?: return null
        val local = point - origin
        return PostReaction.entries.firstOrNull { cells[it.ordinal]?.contains(local) == true }
    }

    fun release(point: Offset): PostReaction? {
        if (!snapshot.open || !snapshot.tracking) return null
        move(point)
        val selected = snapshot.hovered
        // Holding then lifting on the original button keeps the existing tap-to-choose option.
        val keepChoices = selected == null && !moved && !visitedChoice
        lastPoint = null
        startPoint = null
        snapshot = ReactionPickerSnapshot(open = keepChoices)
        if (!keepChoices) clearGeometry()
        return selected
    }

    fun choose(reaction: PostReaction): PostReaction? {
        if (!snapshot.open || snapshot.tracking) return null
        dismiss()
        return reaction
    }

    fun focus(reaction: PostReaction) {
        if (snapshot.open && !snapshot.tracking) snapshot = snapshot.copy(hovered = reaction)
    }

    fun blur(reaction: PostReaction) {
        if (!snapshot.tracking && snapshot.hovered == reaction) snapshot = snapshot.copy(hovered = null)
    }

    fun dismiss() {
        snapshot = ReactionPickerSnapshot()
        lastPoint = null
        startPoint = null
        moved = false
        visitedChoice = false
        clearGeometry()
    }

    private fun clearGeometry() {
        popupOrigin = null
        cells.fill(null)
    }
}

/** Window coordinates, not dp or screen coordinates: works beside either edge and in split screen. */
internal fun reactionPopupPosition(
    anchor: IntRect, window: IntSize, popup: IntSize, margin: Int, gap: Int, rtl: Boolean
): IntOffset {
    val maxX = (window.width - popup.width - margin).coerceAtLeast(0)
    val minX = margin.coerceAtMost(maxX)
    val preferredX = if (rtl) anchor.right - popup.width else anchor.left
    val maxY = (window.height - popup.height - margin).coerceAtLeast(0)
    val minY = margin.coerceAtMost(maxY)
    val above = anchor.top - gap - popup.height
    val below = anchor.bottom + gap
    val y = when {
        above >= minY -> above
        below <= maxY -> below
        else -> above.coerceIn(minY, maxY)
    }
    return IntOffset(preferredX.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
}
