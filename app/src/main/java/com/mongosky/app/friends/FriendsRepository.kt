package com.mongosky.app.friends

/** Only this boundary needs to change when a push stream or different backend is added. */
interface FriendsRepository {
    suspend fun page(token: String, tab: FriendsTab, cursor: String? = null): FriendsPage
    suspend fun setFollowing(token: String, userId: String, following: Boolean): FriendsFollowResult
    suspend fun verifiedIds(token: String, ids: List<String>): Set<String>
}

class LiveFriendsRepository(private val api: FriendsApi = FriendsApi()) : FriendsRepository {
    override suspend fun page(token: String, tab: FriendsTab, cursor: String?): FriendsPage = api.page(token, tab, cursor)
    override suspend fun setFollowing(token: String, userId: String, following: Boolean): FriendsFollowResult =
        api.setFollowing(token, userId, following)
    override suspend fun verifiedIds(token: String, ids: List<String>): Set<String> = api.verifiedIds(token, ids)
}
