package com.mongosky.app.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.LinkedHashMap

/** Full-width media between a 4:5 portrait and a 1.91:1 landscape. */
internal fun homeFeedAspectRatio(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else (width.toFloat() / height).coerceIn(0.8f, 1.91f)

internal fun homeFeedPreviewWidth(pixels: Int): Int = when {
    pixels <= 480 -> 480
    pixels <= 720 -> 720
    else -> 1080
}

/** Session-owned dimensions only: no bitmaps and no additional image requests.
 * A late decode may paint during a fling, but cannot change an existing card's height.
 */
internal class HomeFeedImageState(private val capacity: Int = 256) {
    private val ratios = LinkedHashMap<String, MutableState<Float>>(16, 0.75f, true)
    private val pending = linkedMapOf<String, Float>()
    var moving by mutableStateOf(false)
        private set
    var generation = 0
        private set
    internal val cachedCount get() = ratios.size

    init { require(capacity > 0) }

    fun aspect(url: String): State<Float> = entry(url)

    private fun entry(url: String): MutableState<Float> {
        ratios[url]?.let { return it }
        if (ratios.size >= capacity) {
            val oldest = ratios.keys.first()
            ratios.remove(oldest); pending.remove(oldest)
        }
        return mutableStateOf(1f).also { ratios[url] = it }
    }

    fun resolved(url: String, width: Int, height: Int, requestGeneration: Int) {
        if (requestGeneration != generation || width <= 0 || height <= 0) return
        val value = homeFeedAspectRatio(width, height)
        val state = entry(url)
        if (moving) pending[url] = value else state.value = value
    }

    fun setScrolling(active: Boolean) {
        moving = active
        if (!active && pending.isNotEmpty()) {
            pending.forEach { (url, value) -> ratios[url]?.value = value }
            pending.clear()
        }
    }

    fun clear() {
        generation++
        ratios.values.forEach { it.value = 1f }
        ratios.clear(); pending.clear(); moving = false
    }
}
