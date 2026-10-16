package com.mongosky.app.session

import com.mongosky.app.auth.AuthSession
import com.mongosky.app.auth.authProfileImageUrl
import java.io.IOException
import org.json.JSONObject

/** Token and UI identity share one encrypted, atomic record; they cannot drift between accounts. */
internal data class SavedLoginSession(val token: String, val account: AuthSession? = null)

internal object SessionSnapshot {
    fun encode(saved: SavedLoginSession): ByteArray {
        require(saved.token.isNotBlank())
        val json = JSONObject().put("token", saved.token)
        saved.account?.let { account ->
            require(account.token == saved.token && account.userId.isNotBlank())
            json.put("account", JSONObject()
                .put("id", account.userId)
                .put("first_name", account.firstName)
                .put("last_name", account.lastName)
                .put("profile_image_url", account.profileImageUrl ?: JSONObject.NULL))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): SavedLoginSession {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val token = (json.opt("token") as? String)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Invalid saved session.")
        val account = if (json.has("account")) {
            val user = json.optJSONObject("account") ?: throw IOException("Invalid saved account.")
            val id = (user.opt("id") as? String)?.takeIf { it.isNotBlank() }
                ?: throw IOException("Invalid saved account.")
            AuthSession(token, id, user.optString("first_name"), user.optString("last_name"),
                authProfileImageUrl(user))
        } else null
        return SavedLoginSession(token, account)
    }
}
