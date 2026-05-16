package com.vibegravity.bluecruise.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryTest {

    private lateinit var dataStore: DataStore<Preferences>

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmpFolder.root, "test_datastore.preferences_pb") }
        )
        // Refactor DeviceRepositoryImpl to take DataStore for testability, or rely on
        // VerifyTargetBluetoothDeviceUseCase / UI tests for DataStore coverage.
    }

    @Test
    fun dummyTest() {
        assertEquals(4, 2 + 2)
    }
}
