package com.mongosky.app.auth

import com.mongosky.app.session.SavedLoginSession
import com.mongosky.app.signup.SignupRequest
import java.io.IOException

internal fun testAccount(token: String = "test-token", id: String = "0123456789abcdef01234567") =
    AuthSession(token, id, "Test", "User", "https://example.test/avatar.png")

internal fun testSignup() = SignupRequest("Test", "User", "test@example.test", "a safe password", "other", 1, 2, 1995)

internal class MemoryLoginStore(var saved: SavedLoginSession? = null) : LoginSessionStore {
    var saves = 0
    var clears = 0
    var updates = 0
    var failedSaves = 0
    override suspend fun read() = saved
    override suspend fun save(session: AuthSession) {
        saves++
        if (failedSaves > 0) { failedSaves--; throw IOException("Storage unavailable") }
        saved = SavedLoginSession(session.token, session)
    }
    override suspend fun update(session: AuthSession): Boolean {
        if (saved?.token != session.token ||
            (saved?.account != null && saved?.account?.userId != session.userId)) return false
        updates++
        saved = SavedLoginSession(session.token, session)
        return true
    }
    override suspend fun clearIfCurrent(token: String): Boolean {
        if (saved?.token != token) return false
        clear()
        return true
    }
    override suspend fun clear() { clears++; saved = null }
}

internal class FakeLoginBackend(var account: AuthSession = testAccount()) : LoginBackend {
    var logins = 0
    var validations = 0
    var profiles = 0
    var onLogin: suspend () -> AuthSession = { account }
    var onValidate: suspend (String) -> AuthSession = { account }
    var onProfile: suspend (String) -> AuthSession = { account }
    override suspend fun login(identity: String, password: String): AuthSession { logins++; return onLogin() }
    override suspend fun getSession(token: String): AuthSession { validations++; return onValidate(token) }
    override suspend fun getProfile(token: String): AuthSession { profiles++; return onProfile(token) }
}
