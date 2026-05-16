package com.vibegravity.bluecruise.domain

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyTargetBluetoothDeviceUseCaseTest {

    private val settingsRepository = mockk<com.vibegravity.bluecruise.domain.repository.SettingsRepository>()
    
    private lateinit var useCase: VerifyTargetBluetoothDeviceUseCase

    @Before
    fun setup() {
        useCase = VerifyTargetBluetoothDeviceUseCase(settingsRepository)
    }

    @Test
    fun `invoke with matching mac address and auto play enabled returns true`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("00:11:22:33:44:55")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)
        
        val result = useCase("00:11:22:33:44:55")
        
        assertTrue(result)
    }

    @Test
    fun `invoke with non-matching mac address returns false`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)
        
        val result = useCase("00:11:22:33:44:55")
        
        assertFalse(result)
    }

    @Test
    fun `invoke with null target mac returns false`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf(null)
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)
        
        val result = useCase("00:11:22:33:44:55")
        
        assertFalse(result)
    }

    @Test
    fun `invoke with auto play disabled returns false`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("00:11:22:33:44:55")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(false)
        
        val result = useCase("00:11:22:33:44:55")
        
        assertFalse(result)
    }

    @Test
    fun `invoke with matching mac address different case returns true`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)
        
        val result = useCase("aa:bb:cc:dd:ee:ff")
        
        assertTrue(result)
    }

    @Test
    fun `invoke with empty string target mac returns false`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)

        val result = useCase("00:11:22:33:44:55")

        assertFalse(result)
    }

    @Test
    fun `invoke with empty string connected device mac returns false`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("00:11:22:33:44:55")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)

        val result = useCase("")

        assertFalse(result)
    }

    /** When Bluetooth is on but no device is connected yet (e.g. device not ready), must not trigger playback. */
    @Test
    fun `invoke with null connected device mac returns false so no playback when device not ready`() = runTest {
        coEvery { settingsRepository.targetMacFlow } returns flowOf("00:11:22:33:44:55")
        coEvery { settingsRepository.autoPlayEnabledFlow } returns flowOf(true)

        val result = useCase(null)

        assertFalse(result)
    }
}
