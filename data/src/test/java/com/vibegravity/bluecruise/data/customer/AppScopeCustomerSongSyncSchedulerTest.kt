package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncOutcome
import com.vibegravity.bluecruise.domain.customer.CustomerSongSyncTrigger
import com.vibegravity.bluecruise.domain.customer.SlotSyncResult
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppScopeCustomerSongSyncSchedulerTest {

    @Test
    fun `schedule after login launches background sync with login trigger`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val repository = FakeCustomerSongSyncRepository()
        val scheduler = AppScopeCustomerSongSyncScheduler(
            scope = scope,
            repository = repository
        )
        val session = AuthSession(accessToken = "token-123", userId = "user-456")

        scheduler.scheduleAfterLogin(session)
        scope.advanceUntilIdle()

        assertEquals(listOf(session to CustomerSongSyncTrigger.LOGIN), repository.calls)
    }

    @Test
    fun `schedule after login dedupes overlapping sync requests`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCustomerSongSyncRepository(gate)
        val scheduler = AppScopeCustomerSongSyncScheduler(
            scope = scope,
            repository = repository
        )

        scheduler.scheduleAfterLogin(AuthSession(accessToken = "token-1", userId = "user-1"))
        scheduler.scheduleAfterLogin(AuthSession(accessToken = "token-2", userId = "user-2"))
        scope.advanceUntilIdle()
        gate.complete(Unit)
        scope.advanceUntilIdle()

        assertEquals(1, repository.calls.size)
        assertEquals(AuthSession(accessToken = "token-1", userId = "user-1"), repository.calls.single().first)
    }

    private class FakeCustomerSongSyncRepository(
        private val gate: CompletableDeferred<Unit>? = null
    ) : CustomerSongSyncRepository {
        val calls = mutableListOf<Pair<AuthSession, CustomerSongSyncTrigger>>()

        override suspend fun sync(
            session: AuthSession,
            trigger: CustomerSongSyncTrigger
        ): CustomerSongSyncOutcome {
            calls += session to trigger
            gate?.await()
            return CustomerSongSyncOutcome(
                trigger = trigger,
                hello = SlotSyncResult.PreservedExisting(activeUri = null),
                goodbye = SlotSyncResult.PreservedExisting(activeUri = null)
            )
        }
    }
}
