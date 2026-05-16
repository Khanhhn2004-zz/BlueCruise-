package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncOutcome
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger

interface CustomerSongSyncRepository {
    suspend fun sync(
        session: AuthSession,
        trigger: CustomerSongSyncTrigger
    ): CustomerSongSyncOutcome
}
