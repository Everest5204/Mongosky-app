package com.mongosky.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(context: Context) {

    private val sessionFile = File(context.applicationContext.noBackupFilesDir, "auth_session")
    private val tokenFile = AtomicFile(sessionFile)

    /** Applies to the next successful save; this method performs no disk I/O. */
    fun setRememberMeForNextLogin(rememberMe: Boolean) {
        synchronized(lock) {
            rememberNextLogin = rememberMe
        }
    }

    suspend fun save(token: String) = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Token must not be empty." }

        synchronized(lock) {
            if (!rememberNextLogin) {
                // Every TokenStore instance can read this session in the current
                // process. No token file remains for a later process to restore.
                deleteSavedSession()
                memoryToken = token
                return@synchronized
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            // Keep the existing saved-session format and key alias compatible.
            val payload = byteArrayOf(
                FORMAT_VERSION.toByte(),
                iv.size.toByte()
            ) + iv + encrypted

            val output = tokenFile.startWrite()
            try {
                output.write(payload)
                tokenFile.finishWrite(output)
                memoryToken = token
            } catch (error: Exception) {
                tokenFile.failWrite(output)
                throw error
            }
        }
    }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            memoryToken?.let { return@synchronized it }
            val payload = try {
                tokenFile.readFully()
            } catch (error: FileNotFoundException) {
                if (sessionFile.exists() || File(sessionFile.path + ".bak").exists()) {
                    throw error
                }
                return@synchronized null
            }
            if (payload.size < 2) throw IOException("Invalid saved session.")

            val version = payload[0].toInt() and 0xff
            val ivLength = payload[1].toInt() and 0xff
            if (
                version != FORMAT_VERSION ||
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
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
                .also { memoryToken = it }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            deleteSavedSession()
            memoryToken = null
            rememberNextLogin = true
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
        const val FORMAT_VERSION = 1

        val lock = Any()
        var memoryToken: String? = null
        var rememberNextLogin = true
    }
}
