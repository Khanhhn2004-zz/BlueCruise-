package com.vibegravity.bluecruise.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.auth.LoginError
import com.vibegravity.bluecruise.domain.auth.LoginResult
import com.vibegravity.bluecruise.domain.repository.CustomerSongSyncScheduler
import com.vibegravity.bluecruise.domain.repository.UserDataCleaner
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: AuthPreferencesStore
    private lateinit var syncScheduler: FakeCustomerSongSyncScheduler
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmpFolder.root, "auth_repo.preferences_pb") }
        )
        sessionStore = AuthPreferencesStore(dataStore)
        syncScheduler = FakeCustomerSongSyncScheduler()
    }

    @Test
    fun `successful login persists session`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"success":true,"accessToken":"token-123","userId":"user-456"}
                """.trimIndent()
            )
        )
        server.start()

        val repository = createRepository(server.url("/api/").toString())

        val result = repository.login(phone = "0123456789", password = "secret")

        assertEquals(
            LoginResult.Success(AuthSession(accessToken = "token-123", userId = "user-456")),
            result
        )
        assertEquals(
            AuthSession(accessToken = "token-123", userId = "user-456"),
            sessionStore.sessionFlow.first()
        )
        assertEquals(
            listOf(AuthSession(accessToken = "token-123", userId = "user-456")),
            syncScheduler.scheduledSessions
        )

        server.shutdown()
    }

    @Test
    fun `success false returns invalid credentials without saving`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"success":false,"message":"Invalid credentials"}
                """.trimIndent()
            )
        )
        server.start()

        val repository = createRepository(server.url("/api/").toString())

        val result = repository.login(phone = "0123456789", password = "wrong")

        assertEquals(
            LoginResult.Error(LoginError.INVALID_CREDENTIALS, "Invalid credentials"),
            result
        )
        assertEquals(AuthSession(), sessionStore.sessionFlow.first())
        assertTrue(syncScheduler.scheduledSessions.isEmpty())

        server.shutdown()
    }

    @Test
    fun `blank token in success payload returns server error`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"success":true,"accessToken":"","userId":"user-456"}
                """.trimIndent()
            )
        )
        server.start()

        val repository = createRepository(server.url("/api/").toString())

        val result = repository.login(phone = "0123456789", password = "secret")

        assertEquals(
            LoginResult.Error(LoginError.SERVER, "Login response is missing token or user ID"),
            result
        )
        assertEquals(AuthSession(), sessionStore.sessionFlow.first())
        assertTrue(syncScheduler.scheduledSessions.isEmpty())

        server.shutdown()
    }

    @Test
    fun `io exception returns network error`() = runTest {
        val repository = createRepository("http://127.0.0.1:1/api/")

        val result = repository.login(phone = "0123456789", password = "secret")

        assertTrue(result is LoginResult.Error)
        assertEquals(LoginError.NETWORK, (result as LoginResult.Error).error)
        assertEquals(AuthSession(), sessionStore.sessionFlow.first())
        assertTrue(syncScheduler.scheduledSessions.isEmpty())
    }

    @Test
    fun `logout clears stored session and user scoped data`() = runTest {
        sessionStore.saveSession(AuthSession(accessToken = "token-123", userId = "user-456"))
        val userDataCleaner = FakeUserDataCleaner()
        val repository = createRepository(
            baseUrl = "http://127.0.0.1:1/api/",
            userDataCleaner = userDataCleaner
        )

        repository.logout()

        assertEquals(AuthSession(), sessionStore.sessionFlow.first())
        assertEquals(1, userDataCleaner.clearCalls)
    }

    private fun createRepository(
        baseUrl: String,
        userDataCleaner: UserDataCleaner = FakeUserDataCleaner()
    ): DefaultAuthRepository {
        return DefaultAuthRepository(
            apiClient = LoginApiClient(
                okHttpClient = OkHttpClient(),
                json = json,
                baseUrl = baseUrl
            ),
            sessionRepository = sessionStore,
            customerSongSyncScheduler = syncScheduler,
            userDataCleaner = userDataCleaner
        )
    }

    private class FakeCustomerSongSyncScheduler : CustomerSongSyncScheduler {
        val scheduledSessions = mutableListOf<AuthSession>()

        override fun scheduleAfterLogin(session: AuthSession) {
            scheduledSessions += session
        }
    }

    private class FakeUserDataCleaner : UserDataCleaner {
        var clearCalls = 0
            private set

        override suspend fun clearUserData() {
            clearCalls += 1
        }
    }
}
