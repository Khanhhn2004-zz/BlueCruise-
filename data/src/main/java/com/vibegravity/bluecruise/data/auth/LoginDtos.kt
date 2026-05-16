package com.vibegravity.bluecruise.data.auth

data class LoginApiResponse(
    val httpStatusCode: Int,
    val success: Boolean,
    val accessToken: String?,
    val userId: String?,
    val message: String?
)
