package com.mongosky.app.drawer

import com.mongosky.app.profile.OwnProfileApi
import com.mongosky.app.profile.OwnProfileException
import com.mongosky.app.profile.OwnProfileHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal interface DrawerSource {
    suspend fun profile(ownerId: String): DrawerProfile
    suspend fun verified(ownerId: String): Boolean
}

/** Reuses the web/native profile endpoints and the existing pooled, cancellable HTTP client. */
internal class DrawerRepository(
    private val readToken: suspend () -> String?, http: OwnProfileHttp = OwnProfileHttp()
) : DrawerSource {
    private val api = OwnProfileApi(http)
    private suspend fun token(): String {
        currentCoroutineContext().ensureActive()
        val value = try { readToken() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw OwnProfileException("Could not read your session. Please try again.", cause = error) }
        return value?.takeIf { it.isNotBlank() }
            ?: throw OwnProfileException("Please sign in again.", requiresSignIn = true)
    }
    override suspend fun profile(ownerId: String) = DrawerProfile.from(api.get(token(), ownerId))
    override suspend fun verified(ownerId: String) = api.isVerified(token(), ownerId)
}
