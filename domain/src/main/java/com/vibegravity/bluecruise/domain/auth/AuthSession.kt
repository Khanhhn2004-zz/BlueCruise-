package com.vibegravity.bluecruise.domain.auth

data class AuthSession(
    val accessToken: String = "",
    val userId: String = ""
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()
}
