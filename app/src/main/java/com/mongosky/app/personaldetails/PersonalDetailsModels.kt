package com.mongosky.app.personaldetails

import java.time.LocalDate
import java.time.ZoneOffset

internal data class PersonalDetails(
    val id: String, val firstName: String, val lastName: String, val contact: String,
    val emailVerified: Boolean, val imageUrl: String?, val birthDate: String,
    val gender: String, val revision: Long, val nameCanChange: Boolean, val nameNextChangeAt: String?
) { val displayName: String get() = "$firstName $lastName".trim() }

internal enum class DetailsEditor { NAME, BIRTHDAY, GENDER }
internal data class DetailsDraft(val firstName: String = "", val lastName: String = "", val birthDate: String = "", val gender: String = "")
internal data class PersonalDetailsState(
    val details: PersonalDetails? = null, val loading: Boolean = false, val saving: Boolean = false,
    val error: String? = null, val editor: DetailsEditor? = null, val draft: DetailsDraft = DetailsDraft(),
    val draftError: String? = null, val discardOpen: Boolean = false, val expired: Boolean = false,
    val sessionChanged: Boolean = false, val uncertain: Boolean = false, val notice: String? = null,
    val nameEvent: Long = 0, val nameEventPending: Boolean = false
)
internal class PersonalDetailsException(val status: Int? = null, message: String, cause: Throwable? = null) : Exception(message, cause)
internal interface PersonalDetailsSource {
    suspend fun load(token: String, owner: String): PersonalDetails
    suspend fun saveName(token: String, owner: String, first: String, last: String): PersonalDetails
    suspend fun saveDetails(token: String, owner: String, revision: Long, editor: DetailsEditor, value: String): PersonalDetails
}
internal object DetailsPolicy {
    private val spaces = Regex("[\\p{javaWhitespace}\\p{javaSpaceChar}]+")
    fun normalizeName(value: String): String = value.split(spaces).filter { it.isNotEmpty() }.joinToString(" ")
    fun nameError(first: String, last: String): String? {
        val f = normalizeName(first); val l = normalizeName(last)
        return when {
            f.isEmpty() || l.isEmpty() -> "Enter your first and last name."
            (f + l).any { Character.isISOControl(it) } -> "Remove control characters from your name."
            (f + l).replace(" ", "").let { it.codePointCount(0, it.length) } > 30 -> "First and last name can contain up to 30 letters in total."
            else -> null
        }
    }
    fun birthdayError(value: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): String? {
        if (value.isEmpty()) return null
        val date = runCatching { LocalDate.parse(value) }.getOrNull()
        return if (date == null || date.toString() != value || date.year < 1900 || date > today) "Choose a valid birthday." else null
    }
    fun genderLabel(value: String): String = when (value) { "male" -> "Male"; "female" -> "Female"; "other" -> "Other"; else -> "Prefer not to say" }
    fun normalized(draft: DetailsDraft) = draft.copy(firstName = normalizeName(draft.firstName), lastName = normalizeName(draft.lastName))
    fun matches(details: PersonalDetails, editor: DetailsEditor, draft: DetailsDraft): Boolean = when (editor) {
        DetailsEditor.NAME -> details.firstName == normalizeName(draft.firstName) && details.lastName == normalizeName(draft.lastName)
        DetailsEditor.BIRTHDAY -> details.birthDate == draft.birthDate
        DetailsEditor.GENDER -> details.gender == draft.gender
    }
}
