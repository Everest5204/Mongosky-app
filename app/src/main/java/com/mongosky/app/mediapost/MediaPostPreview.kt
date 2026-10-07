package com.mongosky.app.mediapost

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.AsyncImage
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.mediapost.SelectedPostMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val PreviewGreen = Color(0xFF22C55E)
private val PreviewSoft = Color(0xFFF3F4F6)
private val PreviewMuted = Color(0xFF6B7280)

private const val VideoPreviewUnavailable =
    "Can't preview this video on this phone."

@Composable
fun MediaPostPreview(
    media: List<SelectedPostMedia>,
    activeIndex: Int,
    enabled: Boolean,
    maxPreviewHeight: Dp,
    onActiveIndexChange: (Int) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (media.isEmpty()) return

    val index = activeIndex.coerceIn(media.indices)
    val active = media[index]
    val heightLimit = maxPreviewHeight.coerceAtLeast(240.dp)

    var aspectRatio by remember(active.id, active.uri) {
        mutableStateOf(
            if (active.kind == PostMediaKind.VIDEO) {
                16f / 9f
            } else {
                1f
            }
        )
    }

    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = (maxWidth / aspectRatio)
                .coerceIn(240.dp, heightLimit)

            val updateAspectRatio: (Float) -> Unit = { ratio ->
                if (ratio.isFinite() && ratio > 0f) {
                    aspectRatio = ratio.coerceIn(0.05f, 20f)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(PreviewSoft)
                    .clipToBounds()
            ) {
                when (active.kind) {
                    PostMediaKind.IMAGE -> PhotoPreview(
                        uri = active.uri,
                        description = "Photo ${index + 1} of ${media.size}",
                        onAspectRatio = updateAspectRatio,
                        modifier = Modifier.fillMaxSize()
                    )

                    PostMediaKind.VIDEO -> VideoPreview(
                        media = active,
                        enabled = enabled,
                        onAspectRatio = updateAspectRatio,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (
                    active.kind == PostMediaKind.IMAGE &&
                    media.size > 1
                ) {
                    Text(
                        text = "${index + 1}/${media.size}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Color.Black.copy(alpha = 0.6f)
                            )
                            .padding(
                                horizontal = 9.dp,
                                vertical = 4.dp
                            )
                    )
                }

                IconButton(
                    onClick = { onRemove(active.id) },
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Color.Black.copy(
                                    alpha = if (enabled) 0.6f else 0.25f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (
                                active.kind == PostMediaKind.VIDEO
                            ) {
                                "Remove video"
                            } else {
                                "Remove photo ${index + 1}"
                            },
                            tint = Color.White.copy(
                                alpha = if (enabled) 1f else 0.5f
                            ),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (
            active.kind == PostMediaKind.IMAGE &&
            media.size > 1
        ) {
            PhotoThumbnails(
                media = media,
                activeIndex = index,
                enabled = enabled,
                onSelect = onActiveIndexChange
            )
        }
    }
}

@Composable
private fun PhotoPreview(
    uri: String,
    description: String,
    onAspectRatio: (Float) -> Unit,
    modifier: Modifier
) {
    var loading by remember(uri) {
        mutableStateOf(true)
    }
    var failed by remember(uri) {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onLoading = {
                loading = true
                failed = false
            },
            onSuccess = { result ->
                loading = false
                failed = false

                val drawable = result.result.drawable

                if (
                    drawable.intrinsicWidth > 0 &&
                    drawable.intrinsicHeight > 0
                ) {
                    onAspectRatio(
                        drawable.intrinsicWidth.toFloat() /
                                drawable.intrinsicHeight
                    )
                }
            },
            onError = {
                loading = false
                failed = true
            }
        )

        if (loading) {
            PreviewSpinner()
        }

        if (failed) {
            PreviewMessage(
                message = "Can't preview this photo. Please select it again.",
                color = PreviewMuted
            )
        }
    }
}

@Composable
private fun PhotoThumbnails(
    media: List<SelectedPostMedia>,
    activeIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberLazyListState()

    LaunchedEffect(media[activeIndex].id, media.size) {
        val activeIsVisible = scrollState.layoutInfo
            .visibleItemsInfo
            .any { it.index == activeIndex }

        if (!activeIsVisible) {
            scrollState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E7EB))
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        state = scrollState,
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 10.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = media,
            key = { _, item -> item.id },
            contentType = { _, _ -> "photo" }
        ) { index, item ->
            val shape = RoundedCornerShape(12.dp)

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .border(
                        width = 2.dp,
                        color = if (index == activeIndex) {
                            PreviewGreen
                        } else {
                            Color.Transparent
                        },
                        shape = shape
                    )
                    .clip(shape)
                    .background(PreviewSoft)
                    .selectable(
                        selected = index == activeIndex,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    )
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = "Select photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

private data class VideoPreviewState(
    val playing: Boolean = false,
    val playRequested: Boolean = false,
    val buffering: Boolean = true,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val error: String? = null
)

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(
    media: SelectedPostMedia,
    enabled: Boolean,
    onAspectRatio: (Float) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    val owner = remember(context) {
        context.previewLifecycleOwner()
    }

    var savedPosition by rememberSaveable(media.id, media.uri) {
        mutableStateOf(0L)
    }

    val player = remember(context, media.id, media.uri) {
        ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var state by remember(player) {
        mutableStateOf(
            VideoPreviewState(positionMs = savedPosition)
        )
    }

    var seeking by remember(player) {
        mutableStateOf<Float?>(null)
    }

    var started by remember(owner) {
        mutableStateOf(
            owner?.lifecycle?.currentState
                ?.isAtLeast(Lifecycle.State.STARTED) ?: true
        )
    }

    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events
            ) {
                state = VideoPreviewState(
                    playing = player.isPlaying,
                    playRequested = player.playWhenReady &&
                            player.playbackState != Player.STATE_ENDED,
                    buffering = player.playbackState ==
                            Player.STATE_BUFFERING,
                    durationMs = player.duration.coerceAtLeast(0L),
                    positionMs = player.currentPosition
                        .coerceAtLeast(0L),
                    error = if (player.playerError != null) {
                        VideoPreviewUnavailable
                    } else {
                        null
                    }
                )

                savedPosition = state.positionMs
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onAspectRatio(
                        videoSize.width *
                                videoSize.pixelWidthHeightRatio /
                                videoSize.height
                    )
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    started = true
                }

                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    started = false
                    savedPosition = player.currentPosition
                        .coerceAtLeast(0L)
                    player.pause()
                }

                else -> Unit
            }
        }

        player.addListener(listener)
        owner?.lifecycle?.addObserver(observer)

        val initialPosition = savedPosition

        try {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(media.uri)
                    .setMimeType(media.mimeType)
                    .build(),
                initialPosition
            )

            player.playWhenReady = false
            player.prepare()
        } catch (_: Exception) {
            state = state.copy(
                buffering = false,
                error = VideoPreviewUnavailable
            )
        }

        onDispose {
            savedPosition = player.currentPosition
                .coerceAtLeast(0L)

            owner?.lifecycle?.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, enabled) {
        if (!enabled) {
            player.pause()
        }
    }

    LaunchedEffect(player, state.playing, started, enabled) {
        if (!state.playing || !started || !enabled) {
            return@LaunchedEffect
        }

        while (isActive) {
            if (seeking == null) {
                val position = player.currentPosition
                    .coerceAtLeast(0L)

                state = state.copy(positionMs = position)
                savedPosition = position
            }

            delay(250L)
        }
    }

    Box(modifier.background(Color.Black)) {
        ContentFrame(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Selected video preview"
                },
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit
        )

        if (state.buffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PreviewSpinner()
            }
        }

        state.error?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp),
                contentAlignment = Alignment.Center
            ) {
                PreviewMessage(
                    message = message,
                    color = Color.White
                )
            }
        }

        val controlsEnabled = enabled &&
                started &&
                state.error == null

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = controlsEnabled,
                onClick = {
                    if (state.playRequested) {
                        player.pause()
                    } else {
                        if (
                            player.playbackState ==
                            Player.STATE_ENDED
                        ) {
                            player.seekTo(0L)
                        }

                        player.play()
                    }
                }
            ) {
                Icon(
                    imageVector = if (state.playRequested) {
                        PreviewPause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (state.playRequested) {
                        "Pause video"
                    } else {
                        "Play video"
                    },
                    tint = Color.White.copy(
                        alpha = if (controlsEnabled) 1f else 0.45f
                    )
                )
            }

            val progress = if (state.durationMs > 0L) {
                (
                        state.positionMs.toDouble() /
                                state.durationMs
                        ).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            Slider(
                value = seeking ?: progress,
                onValueChange = {
                    seeking = it
                },
                onValueChangeFinished = {
                    seeking?.let { fraction ->
                        if (
                            controlsEnabled &&
                            state.durationMs > 0L
                        ) {
                            val position = (
                                    fraction * state.durationMs
                                    ).toLong().coerceIn(
                                    0L,
                                    state.durationMs
                                )

                            player.seekTo(position)
                            state = state.copy(
                                positionMs = position
                            )
                            savedPosition = position
                        }
                    }

                    seeking = null
                },
                enabled = controlsEnabled &&
                        state.durationMs > 0L,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Video position"
                    },
                colors = SliderDefaults.colors(
                    thumbColor = PreviewGreen,
                    activeTrackColor = PreviewGreen,
                    inactiveTrackColor = Color.White.copy(
                        alpha = 0.3f
                    )
                )
            )

            val shownPosition = seeking?.let {
                (it * state.durationMs).toLong()
            } ?: state.positionMs

            Text(
                text = "${previewTime(shownPosition)} / " +
                        previewTime(state.durationMs),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(
                    start = 8.dp,
                    end = 12.dp
                )
            )
        }
    }
}

@Composable
private fun PreviewSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = PreviewGreen,
        strokeWidth = 2.5.dp
    )
}

@Composable
private fun PreviewMessage(
    message: String,
    color: Color
) {
    Text(
        text = message,
        color = color,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp)
    )
}

private fun Context.previewLifecycleOwner(): LifecycleOwner? {
    var current = this

    while (current is ContextWrapper) {
        if (current is LifecycleOwner) {
            return current
        }

        val base = current.baseContext

        if (base === current) {
            break
        }

        current = base
    }

    return current as? LifecycleOwner
}

private fun previewTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L

    return "${seconds / 60L}:" +
            (seconds % 60L).toString().padStart(2, '0')
}

private val PreviewPause = ImageVector.Builder(
    name = "PreviewPause",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = PathParser()
            .parsePathString(
                "M6 5H10V19H6Z M14 5H18V19H14Z"
            )
            .toNodes(),
        fill = SolidColor(Color.Black)
    )
}.build()
