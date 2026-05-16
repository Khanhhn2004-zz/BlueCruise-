package com.vibegravity.bluecruise.data.auth

import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.auth.LoginError
import com.vibegravity.bluecruise.domain.auth.LoginResult
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncScheduler
import com.vibegravity.bluecruise.domain.repository.UserDataCleaner
import java.io.IOException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val apiClient: LoginApiClient,
    private val sessionRepository: AuthSessionRepository,
    private val customerSongSyncScheduler: CustomerSongSyncScheduler,
    private val userDataCleaner: UserDataCleaner
) : AuthRepository {

    override suspend fun login(phone: String, password: String): LoginResult {
        if (phone.isBlank()) {
            return LoginResult.Error(
                error = LoginError.EMPTY_PHONE,
                message = PHONE_REQUIRED_MESSAGE
            )
        }
        if (password.isBlank()) {
            return LoginResult.Error(
                error = LoginError.EMPTY_PASSWORD,
                message = PASSWORD_REQUIRED_MESSAGE
            )
        }

        return try {
            val response = apiClient.login(phone = phone, password = password)
            when {
                !response.success && response.httpStatusCode == 401 -> invalidCredentials(response.message)
                !response.success && response.message.equals(INVALID_CREDENTIALS_MESSAGE, ignoreCase = true) ->
                    invalidCredentials(response.message)
                !response.success -> LoginResult.Error(
                    error = LoginError.SERVER,
                    message = response.message ?: GENERIC_LOGIN_FAILURE_MESSAGE
                )
                response.accessToken.isNullOrBlank() || response.userId.isNullOrBlank() -> LoginResult.Error(
                    error = LoginError.SERVER,
                    message = MISSING_SESSION_FIELDS_MESSAGE
                )
                else -> {
                    val session = AuthSession(
                        accessToken = response.accessToken,
                        userId = response.userId
                    )
                    sessionRepository.saveSession(session)
                    customerSongSyncScheduler.scheduleAfterLogin(session)
                    LoginResult.Success(session)
                }
            }
        } catch (_: IOException) {
            LoginResult.Error(
                error = LoginError.NETWORK,
                message = NETWORK_ERROR_MESSAGE
            )
        } catch (_: Throwable) {
            LoginResult.Error(
                error = LoginError.UNKNOWN,
                message = GENERIC_LOGIN_FAILURE_MESSAGE
            )
        }
    }

    override suspend fun logout() {
        sessionRepository.clearSession()
        userDataCleaner.clearUserData()
    }

    private fun invalidCredentials(message: String?): LoginResult.Error {
        return LoginResult.Error(
            error = LoginError.INVALID_CREDENTIALS,
            message = message ?: INVALID_CREDENTIALS_MESSAGE
        )
    }

    private companion object {
        const val PHONE_REQUIRED_MESSAGE = "Phone is required"
        const val PASSWORD_REQUIRED_MESSAGE = "Password is required"
        const val INVALID_CREDENTIALS_MESSAGE = "Invalid credentials"
        const val NETWORK_ERROR_MESSAGE = "Unable to connect to server"
        const val GENERIC_LOGIN_FAILURE_MESSAGE = "Unable to complete login"
        const val MISSING_SESSION_FIELDS_MESSAGE = "Login response is missing token or user ID"
    }
}
