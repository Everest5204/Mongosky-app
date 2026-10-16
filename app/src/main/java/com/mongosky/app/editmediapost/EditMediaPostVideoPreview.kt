package com.mongosky.app.editmediapost

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import com.mongosky.app.editpost.*

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** No autoplay or player allocation on opening the editor. Only one active preview is created on tap. */
@OptIn(UnstableApi::class)
@Composable
internal fun EditMediaPostVideoPreview(url: String, enabled: Boolean) {
    var requested by rememberSaveable(url) { mutableStateOf(false) }
    if (!requested) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FilledIconButton({ requested = true }, enabled = enabled, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.surface(Color(0xC414171C)), contentColor = AppColors.content(Color.White))) {
            Icon(EditPostIcons.play, appDescription("Preview video"), modifier = Modifier.size(26.dp))
        }
    } else EditMediaPostPlayer(url, enabled)
}

@OptIn(UnstableApi::class)
@Composable
private fun EditMediaPostPlayer(url: String, enabled: Boolean) {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var savedPosition by rememberSaveable(url) { mutableLongStateOf(0L) }
    val player = remember(app, url) { ExoPlayer.Builder(app).setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true).build() }
    var playing by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(true) }
    var error by remember(player) { mutableStateOf(false) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var position by remember(player) { mutableLongStateOf(savedPosition) }
    var seek by remember(player) { mutableStateOf<Float?>(null) }
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                buffering = player.playbackState == Player.STATE_BUFFERING
                error = player.playerError != null
                duration = player.duration.coerceAtLeast(0)
                position = player.currentPosition.coerceAtLeast(0); savedPosition = position
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> { started = false; player.pause() }
                else -> Unit
            }
        }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(url), savedPosition); player.playWhenReady = false; player.prepare()
        onDispose { savedPosition = player.currentPosition.coerceAtLeast(0)
            owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(enabled, player) { if (!enabled) player.pause() }
    LaunchedEffect(playing, started, player) {
        if (playing && started) while (isActive) {
            position = player.currentPosition.coerceAtLeast(0); savedPosition = position
            delay(500)
        }
    }
    Column(Modifier.fillMaxSize().background(AppColors.surface(Color.Black))) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit)
            if (buffering && !error) CircularProgressIndicator(Modifier.size(24.dp), color = AppColors.content(Color.White), strokeWidth = 2.dp)
            if (error) TextButton({ player.prepare() }, enabled = enabled) { Text(appText("Could not play video. Retry"), color = AppColors.content(Color.White)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (player.isPlaying || player.playWhenReady) player.pause() else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            } }, enabled = enabled && started && !error) {
                Icon(if (playing) EditPostIcons.pause else EditPostIcons.play, appDescription(if (playing) "Pause video" else "Play video"), tint = AppColors.content(Color.White))
            }
            Slider(value = seek ?: if (duration > 0) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { seek = it }, onValueChangeFinished = { seek?.let { player.seekTo((it * duration).toLong()) }; seek = null },
                enabled = enabled && started && !error && duration > 0, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AppColors.content(EditPostColors.mediaGreen)))
            Text("${editMediaVideoTime(position)} / ${editMediaVideoTime(duration)}", color = AppColors.content(Color.White), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun editMediaVideoTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
