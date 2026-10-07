package com.mongosky.app.profile

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.FeedSource

data class UserProfile(
    val id: String, val firstName: String, val lastName: String,
    val title: String = "", val bio: String = "", val imageUrl: String? = null, val coverUrl: String? = null,
    val followersCount: Long = 0, val followingCount: Long = 0, val postsCount: Long = 0,
    val isSelf: Boolean = false, val isFollowing: Boolean = false, val canMessage: Boolean = false
) {
    val displayName: String get() = "$firstName $lastName".trim().replace(Regex("\\s+"), " ")
    val author: FeedAuthor get() = FeedAuthor(id, firstName, lastName, imageUrl)
    internal fun asPostOwner() = OwnProfile(id, firstName, lastName, bio, imageUrl, coverUrl,
        followersCount, followingCount, postsCount)
}

data class UserProfileFollow(val isFollowing: Boolean, val followersCount: Long)

data class UserProfileState(
    val viewerId: String = "", val userId: String = "", val hint: FeedAuthor? = null,
    val profile: UserProfile? = null, val verified: Boolean = false,
    val profileLoading: Boolean = false, val profileError: String? = null,
    val posts: List<FeedPost> = emptyList(), val loadingSources: Set<FeedSource> = emptySet(),
    val postErrors: Map<FeedSource, String> = emptyMap(), val hasMore: Boolean = false,
    val refreshing: Boolean = false, val checking: Boolean = false,
    val tab: OwnProfileTab = OwnProfileTab.ALL, val pageVersion: Long = 0, val refreshVersion: Long = 0,
    val followingBusy: Boolean = false, val followingUncertain: Boolean = false,
    val notice: String? = null, val sessionExpired: Boolean = false, val unavailable: Boolean = false
) {
    val postsLoading: Boolean get() = loadingSources.isNotEmpty()
}
