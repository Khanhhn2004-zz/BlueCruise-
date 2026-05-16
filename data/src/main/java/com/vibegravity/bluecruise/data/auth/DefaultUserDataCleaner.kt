package com.vibegravity.bluecruise.data.auth

import com.vibegravity.bluecruise.data.customer.CustomerSongFileStore
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.domain.repository.UserDataCleaner
import javax.inject.Inject

class DefaultUserDataCleaner @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val customerSongFileStore: CustomerSongFileStore
) : UserDataCleaner {

    override suspend fun clearUserData() {
        settingsRepository.clearUserScopedData()
        customerSongFileStore.clearAll()
    }
}
