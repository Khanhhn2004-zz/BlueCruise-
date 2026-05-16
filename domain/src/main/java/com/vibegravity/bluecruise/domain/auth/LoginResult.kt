package com.vibegravity.bluecruise.domain.auth

sealed interface LoginResult {
    data class Success(val session: AuthSession) : LoginResult

    data class Error(
        val error: LoginError,
        val message: String
    ) : LoginResult
}
