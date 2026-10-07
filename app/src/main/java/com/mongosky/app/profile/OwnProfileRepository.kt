package com.mongosky.app.profile

import com.mongosky.app.auth.TokenStore
import com.mongosky.app.post.FeedPage
import com.mongosky.app.post.FeedSource
import com.mongosky.app.profile.OwnProfileApi
import com.mongosky.app.profile.OwnProfileConnectionsApi
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.OwnProfileHttp
import com.mongosky.app.profile.OwnProfileMediaApi
import com.mongosky.app.profile.OwnProfilePostsApi
import com.mongosky.app.profile.ProfileImagePreparer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface OwnProfileDataSource {
    suspend fun isVerified(ownerId: String): Boolean = false
    suspend fun profile(ownerId: String): OwnProfile
    suspend fun posts(ownerId: String, source: FeedSource, cursor: String?): FeedPage
    suspend fun saveBio(ownerId: String, bio: String): String
    suspend fun uploadImage(ownerId: String, kind: ProfileImageKind, uri: String): ProfileMediaChange
    suspend fun connections(ownerId: String, kind: ProfileConnectionKind, cursor: String?): ProfileConnectionPage
    suspend fun setFollowing(personId: String, following: Boolean): Boolean
}

class OwnProfileRepository(
    private val readToken: suspend () -> String?,
    private val prepareImage: suspend (String, ProfileImageKind) -> File,
    http: OwnProfileHttp = OwnProfileHttp()
) : OwnProfileDataSource {
    constructor(tokenStore: TokenStore, imagePreparer: ProfileImagePreparer) : this(tokenStore::read, imagePreparer::prepare)
    private val profileApi = OwnProfileApi(http)
    private val postsApi = OwnProfilePostsApi(http)
    private val mediaApi = OwnProfileMediaApi(http)
    private val connectionsApi = OwnProfileConnectionsApi(http)

    private suspend fun token(): String {
        currentCoroutineContext().ensureActive()
        val value = try { readToken() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw OwnProfileException("Could not read your session. Please try again.", cause = error) }
        return value?.takeIf { it.isNotBlank() }
            ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
    }
    override suspend fun profile(ownerId: String) = profileApi.get(token(), ownerId)
    override suspend fun isVerified(ownerId: String) = profileApi.isVerified(token(), ownerId)
    override suspend fun posts(ownerId: String, source: FeedSource, cursor: String?) = postsApi.load(token(), ownerId, source, cursor)
    override suspend fun saveBio(ownerId: String, bio: String) = profileApi.saveBio(token(), ownerId, bio)
    override suspend fun uploadImage(ownerId: String, kind: ProfileImageKind, uri: String): ProfileMediaChange {
        val token = token()
        val file = prepareImage(uri, kind)
        return try {
            currentCoroutineContext().ensureActive()
            mediaApi.upload(token, ownerId, kind, file)
        } finally { file.delete() }
    }
    override suspend fun connections(ownerId: String, kind: ProfileConnectionKind, cursor: String?) =
        connectionsApi.load(token(), ownerId, kind, cursor)
    override suspend fun setFollowing(personId: String, following: Boolean) = connectionsApi.setFollowing(token(), personId, following)
}
