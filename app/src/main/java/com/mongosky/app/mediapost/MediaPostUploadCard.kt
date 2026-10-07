package com.mongosky.app.mediapost

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mongosky.app.mediapost.MediaPostDraft
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.mediapost.PostMediaKind
import kotlinx.coroutines.delay

private val UploadGreen = Color(0xFF15803D)
private val UploadTrack = Color(0xFFDCFCE7)
private val UploadText = Color(0xFF111827)
private val UploadMuted = Color(0xFF6B7280)
private val UploadBorder = Color(0xFFE5E7EB)
private val UploadError = Color(0xFFB91C1C)

/** Home consumes a confirmed post before acknowledging success in the ViewModel. */
@Composable
fun MediaPostUploadCard(
    state: MediaPostUploadState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onCheckPosts: () -> Unit,
    modifier: Modifier = Modifier,
    retryDelaySeconds: () -> Int = { 0 }
) {
    if (state is MediaPostUploadState.Idle ||
        state is MediaPostUploadState.Succeeded
    ) return

    val draft = state.draft ?: return
    val failure = state as? MediaPostUploadState.Failed

    val title = when (state) {
        is MediaPostUploadState.Preparing -> "Preparing your post…"
        is MediaPostUploadState.Uploading -> "Uploading…"
        is MediaPostUploadState.Publishing -> "Publishing…"

        is MediaPostUploadState.Failed ->
            if (state.outcomeUnknown) {
                "Check your post"
            } else {
                "Couldn't publish your post"
            }

        else -> return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, UploadBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UploadThumbnail(draft)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = if (failure == null) {
                            UploadText
                        } else {
                            UploadError
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )

                    Text(
                        text = mediaLabel(draft),
                        color = UploadMuted,
                        fontSize = 13.sp
                    )
                }
            }

            if (draft.caption.isNotBlank()) {
                Text(
                    text = draft.caption,
                    color = UploadText,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (failure != null) {
                Text(
                    text = failure.message,
                    color = UploadError,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )

                UploadFailureActions(
                    failure = failure,
                    retryDelaySeconds = retryDelaySeconds,
                    onRetry = onRetry,
                    onEdit = onEdit,
                    onDismiss = onDismiss,
                    onSignIn = onSignIn,
                    onCheckPosts = onCheckPosts
                )
            } else {
                UploadProgress(state)
            }
        }
    }
}

@Composable
private fun UploadThumbnail(draft: MediaPostDraft) {
    val media = draft.media.firstOrNull()

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6)),
        contentAlignment = Alignment.Center
    ) {
        if (media?.kind == PostMediaKind.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Selected video",
                tint = UploadGreen,
                modifier = Modifier.size(30.dp)
            )
        } else if (media != null) {
            Text(
                text = "Photo",
                color = UploadMuted,
                fontSize = 12.sp
            )

            AsyncImage(
                model = media.uri,
                contentDescription = "Selected photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun UploadProgress(state: MediaPostUploadState) {
    val progress = (state as? MediaPostUploadState.Uploading)?.progress
    val fraction = progress?.fraction

    if (fraction == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = UploadGreen,
            trackColor = UploadTrack
        )
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = UploadGreen,
            trackColor = UploadTrack
        )
    }

    val message = when (state) {
        is MediaPostUploadState.Preparing ->
            "You can keep browsing while we prepare your media."

        is MediaPostUploadState.Uploading -> {
            val percent = progress?.percent

            if (percent == null) {
                "You can keep browsing while your media uploads."
            } else {
                "$percent% sent · You can keep browsing."
            }
        }

        is MediaPostUploadState.Publishing ->
            "Waiting for confirmation. You can keep browsing."

        else -> return
    }

    Text(
        text = message,
        color = UploadMuted,
        fontSize = 13.sp
    )
}

@Composable
private fun UploadFailureActions(
    failure: MediaPostUploadState.Failed,
    retryDelaySeconds: () -> Int,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onCheckPosts: () -> Unit
) {
    val canRetry = failure.canRetry &&
            !failure.requiresSignIn &&
            !failure.outcomeUnknown

    val currentRetryDelay by rememberUpdatedState(retryDelaySeconds)

    var retryIn by remember(failure.draft.id, canRetry) {
        mutableIntStateOf(
            if (canRetry) {
                retryDelaySeconds().coerceAtLeast(0)
            } else {
                0
            }
        )
    }

    LaunchedEffect(failure.draft.id, canRetry) {
        if (!canRetry) return@LaunchedEffect

        while (true) {
            retryIn = currentRetryDelay().coerceAtLeast(0)

            if (retryIn == 0) break

            delay(1_000L)
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            failure.requiresSignIn -> {
                Button(
                    onClick = onSignIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UploadGreen
                    )
                ) {
                    Text("Sign in again")
                }
            }

            failure.outcomeUnknown -> {
                OutlinedButton(
                    onClick = onCheckPosts,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = UploadGreen
                    )
                ) {
                    Text(
                        if (
                            failure.draft.media.firstOrNull()?.kind ==
                            PostMediaKind.VIDEO
                        ) {
                            "Check Reels"
                        } else {
                            "Check feed"
                        }
                    )
                }
            }

            else -> {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        enabled = retryIn == 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UploadGreen
                        )
                    ) {
                        Text(
                            if (retryIn > 0) {
                                "Retry in ${retryIn}s"
                            } else {
                                "Retry"
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = UploadGreen
                    )
                ) {
                    Text("Edit post")
                }
            }
        }

        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(
                contentColor = UploadMuted
            )
        ) {
            Text("Dismiss")
        }
    }
}

private fun mediaLabel(draft: MediaPostDraft): String = when {
    draft.media.firstOrNull()?.kind == PostMediaKind.VIDEO -> "1 video"
    draft.media.size == 1 -> "1 photo"
    else -> "${draft.media.size} photos"
}
