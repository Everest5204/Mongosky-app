package com.mongosky.app.profile.userfeed

import com.mongosky.app.home.HomeAuthorState
import com.mongosky.app.post.FeedPost
import com.mongosky.app.post.HomePost
import com.mongosky.app.profile.UserProfile
import com.mongosky.app.profile.UserProfileState
import com.mongosky.app.profile.profileUserId
import com.mongosky.app.profile.toHomePost

/** A stale owner or an isSelf flag alone can never grant owner-only post actions. */
internal fun userProfileFeedPost(raw: FeedPost, profile: UserProfile?, viewerId: String?): HomePost {
    val owner = profile?.takeIf { it.id == raw.userId && raw.author.id == raw.userId }
    val own = owner?.isSelf == true && profileUserId(viewerId) == raw.userId
    val post = raw.toHomePost(owner?.author ?: raw.author, own)
    return when (post) {
        is HomePost.Text -> post.copy(post = post.post.copy(isFollowing = owner?.isFollowing == true))
        is HomePost.Media -> post.copy(post = post.post.copy(isFollowing = owner?.isFollowing == true))
    }
}

internal fun userProfileFeedAuthor(state: UserProfileState, authorId: String): HomeAuthorState {
    val profile = state.profile?.takeIf { it.id == state.userId && it.id == authorId }
    return HomeAuthorState(verified = profile != null && state.verified && !state.sessionExpired && !state.unavailable,
        following = profile?.isFollowing == true,
        pending = profile == null || state.followingBusy || state.followingUncertain || state.sessionExpired || state.unavailable)
}
