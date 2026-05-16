package com.vibegravity.bluecruise.domain.repository

interface UserDataCleaner {
    suspend fun clearUserData()
}
