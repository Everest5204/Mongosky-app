package com.mongosky.app.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a test device. Files are isolated from the installed app's real session. */
@RunWith(AndroidJUnit4::class)
class TokenStorePersistenceTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var store: TokenStore
    private val account = AuthSession("device-test-token", "0123456789abcdef01234567", "Device", "Test")

    @Before fun before() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        directory = File(app.noBackupFilesDir, "session-test-" + UUID.randomUUID()).apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
        coldProcessCache()
        store = TokenStore(context)
        store.clear()
    }

    @After fun after() = runBlocking {
        store.clear()
        directory.deleteRecursively()
        coldProcessCache()
    }

    private fun coldProcessCache() {
        // Reset process-local caches only; disk and Android Keystore remain intact.
        mapOf("memoryLoaded" to false, "memorySession" to null, "persistentSession" to false,
            "rememberNextLogin" to true).forEach { (name, value) ->
            TokenStore::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
        }
    }

    @Test fun uncheckedRememberMeStillRestoresAnAuthenticatedAccount() = runBlocking {
        store.setRememberMeForNextLogin(false)
        store.saveSession(account)
        coldProcessCache()
        val restored = TokenStore(context).readSession()
        assertEquals(account.token, restored?.token)
        assertEquals(account.userId, restored?.account?.userId)
    }

    @Test fun sessionFileDoesNotContainPlaintextTokenOrName() = runBlocking {
        store.saveSession(account)
        val bytes = File(directory, "auth_session").readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(account.token))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(account.firstName))
        assertEquals(2, bytes[0].toInt())
    }

    @Test fun logoutRemovesTheColdStartSession() = runBlocking {
        store.saveSession(account)
        store.clear()
        coldProcessCache()
        assertNull(TokenStore(context).readSession())
        assertFalse(File(directory, "auth_session").exists())
    }

    @Test fun oldProfileCannotOverwriteANewerAccount() = runBlocking {
        store.saveSession(account)
        val fresh = AuthSession("fresh-token", "1123456789abcdef01234567", "New", "Account")
        store.saveSession(fresh)
        assertFalse(store.updateSession(account))
        assertFalse(store.clearIfCurrent(account.token))
        coldProcessCache()
        assertEquals(fresh.userId, TokenStore(context).readSession()?.account?.userId)
    }

    @Test fun versionOneEncryptedTokenCanUpgradeWithoutLosingItsKey() = runBlocking {
        store.saveSession(account)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keys.getKey("mongosky_session_key_v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(account.token.toByteArray(Charsets.UTF_8))
        File(directory, "auth_session").writeBytes(byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + encrypted)
        coldProcessCache()
        val migratedStore = TokenStore(context)
        assertEquals(account.token, migratedStore.readSession()?.token)
        assertNull(migratedStore.readSession()?.account)
        assertTrue(migratedStore.updateSession(account))
        coldProcessCache()
        assertEquals(account.userId, TokenStore(context).readSession()?.account?.userId)
    }
}
