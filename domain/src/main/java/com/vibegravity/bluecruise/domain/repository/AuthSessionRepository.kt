package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.auth.AuthSession
import kotlinx.coroutines.flow.Flow

interface AuthSessionRepository {
    val sessionFlow: Flow<AuthSession>

    suspend fun saveSession(session: AuthSession)

    suspend fun clearSession()
}
