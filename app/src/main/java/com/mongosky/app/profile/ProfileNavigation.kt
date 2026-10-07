package com.mongosky.app.profile

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.mongosky.app.post.FeedAuthor

/** One native destination for author links across all sections. */
data class ProfileOpenRequest(val userId: String, val author: FeedAuthor? = null)

val LocalProfileNavigator = staticCompositionLocalOf<(ProfileOpenRequest) -> Unit> { {} }
val LocalProfileViewerId = staticCompositionLocalOf<String?> { null }

internal fun profileUserId(value: String?): String? = value?.trim()?.lowercase()?.takeIf {
    it.length == 24 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } && it.any { char -> char != '0' }
}

internal fun profileRoute(userId: String): String = "USER_PROFILE:${requireNotNull(profileUserId(userId))}"
internal fun profileRouteUserId(route: String): String? =
    if (route.startsWith("USER_PROFILE:")) profileUserId(route.substringAfter(':')) else null

@Composable
fun Modifier.profileLink(userId: String?, author: FeedAuthor? = null, beforeOpen: () -> Unit = {}): Modifier {
    val id = profileUserId(userId) ?: return this
    val navigate = LocalProfileNavigator.current
    return clickable(role = Role.Button, onClickLabel = "View profile") {
        beforeOpen()
        navigate(ProfileOpenRequest(id, author?.takeIf { profileUserId(it.id) == id }))
    }
}
