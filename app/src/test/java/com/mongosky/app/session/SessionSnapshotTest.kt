package com.mongosky.app.session

import com.mongosky.app.auth.AuthSession
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SessionSnapshotTest {
    private fun jwt(claims: String) = "header." +
        Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray()) + ".signature"

    @Test fun snapshotRestoresTokenAccountAndAvatarTogether() {
        val account = AuthSession("test-token", "0123456789abcdef01234567", "টেস্ট", "User", "https://example.test/a.png")
        val saved = SessionSnapshot.decode(SessionSnapshot.encode(SavedLoginSession(account.token, account)))
        assertEquals(account.token, saved.token)
        assertEquals(saved.token, saved.account?.token)
        assertEquals(account.userId, saved.account?.userId)
        assertEquals(account.firstName, saved.account?.firstName)
        assertEquals(account.profileImageUrl, saved.account?.profileImageUrl)
    }

    @Test fun tokenOnlySnapshotRemainsReadableForMigration() {
        val saved = SessionSnapshot.decode(SessionSnapshot.encode(SavedLoginSession("legacy-token")))
        assertEquals("legacy-token", saved.token)
        assertNull(saved.account)
    }

    @Test fun malformedOrNullSnapshotCannotRestoreAnAccount() {
        listOf("{}", "{\"token\":null}", "{\"token\":\"t\",\"account\":{\"id\":null}}", "not-json").forEach {
            assertTrue(runCatching { SessionSnapshot.decode(it.toByteArray()) }.isFailure)
        }
    }

    @Test fun expirationUsesSecondsAndDoesNotGuessOpaqueTokenExpiry() {
        assertTrue(SessionStartupPolicy.isExpired(jwt("{\"exp\":10}"), 10_000))
        assertFalse(SessionStartupPolicy.isExpired(jwt("{\"exp\":11}"), 10_000))
        assertFalse(SessionStartupPolicy.isExpired("opaque-token", Long.MAX_VALUE))
    }

    @Test fun legacyIdentityMustBeUnambiguousAndBelongToTheToken() {
        val token = jwt("{\"sub\":\"0123456789abcdef01234567\",\"first_name\":\"Test\"}")
        val account = SessionStartupPolicy.legacyAccount(token)
        assertEquals("0123456789abcdef01234567", account?.userId)
        assertEquals(token, account?.token)
        assertNull(SessionStartupPolicy.legacyAccount(jwt("{\"sub\":\"0123456789abcdef01234567\",\"id\":\"1123456789abcdef01234567\"}")))
        assertNull(SessionStartupPolicy.legacyAccount("opaque-token"))
    }
}
