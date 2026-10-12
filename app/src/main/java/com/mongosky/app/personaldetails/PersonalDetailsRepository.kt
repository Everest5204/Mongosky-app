package com.mongosky.app.personaldetails

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PersonalDetailsRepository(
    private val baseUrl: String = "https://api.mongosky.com/api/",
    private val client: OkHttpClient = sharedClient
) : PersonalDetailsSource {
    override suspend fun load(token: String, owner: String) = parse(exchange(token, "user/personal-details"), owner)
    override suspend fun saveName(token: String, owner: String, first: String, last: String): PersonalDetails {
        val response = exchange(token, "user/name", JSONObject().put("first_name", first).put("last_name", last))
        val name = response.optJSONObject("name") ?: invalid()
        if (name.optString("first_name") != first || name.optString("last_name") != last) invalid()
        val confirmed = load(token, owner)
        if (confirmed.firstName != first || confirmed.lastName != last) invalid()
        return confirmed
    }
    override suspend fun saveDetails(token: String, owner: String, revision: Long, editor: DetailsEditor, value: String): PersonalDetails {
        require(revision >= 0 && editor != DetailsEditor.NAME)
        val field = if (editor == DetailsEditor.BIRTHDAY) "birth_date" else "gender"
        val body = JSONObject().put("expected_revision", revision).put(field, value)
        val result = parse(exchange(token, "user/personal-details", body), owner)
        if ((if (editor == DetailsEditor.BIRTHDAY) result.birthDate else result.gender) != value ||
            result.revision < revision || result.revision > revision + 1) invalid()
        return result
    }
    private fun parse(root: JSONObject, owner: String): PersonalDetails {
        val d = root.optJSONObject("details") ?: invalid()
        if (d.optString("id") != owner || !owner.matches(Regex("[a-f0-9]{24}")) || d.has("error")) invalid()
        fun string(key: String): String = (d.opt(key) as? String) ?: invalid()
        val revision = d.opt("revision")
        if (revision !is Number || revision.toLong() < 0 || revision.toDouble() != revision.toLong().toDouble()) invalid()
        val birthday = string("birth_date")
        if (DetailsPolicy.birthdayError(birthday) != null) invalid()
        val verified = d.opt("email_verified") as? Boolean ?: invalid()
        val canChange = d.opt("name_can_change") as? Boolean ?: invalid()
        val next = if (!d.has("name_next_change_at") || d.isNull("name_next_change_at")) null else string("name_next_change_at").also { runCatching { Instant.parse(it) }.getOrElse { invalid() } }
        return PersonalDetails(owner, string("first_name"), string("last_name"), string("email_or_phone"), verified,
            string("profile_image_url").takeIf { it.startsWith("https://") && it.length <= 4096 }, birthday,
            string("gender"), revision.toLong(), canChange, next)
    }
    private suspend fun exchange(token: String, path: String, json: JSONObject? = null): JSONObject = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").header("Cache-Control", "no-store")
        if (json != null) {
            val encoded = json.toString()
            val body = object : RequestBody() {
                override fun contentType() = "application/json; charset=utf-8".toMediaType()
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.writeUtf8(encoded) }
            }
            request.patch(body)
        }
        val call = client.newCall(request.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(PersonalDetailsException(message = "Could not connect. Check your connection and try again.", cause = e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = response.use { r ->
                        if (r.code != 200) throw PersonalDetailsException(r.code, when (r.code) {
                            401 -> "Please sign in again."
                            403, 409 -> "Your details changed or your name is locked. Refresh to check before saving."
                            404, 405 -> "Personal Details is not available yet. Try again after the backend update."
                            429 -> "Too many attempts. Please try again later."
                            400 -> "Check your details and try again."
                            else -> "Could not confirm the update. Refresh your details to check."
                        })
                        val body = r.body ?: invalid()
                        if (body.contentLength() > MAX_BODY) invalid()
                        val source = body.source(); if (source.request(MAX_BODY + 1)) invalid()
                        val parsed = runCatching { JSONObject(source.readUtf8()) }.getOrElse { invalid() }
                        if (parsed.has("error")) invalid()
                        parsed
                    }
                    if (continuation.isActive) continuation.resume(root)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    private fun invalid(): Nothing = throw PersonalDetailsException(message = "Could not confirm the response. Refresh your details to check.")
    companion object {
        private const val MAX_BODY = 16_384L
        private val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}
