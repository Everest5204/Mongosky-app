package com.mongosky.app.data.model

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