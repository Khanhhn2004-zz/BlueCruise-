package com.vibegravity.bluecruise.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothConnectionHandlerTest {

    private lateinit var handler: BluetoothConnectionHandler
    private val verifyTargetUseCase = mockk<VerifyTargetBluetoothDeviceUseCase>()

    @Before
    fun setup() {
        handler = BluetoothConnectionHandler(verifyTargetUseCase)
    }

    @Test
    fun `connected event for target device returns StartPlayback`() = runTest {
        coEvery { verifyTargetUseCase("00:11:22:33:44:55") } returns true

        val action = handler.handle(BluetoothEventType.CONNECTED, "00:11:22:33:44:55")

        assertTrue(action is BluetoothConnectionAction.StartPlayback)
        coVerify { verifyTargetUseCase("00:11:22:33:44:55") }
    }

    @Test
    fun `disconnected event for target device returns StopPlayback`() = runTest {
        coEvery { verifyTargetUseCase("00:11:22:33:44:55") } returns true

        val action = handler.handle(BluetoothEventType.DISCONNECTED, "00:11:22:33:44:55")

        assertTrue(action is BluetoothConnectionAction.StopPlayback)
        coVerify { verifyTargetUseCase("00:11:22:33:44:55") }
    }

    @Test
    fun `non target device returns NoOp`() = runTest {
        coEvery { verifyTargetUseCase("AA:BB:CC:DD:EE:FF") } returns false

        val action = handler.handle(BluetoothEventType.CONNECTED, "AA:BB:CC:DD:EE:FF")

        assertTrue(action is BluetoothConnectionAction.NoOp)
        coVerify { verifyTargetUseCase("AA:BB:CC:DD:EE:FF") }
    }

    @Test
    fun `null mac returns NoOp without calling use case`() = runTest {
        val action = handler.handle(BluetoothEventType.CONNECTED, null)

        assertTrue(action is BluetoothConnectionAction.NoOp)
    }

    @Test
    fun `empty string mac returns NoOp when use case returns false`() = runTest {
        coEvery { verifyTargetUseCase("") } returns false

        val action = handler.handle(BluetoothEventType.CONNECTED, "")

        assertTrue(action is BluetoothConnectionAction.NoOp)
        coVerify { verifyTargetUseCase("") }
    }

    /** When Bluetooth is turned on but device is not yet ready (no device in broadcast), must not start playback. */
    @Test
    fun `connected event with null device Mac returns NoOp so no sound when BT on but device not ready`() = runTest {
        val action = handler.handle(BluetoothEventType.CONNECTED, null)

        assertTrue(action is BluetoothConnectionAction.NoOp)
        // Handler returns early for null deviceMac without calling use case -> no StartPlayback, no sound
    }
}

