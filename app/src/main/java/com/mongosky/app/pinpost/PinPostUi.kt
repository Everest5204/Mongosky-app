package com.mongosky.app.pinpost

import com.mongosky.app.language.appText

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mongosky.app.home.HomeFeedIcons
import com.mongosky.app.post.FeedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun PinPostDialog(controller: PinPostController) {
    val state = controller.dialog ?: return
    var cooldown by remember(controller, state.retryAtMillis) { mutableStateOf(controller.remainingCooldownMillis()) }
    LaunchedEffect(controller, state.retryAtMillis) {
        cooldown = controller.remainingCooldownMillis()
        while (cooldown > 0) { delay(minOf(1000L, cooldown)); cooldown = controller.remainingCooldownMillis() }
    }
    AlertDialog(onDismissRequest = { controller.dismiss() }, containerColor = AppColors.surface(androidx.compose.ui.graphics.Color.White),
        icon = { Icon(HomeFeedIcons.pin, null, tint = AppColors.content(FeedColors.brand)) },
        title = { Text(appText(if (state.unpin) "Unpin this post?" else "Pin to your profile?"), fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.content(FeedColors.brand), strokeWidth = 2.dp)
                    Text(appText("Checking your profile pin…"))
                }
                state.unpin -> Text(appText("This post will return to its usual place on your profile."))
                state.baseline?.post != null -> Text(appText("This post will replace your current pinned post at the top of your profile. The previous post stays on your profile."))
                else -> Text(appText("This post will appear at the top of your profile. You can pin one post at a time."))
            }
            state.error?.let { Text(appText(it), color = AppColors.content(FeedColors.brand), fontSize = 14.sp) }
        } },
        confirmButton = {
            TextButton(onClick = { when { state.uncertain -> controller.check(); state.needsRefresh -> controller.retryRead(); else -> controller.confirm() } },
                enabled = !state.busy && !state.loading && cooldown == 0L) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = AppColors.content(FeedColors.brand), strokeWidth = 2.dp)
                else Text(appText(when { cooldown > 0 -> "Wait ${(cooldown + 999) / 1000}s"; state.uncertain -> "Check status"; state.needsRefresh -> "Refresh"; state.attempt != null -> "Retry update"; state.unpin -> "Unpin"; state.baseline?.post != null -> "Replace pin"; else -> "Pin post" }), color = AppColors.content(FeedColors.brand))
            }
        },
        dismissButton = { TextButton(onClick = { controller.dismiss() }, enabled = !state.busy) { Text(appText("Cancel"), color = AppColors.content(FeedColors.muted)) } })
}

@Composable
internal fun PinnedPostLabel() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(HomeFeedIcons.pin, null, Modifier.size(15.dp), tint = AppColors.content(FeedColors.muted))
        Text(appText("Pinned"), color = AppColors.content(FeedColors.muted), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** One indexed read per active profile interval; never one request per post/card. */
@Composable
internal fun ProfilePinEffect(controller: PinPostController, ownerId: String, active: Boolean) {
    LaunchedEffect(controller, ownerId, active) {
        if (active && PinPolicy.validId(ownerId)) while (isActive) {
            controller.refresh(ownerId, force = true)
            delay(PinPostController.CACHE_MILLIS)
        }
    }
}
