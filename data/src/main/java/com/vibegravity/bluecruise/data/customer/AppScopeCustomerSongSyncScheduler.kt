package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.qualifier.ApplicationScope
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Singleton
class AppScopeCustomerSongSyncScheduler @Inject constructor(
    @ApplicationScope
    private val scope: CoroutineScope,
    private val repository: CustomerSongSyncRepository
) : CustomerSongSyncScheduler {
    private val mutex = Mutex()

    override fun scheduleAfterLogin(session: AuthSession) {
        scope.launch {
            if (!mutex.tryLock()) {
                return@launch
            }
            try {
                repository.sync(session, CustomerSongSyncTrigger.LOGIN)
            } finally {
                mutex.unlock()
            }
        }
    }
}
