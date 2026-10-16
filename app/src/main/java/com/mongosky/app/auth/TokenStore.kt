package com.mongosky.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mongosky.app.session.SavedLoginSession
import com.mongosky.app.session.SessionSnapshot
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenStore(context: Context) {

    private val sessionFile = File(context.applicationContext.noBackupFilesDir, "auth_session")
    private val tokenFile = AtomicFile(sessionFile)

    /** Applies to the next successful save; this method performs no disk I/O. */
    fun setRememberMeForNextLogin(rememberMe: Boolean) {
        synchronized(lock) {
            rememberNextLogin = rememberMe
        }
    }

    suspend fun save(token: String) = saveRecord(SavedLoginSession(token))

    internal suspend fun saveSession(account: AuthSession) =
        saveRecord(SavedLoginSession(account.token, account))

    private suspend fun saveRecord(saved: SavedLoginSession) = withContext(Dispatchers.IO) {
        require(saved.token.isNotBlank()) { "Token must not be empty." }

        synchronized(lock) {
            if (!rememberNextLogin) {
                // Every TokenStore instance can read this session in the current
                // process. No token file remains for a later process to restore.
                deleteSavedSession()
                memorySession = saved
                memoryLoaded = true
                persistentSession = false
                return@synchronized
            }
            writeSavedSession(saved)
            memorySession = saved
            memoryLoaded = true
            persistentSession = true
        }
    }

    suspend fun read(): String? = readSession()?.token

    internal suspend fun readSession(): SavedLoginSession? {
        // Once loaded, all feature TokenStore instances share this immutable record without a hop to IO.
        if (memoryLoaded) return memorySession
        return withContext(Dispatchers.IO) { synchronized(lock) { readSavedSession() } }
    }

    /** A late validation/profile response cannot revive logout or replace a different account. */
    internal suspend fun updateSession(account: AuthSession): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = readSavedSession() ?: return@synchronized false
            if (current.token != account.token ||
                (current.account != null && current.account.userId != account.userId)) return@synchronized false
            val saved = SavedLoginSession(account.token, account)
            if (persistentSession) writeSavedSession(saved)
            memorySession = saved
            true
        }
    }

    internal suspend fun clearIfCurrent(token: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (readSavedSession()?.token != token) return@synchronized false
            clearSavedSession()
            true
        }
    }

    // Caller holds lock. Disk and Android Keystore work always runs on Dispatchers.IO.
    private fun readSavedSession(): SavedLoginSession? {
        if (memoryLoaded) return memorySession
        val payload = try {
            tokenFile.readFully()
        } catch (error: FileNotFoundException) {
            if (sessionFile.exists() || File(sessionFile.path + ".bak").exists()) {
                throw error
            }
            memoryLoaded = true
            persistentSession = false
            return null
        }
        if (payload.size < 2) throw IOException("Invalid saved session.")

        val version = payload[0].toInt() and 0xff
        val ivLength = payload[1].toInt() and 0xff
        if (
            version !in 1..FORMAT_VERSION ||
            ivLength !in 12..16 ||
            payload.size < 2 + ivLength + 16
        ) {
            throw IOException("Invalid saved session.")
        }

        val iv = payload.copyOfRange(2, 2 + ivLength)
        val encrypted = payload.copyOfRange(2 + ivLength, payload.size)
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IOException("Session key is unavailable.")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(encrypted)
        val saved = if (version == 1) {
            val token = String(plaintext, Charsets.UTF_8).takeIf { it.isNotBlank() }
                ?: throw IOException("Invalid saved session.")
            SavedLoginSession(token)
        } else SessionSnapshot.decode(plaintext)
        memorySession = saved
        memoryLoaded = true
        persistentSession = true
        return saved
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { clearSavedSession() }
    }

    private fun clearSavedSession() {
        deleteSavedSession()
        memorySession = null
        memoryLoaded = true
        persistentSession = false
        rememberNextLogin = true
    }

    private fun writeSavedSession(saved: SavedLoginSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(SessionSnapshot.encode(saved))
        val iv = cipher.iv
        // v2 adds the account snapshot. Existing v1 tokens and the Keystore alias remain readable.
        val payload = byteArrayOf(FORMAT_VERSION.toByte(), iv.size.toByte()) + iv + encrypted
        val output = tokenFile.startWrite()
        try {
            output.write(payload)
            tokenFile.finishWrite(output)
        } catch (error: Exception) {
            tokenFile.failWrite(output)
            throw error
        }
    }

    private fun deleteSavedSession() {
        tokenFile.delete()
        if (
            sessionFile.exists() ||
            File(sessionFile.path + ".bak").exists() ||
            File(sessionFile.path + ".new").exists()
        ) {
            throw IOException("Could not remove saved session.")
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(specification)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "mongosky_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 2

        val lock = Any()
        @Volatile var memorySession: SavedLoginSession? = null
        @Volatile var memoryLoaded = false
        var persistentSession = false
        var rememberNextLogin = true
    }
}
