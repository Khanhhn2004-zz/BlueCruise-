package com.vibegravity.bluecruise.auth

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.auth.LoginError
import com.vibegravity.bluecruise.domain.auth.LoginResult
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank phone emits phone validation error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPasswordChanged("secret")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Phone is required", viewModel.uiState.value.phoneError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `blank password emits password validation error`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPhoneChanged("0123456789")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Password is required", viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful submit emits navigation event`() = runTest(testDispatcher) {
        authRepository.result = LoginResult.Success(
            AuthSession(accessToken = "token-123", userId = "user-456")
        )
        val viewModel = createViewModel()
        viewModel.onPhoneChanged("0123456789")
        viewModel.onPasswordChanged("secret")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.navigateToMain)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.submitError)
        assertEquals("0123456789" to "secret", authRepository.lastRequest)
    }

    @Test
    fun `repository error renders message and clears loading`() = runTest(testDispatcher) {
        authRepository.result = LoginResult.Error(
            error = LoginError.INVALID_CREDENTIALS,
            message = "Invalid credentials"
        )
        val viewModel = createViewModel()
        viewModel.onPhoneChanged("0123456789")
        viewModel.onPasswordChanged("wrong")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Invalid credentials", viewModel.uiState.value.submitError)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.navigateToMain)
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            authRepository = authRepository,
            ioDispatcher = testDispatcher
        )
    }

    private class FakeAuthRepository : AuthRepository {
        var result: LoginResult = LoginResult.Error(LoginError.UNKNOWN, "Unexpected")
        var lastRequest: Pair<String, String>? = null

        override suspend fun login(phone: String, password: String): LoginResult {
            lastRequest = phone to password
            return result
        }

        override suspend fun logout() = Unit
    }
}
