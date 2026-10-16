package com.mongosky.app.session

import com.mongosky.app.auth.AuthSession
import java.util.Base64
import org.json.JSONObject

/** Local JWT claims are routing hints only. Every protected API still authenticates on the server. */
internal object SessionStartupPolicy {
    fun isExpired(token: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiry = (claims(token)?.opt("exp") as? Number)?.toLong() ?: return false
        return expiry <= nowMillis / 1_000
    }

    /** Upgrade token-only v1 installs without a network gate when the token carries an identity. */
    fun legacyAccount(token: String): AuthSession? {
        val claims = claims(token) ?: return null
        val ids = listOf("sub", "id", "user_id", "userId", "_id")
            .mapNotNull { (claims.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
        // Ambiguous or opaque tokens must be resolved once by /auth/me, never guessed.
        val id = ids.singleOrNull()?.takeIf { value ->
            value.length == 24 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        } ?: return null
        return AuthSession(token, id,
            claims.optString("first_name").ifBlank { claims.optString("name") },
            claims.optString("last_name"))
    }

    private fun claims(token: String): JSONObject? = try {
        val parts = token.split('.')
        if (parts.size != 3 || parts[1].length !in 1..32_768) null
        else JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
    } catch (_: Exception) { null }
}
