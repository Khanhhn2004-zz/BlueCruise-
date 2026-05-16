package com.vibegravity.bluecruise.auth

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchGateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthSessionRepository(flow { emit(AuthSession()) })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `logged out session emits Login destination`() = runTest(testDispatcher) {
        repository = FakeAuthSessionRepository(flow { emit(AuthSession()) })
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(LaunchGateDestination.Login, viewModel.destination.value)
    }

    @Test
    fun `logged in session emits Main destination`() = runTest(testDispatcher) {
        repository = FakeAuthSessionRepository(
            flow { emit(AuthSession(accessToken = "token-123", userId = "user-456")) }
        )
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(LaunchGateDestination.Main, viewModel.destination.value)
    }

    @Test
    fun `session read failure clears state and emits Login destination`() = runTest(testDispatcher) {
        repository = FakeAuthSessionRepository(
            flow { throw IllegalStateException("broken datastore") }
        )
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertTrue(repository.clearCalled)
        assertEquals(LaunchGateDestination.Login, viewModel.destination.value)
    }

    private fun createViewModel(): LaunchGateViewModel {
        return LaunchGateViewModel(
            authSessionRepository = repository,
            ioDispatcher = testDispatcher
        )
    }

    private class FakeAuthSessionRepository(
        override val sessionFlow: Flow<AuthSession>
    ) : AuthSessionRepository {
        var clearCalled: Boolean = false

        override suspend fun saveSession(session: AuthSession) = Unit

        override suspend fun clearSession() {
            clearCalled = true
        }
    }
}
