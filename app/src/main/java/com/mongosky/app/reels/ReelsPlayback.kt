@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("DEPRECATION")
package com.mongosky.app.reels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.net.Uri
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One bounded public-video cache for the application; it contains no API session headers. */
private object ReelsVideoCache {
    private var cache: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.cacheDir, "reels-video"),
        LeastRecentlyUsedCacheEvictor(96L * 1024 * 1024)
    ).also { cache = it }
}

private data class ReelsSources(val playback: DataSource.Factory, val prefetch: CacheDataSource.Factory?)

/** Separates native commands from screen state so release/switch ordering is verifiable. */
internal interface ReelPlayerEngine {
    val player: ExoPlayer? get() = null
    fun load(id: String, url: String)
    fun prepare()
    fun play(ready: Boolean, muted: Boolean)
    fun release()
}

private class ExoReelEngine(override val player: ExoPlayer) : ReelPlayerEngine {
    override fun load(id: String, url: String) {
        player.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(url).build())
    }
    override fun prepare() = player.prepare()
    override fun play(ready: Boolean, muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = ready
    }
    override fun release() {
        player.pause()
        try { player.clearVideoSurface() } finally { player.release() }
    }
}

internal class ReelPlayback(
    private val engine: ReelPlayerEngine,
    private val reportError: (Exception) -> Unit = { Log.e("MongoskyReels", "Video player command failed", it) }
) {
    constructor(player: ExoPlayer) : this(ExoReelEngine(player))
    val player get() = engine.player
    var buffering by mutableStateOf(true)
        private set
    var error by mutableStateOf(false)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var manuallyPaused by mutableStateOf(false)
        private set
    private var currentId: String? = null
    private var currentUrl: String? = null
    private var allowed = false
    private var muted = false
    private var released = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (released) return
            buffering = player.playbackState == Player.STATE_BUFFERING
            error = player.playerError != null
        }
        override fun onRenderedFirstFrame() { if (!released) firstFrame = true }
    }
    init { player?.addListener(listener); player?.repeatMode = Player.REPEAT_MODE_ONE }
    fun show(reel: Reel) {
        if (released || currentId == reel.id && currentUrl == reel.videoUrl) return
        currentId = reel.id; currentUrl = reel.videoUrl
        manuallyPaused = false; firstFrame = false; buffering = true; error = false
        command {
            engine.play(false, muted)
            engine.load(reel.id, reel.videoUrl)
            engine.prepare()
            engine.play(allowed, muted)
        }
    }
    fun setPlaying(allowed: Boolean, muted: Boolean) {
        if (released) return
        this.allowed = allowed
        this.muted = muted
        command { engine.play(allowed && !manuallyPaused && !error, muted) }
    }
    fun pause() { if (!released) command { engine.play(false, muted) } }
    fun toggle() {
        if (!released && allowed) {
            manuallyPaused = !manuallyPaused
            command { engine.play(!manuallyPaused && !error, muted) }
        }
    }
    fun retry() {
        if (released) return
        val id = currentId ?: return
        val url = currentUrl ?: return
        error = false; buffering = true; firstFrame = false
        command {
            engine.play(false, muted); engine.load(id, url); engine.prepare()
            engine.play(allowed && !manuallyPaused, muted)
        }
    }
    fun release() {
        if (released) return
        released = true
        player?.removeListener(listener)
        try { engine.release() } catch (error: Exception) { reportError(error) }
    }
    private inline fun command(action: () -> Unit) {
        try { action() } catch (failure: Exception) {
            error = true; buffering = false
            reportError(failure)
            try { engine.play(false, muted) } catch (_: Exception) { }
        }
    }
}

/** A cancelled blocking HTTP read must leave before another prefetch can enter. */
internal class ReelPrefetchGate {
    private val mutex = Mutex()
    suspend fun run(block: suspend () -> Unit) = mutex.withLock { block() }
}

@Composable
internal fun rememberReelPlayback(active: Reel?, next: Reel?, allowed: Boolean, muted: Boolean): ReelPlayback? {
    val context = LocalContext.current.applicationContext
    val prefetchGate = remember(context) { ReelPrefetchGate() }
    val sources by produceState<ReelsSources?>(null, context) {
        value = withContext(Dispatchers.IO) {
            val upstream = DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(10_000)
                .setAllowCrossProtocolRedirects(false).setUserAgent("Mongosky-Reels")
            val factory = runCatching {
                CacheDataSource.Factory().setCache(ReelsVideoCache.get(context)).setUpstreamDataSourceFactory(upstream)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            }.getOrNull()
            ReelsSources(factory ?: upstream, factory)
        }
    }
    val playback = remember(sources, context) { sources?.let {
        ReelPlayback(ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setAudioAttributes(AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 10_000, 350, 750)
                .setTargetBufferBytes(8 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(it.playback)).build())
    } }
    DisposableEffect(playback) { onDispose { playback?.release() } }
    LaunchedEffect(playback, active?.id, active?.videoUrl) {
        if (active != null) playback?.show(active)
    }
    DisposableEffect(playback, allowed, muted, active?.id) {
        playback?.setPlaying(allowed && active != null, muted)
        onDispose { playback?.pause() }
    }
    LaunchedEffect(sources, next?.videoUrl, allowed) {
        val factory = sources?.prefetch ?: return@LaunchedEffect
        val url = next?.videoUrl ?: return@LaunchedEffect
        // Backend videos are progressive files. Playlist manifests use normal playback loading.
        if (!allowed || url.substringBefore('?').endsWith(".m3u8", true)) return@LaunchedEffect
        delay(150) // Skip prefetch work for a swipe that immediately moves on again.
        withContext(Dispatchers.IO) {
            prefetchGate.run {
                suspendCancellableCoroutine<Unit> { continuation ->
                    try {
                        val spec = DataSpec.Builder().setUri(Uri.parse(url)).setLength(2L * 1024 * 1024).build()
                        val writer = CacheWriter(factory.createDataSource(), spec, ByteArray(32 * 1024), null)
                        continuation.invokeOnCancellation { writer.cancel() }
                        writer.cache()
                    } catch (_: Exception) { /* A failed prefetch never blocks playback. */ }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }
    return playback
}
