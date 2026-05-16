package com.vibegravity.bluecruise.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibegravity.bluecruise.domain.auth.AuthSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AuthPreferencesStoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmpFolder.root, "auth_prefs.preferences_pb") }
        )
    }

    @Test
    fun `saveSession emits logged in session`() = runTest {
        val store = AuthPreferencesStore(dataStore)
        val expected = AuthSession(
            accessToken = "token-123",
            userId = "user-456"
        )

        assertEquals(AuthSession(), store.sessionFlow.first())

        store.saveSession(expected)

        assertEquals(expected, store.sessionFlow.first())
    }

    @Test
    fun `clearSession emits logged out session`() = runTest {
        val store = AuthPreferencesStore(dataStore)

        store.saveSession(AuthSession(accessToken = "token-123", userId = "user-456"))
        assertEquals(AuthSession(accessToken = "token-123", userId = "user-456"), store.sessionFlow.first())

        store.clearSession()

        assertEquals(AuthSession(), store.sessionFlow.first())
    }
}
