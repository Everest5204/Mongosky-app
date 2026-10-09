package com.mongosky.app.drawer

import com.mongosky.app.profile.OwnProfile

/** Same order and labels as the web drawer; the final account action is native sign-out. */
internal enum class DrawerDestination(val label: String, val routeName: String?) {
    PROFILE("Profile", "PROFILE"),
    MY_ACTIVITY("My Activity", "MY_ACTIVITY"),
    SAVED_ITEMS("Saved Items", "SAVED_ITEMS"),
    SIGNUP("Signup", "SIGNUP"),
    SETTINGS("Settings", "SETTINGS"),
    GUIDELINES("Community Guidelines", "GUIDELINES"),
    CHANGE_NAME("Change name", "CHANGE_NAME"),
    MOBILE_APP("Mobile App", "MOBILE_APP"),
    SIGN_OUT("Sign out", null)
}

internal data class DrawerProfile(
    val id: String, val displayName: String, val imageUrl: String?,
    val followingCount: Long, val followersCount: Long
) {
    companion object {
        fun from(profile: OwnProfile) = DrawerProfile(profile.id, profile.displayName, profile.profileImageUrl,
            profile.followingCount, profile.followersCount)
    }
}

internal data class DrawerState(
    val ownerId: String = "", val profile: DrawerProfile? = null,
    val verified: Boolean = false, val badgeKnown: Boolean = false,
    val loading: Boolean = false, val error: String? = null, val sessionExpired: Boolean = false
)

internal object DrawerPolicy {
    const val SETTLE_MILLIS = 300L
    const val PROFILE_INTERVAL_MILLIS = 10_000L
    const val BADGE_INTERVAL_MILLIS = 60_000L
}
