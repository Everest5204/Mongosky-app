package com.mongosky.app.profile

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.mongosky.app.post.FeedIcons
import com.mongosky.app.profile.ProfileGalleryAsset
import com.mongosky.app.profile.ownProfileLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Exactly one player exists after a tap; it pauses offscreen and releases on close. */
@OptIn(UnstableApi::class)
@Composable
internal fun OwnProfileVideoViewer(asset: ProfileGalleryAsset, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { context.ownProfileLifecycleOwner() }
    var savedPosition by rememberSaveable(asset.key) { mutableStateOf(0L) }
    val player = remember(context, asset.key) {
        ExoPlayer.Builder(context.applicationContext).setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true).build()
    }
    var playing by remember(player) { mutableStateOf(false) }
    var playRequested by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(true) }
    var duration by remember(player) { mutableStateOf(0L) }
    var position by remember(player) { mutableStateOf(savedPosition) }
    var error by remember(player) { mutableStateOf(false) }
    var seeking by remember(player) { mutableStateOf<Float?>(null) }
    var started by remember(owner) { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true) }

    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                playRequested = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                buffering = player.playbackState == Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0)
                position = player.currentPosition.coerceAtLeast(0)
                savedPosition = position
                error = player.playerError != null
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    started = false; savedPosition = player.currentPosition.coerceAtLeast(0); player.pause()
                }
                else -> Unit
            }
        }
        player.addListener(listener); owner?.lifecycle?.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(asset.media.url), savedPosition)
        player.playWhenReady = false
        player.prepare()
        onDispose {
            savedPosition = player.currentPosition.coerceAtLeast(0)
            owner?.lifecycle?.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(player, playing, started) {
        if (playing && started) while (isActive) {
            if (seeking == null) { position = player.currentPosition.coerceAtLeast(0); savedPosition = position }
            delay(500)
        }
    }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(FeedIcons.close, "Close video", tint = Color.White) }
                Text(asset.caption.ifBlank { "Video" }, color = Color.White, maxLines = 2, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit)
                if (buffering) OwnProfileSpinner(color = Color.White)
                if (error) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not play this video.", color = Color.White, modifier = Modifier.padding(16.dp))
                    TextButton({ error = false; player.prepare() }) { Text("Retry", color = Color.White) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ if (player.isPlaying || player.playWhenReady) player.pause() else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                } }, enabled = started && !error) {
                    Icon(if (playRequested) OwnProfileIcons.pause else OwnProfileIcons.play,
                        if (playRequested) "Pause" else "Play", tint = Color.White)
                }
                Slider(value = seeking ?: if (duration > 0) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f,
                    onValueChange = { seeking = it }, onValueChangeFinished = {
                        seeking?.let { player.seekTo((it * duration).toLong().coerceIn(0L, duration)) }; seeking = null
                    }, enabled = started && !error && duration > 0, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = OwnProfileColors.brand))
                Text("${videoTime(position)} / ${videoTime(duration)}", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp))
            }
        }
    }
}
private fun videoTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
