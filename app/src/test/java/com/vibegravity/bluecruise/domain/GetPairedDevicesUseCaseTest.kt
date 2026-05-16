package com.vibegravity.bluecruise.domain

import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetPairedDevicesUseCaseTest {

    private val bluetoothAdapterRepo = mockk<IBluetoothAdapterRepo>()
    private lateinit var useCase: GetPairedDevicesUseCase

    @Before
    fun setup() {
        useCase = GetPairedDevicesUseCase(bluetoothAdapterRepo)
    }

    @Test
    fun `invoke returns list from repository`() {
        val devices = listOf(
            BluetoothDeviceDomain("Car BT", "AA:BB:CC:DD:EE:FF"),
            BluetoothDeviceDomain("Phone", "11:22:33:44:55:66")
        )
        every { bluetoothAdapterRepo.getPairedDevices() } returns devices

        val result = useCase()

        assertEquals(2, result.size)
        assertEquals("Car BT", result[0].name)
        assertEquals("AA:BB:CC:DD:EE:FF", result[0].macAddress)
        assertEquals("Phone", result[1].name)
        verify(exactly = 1) { bluetoothAdapterRepo.getPairedDevices() }
    }

    @Test
    fun `invoke returns empty list when repository returns empty`() {
        every { bluetoothAdapterRepo.getPairedDevices() } returns emptyList()

        val result = useCase()

        assertEquals(0, result.size)
        verify(exactly = 1) { bluetoothAdapterRepo.getPairedDevices() }
    }
}
