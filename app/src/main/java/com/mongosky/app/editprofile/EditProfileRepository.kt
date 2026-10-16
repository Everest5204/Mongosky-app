package com.mongosky.app.editprofile

import com.mongosky.app.auth.TokenStore
import com.mongosky.app.profile.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface EditProfileDataSource {
    suspend fun load(ownerId: String): EditableProfile
    suspend fun saveBio(ownerId: String, bio: String): EditableProfile
    suspend fun uploadImage(ownerId: String, kind: ProfileImageKind, uri: String): ProfileMediaChange
}
internal class EditProfileRepository(private val tokens: TokenStore, images: ProfileImagePreparer,
    http: OwnProfileHttp = OwnProfileHttp()) : EditProfileDataSource {
    private val api = EditProfileApi(http)
    private val preparation = Mutex()
    // Bound bitmap memory: prepare one photo at a time, then overlap only network uploads.
    private val media = OwnProfileRepository(::token, { uri, kind -> preparation.withLock { images.prepare(uri, kind) } }, http)
    private suspend fun token(): String {
        currentCoroutineContext().ensureActive()
        val value = try { tokens.read() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw OwnProfileException("Could not read your session. Please try again.", cause = e) }
        currentCoroutineContext().ensureActive()
        return value?.takeIf { it.isNotBlank() } ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
    }
    override suspend fun load(ownerId: String) = api.load(token(), ownerId)
    override suspend fun saveBio(ownerId: String, bio: String) = api.saveBio(token(), ownerId, bio)
    override suspend fun uploadImage(ownerId: String, kind: ProfileImageKind, uri: String) = media.uploadImage(ownerId, kind, uri)
}
