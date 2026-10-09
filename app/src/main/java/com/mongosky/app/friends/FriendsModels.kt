package com.mongosky.app.friends

import java.io.IOException

enum class FriendsTab(val path: String, val label: String) {
    FOLLOWERS("followers", "Followers"), FOLLOWING("following", "Following")
}

data class FriendsUser(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val imageUrl: String? = null,
    val title: String = "",
    val isFollowing: Boolean = false,
    val followsYou: Boolean = false,
    val isSelf: Boolean = false,
    val verified: Boolean = false
) {
    val fullName: String get() = listOf(firstName, lastName).filter(String::isNotBlank)
        .joinToString(" ").ifBlank { "Mongosky user" }
    val actionLabel: String get() = if (isFollowing) "Following" else "Follow Back"
}

data class FriendsPage(
    val users: List<FriendsUser>, val nextCursor: String?, val hasMore: Boolean,
    val followersCount: Long, val followingCount: Long
)

/** This response's followers_count belongs to the target, not the signed-in viewer. */
data class FriendsFollowResult(val isFollowing: Boolean)

data class FriendsListState(
    val users: List<FriendsUser> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val loadedPages: Int = 0,
    val headUsers: List<FriendsUser> = emptyList(),
    val visitedCursors: Set<String> = emptySet(),
    val error: String? = null,
    val moreError: String? = null
)

data class FriendsUiState(
    val activeTab: FriendsTab = FriendsTab.FOLLOWERS,
    val followers: FriendsListState = FriendsListState(),
    val following: FriendsListState = FriendsListState(),
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val countsLoaded: Boolean = false,
    val pendingIds: Set<String> = emptySet(),
    val uncertainIds: Set<String> = emptySet(),
    val sessionExpired: Boolean = false,
    val message: String? = null
) {
    val activeList: FriendsListState get() = list(activeTab)
    fun list(tab: FriendsTab): FriendsListState = if (tab == FriendsTab.FOLLOWERS) followers else following
    fun withList(tab: FriendsTab, value: FriendsListState): FriendsUiState =
        if (tab == FriendsTab.FOLLOWERS) copy(followers = value) else copy(following = value)
}

enum class FriendsFailure { SESSION_EXPIRED, ACCESS_DENIED, RATE_LIMITED, NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE, REJECTED }

class FriendsException(
    message: String, val failure: FriendsFailure, val retryAfterSeconds: Int = 0, cause: Throwable? = null
) : IOException(message, cause)
