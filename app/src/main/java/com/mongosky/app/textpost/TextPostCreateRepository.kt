package com.mongosky.app.textpost

import com.mongosky.app.auth.TokenStore
import com.mongosky.app.textpost.TextPost
import com.mongosky.app.textpost.TextPostCreateApi
import com.mongosky.app.textpost.TextPostCreateException
import com.mongosky.app.textpost.TextPostCreator
import com.mongosky.app.textpost.TextPostDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface TextPostPublisher {
    suspend fun publish(draft: TextPostDraft): TextPost
}

class TextPostCreateRepository(
    private val readToken: suspend () -> String?,
    private val api: TextPostCreator = TextPostCreateApi()
) : TextPostPublisher {
    constructor(tokenStore: TokenStore) : this(tokenStore::read)

    override suspend fun publish(draft: TextPostDraft): TextPost {
        currentCoroutineContext().ensureActive()
        val token = try {
            readToken()?.takeIf { it.isNotBlank() }
                ?: throw TextPostCreateException("Please sign in again.", requiresSignIn = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: TextPostCreateException) {
            throw error
        } catch (error: Exception) {
            throw TextPostCreateException("Could not read your saved session. Please retry.", canRetry = true, cause = error)
        }
        currentCoroutineContext().ensureActive()
        return api.create(token, draft)
    }
}
