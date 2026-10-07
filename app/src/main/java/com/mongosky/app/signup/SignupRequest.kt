package com.mongosky.app.signup

data class SignupRequest(
    val firstName: String,
    val lastName: String,
    val emailOrPhone: String,
    val password: String,
    val gender: String,
    val birthMonth: Int,
    val birthDay: Int,
    val birthYear: Int
)
