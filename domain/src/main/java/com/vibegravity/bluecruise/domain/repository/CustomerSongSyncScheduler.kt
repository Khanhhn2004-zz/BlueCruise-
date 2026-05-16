package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.auth.AuthSession

interface CustomerSongSyncScheduler {
    fun scheduleAfterLogin(session: AuthSession)
}
