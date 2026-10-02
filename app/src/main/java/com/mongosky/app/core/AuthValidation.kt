package com.mongosky.app.core

import com.mongosky.app.data.model.SignupRequest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneOffset

object AuthValidation {

    private val emailPattern =
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    private val phonePattern =
        Regex("^(\\+8801|01)[3-9][0-9]{8}$")

    private val allowedGenders =
        setOf("male", "female", "other")

    fun credentials(
        emailOrPhone: String,
        password: String
    ): String? {
        val identity = emailOrPhone.trim()

        if (
            !emailPattern.matches(identity) &&
            !phonePattern.matches(identity)
        ) {
            return "Enter a valid email or Bangladesh phone number."
        }

        val passwordLength =
            password.codePointCount(0, password.length)

        if (passwordLength < 6) {
            return "Use a password with at least 6 characters."
        }

        if (password.toByteArray(Charsets.UTF_8).size > 72) {
            return "This password is too long. Please use a shorter one."
        }

        return null
    }

    fun signup(request: SignupRequest): String? {
        val credentialsError = credentials(
            emailOrPhone = request.emailOrPhone,
            password = request.password
        )

        if (credentialsError != null) {
            return credentialsError
        }

        val firstName = request.firstName.trim()
        val lastName = request.lastName.trim()

        if (firstName.isBlank() || lastName.isBlank()) {
            return "Enter your first and last name."
        }

        val combinedName =
            (firstName + lastName).filterNot { it.isWhitespace() }

        val nameLength =
            combinedName.codePointCount(0, combinedName.length)

        if (nameLength > 30) {
            return "Your first and last name together can contain up to 30 characters."
        }

        if (request.gender !in allowedGenders) {
            return "Select your gender."
        }

        if (request.birthYear < 1900) {
            return "Enter a valid date of birth."
        }

        val birthDate = try {
            LocalDate.of(
                request.birthYear,
                request.birthMonth,
                request.birthDay
            )
        } catch (_: DateTimeException) {
            return "Enter a valid date of birth."
        }

        val today = LocalDate.now(ZoneOffset.UTC)

        if (birthDate.isAfter(today)) {
            return "Your date of birth cannot be in the future."
        }

        return null
    }
}