package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.auth.LoginResult

interface AuthRepository {
    suspend fun login(phone: String, password: String): LoginResult

    suspend fun logout()
}
