package com.vibegravity.bluecruise.data

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.ParcelUuid
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BluetoothAdapterRepoProcessDetectionTest {

    @Test
    fun `isAndroidAutoProcessRunning returns true for matching subprocess regardless of importance`() {
        val context = mockk<Context>(relaxed = true)
        val activityManager = mockk<ActivityManager>()
        val process = ActivityManager.RunningAppProcessInfo().apply {
            processName = "com.google.android.projection.gearhead:car"
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        }
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.runningAppProcesses } returns mutableListOf(process)

        val repo = BluetoothAdapterRepo(context)

        assertTrue(repo.isAndroidAutoProcessRunning())
    }

    @Test
    fun `isAndroidAutoDevice stays false when only gearhead is running and no device heuristic matches`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val activityManager = mockk<ActivityManager>()
        val process = ActivityManager.RunningAppProcessInfo().apply {
            processName = "com.google.android.projection.gearhead:shared"
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.getRemoteDevice("11:22:33:44:55:66") } returns device
        every { device.uuids } returns emptyArray<ParcelUuid>()
        every { device.name } returns "Plain Car Stereo"
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.runningAppProcesses } returns mutableListOf(process)

        val repo = BluetoothAdapterRepo(context)

        assertFalse(repo.isAndroidAutoDevice("11:22:33:44:55:66"))
    }

    @Test
    fun `isAndroidAutoDevice returns true for car audio class`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val bluetoothClass = mockk<BluetoothClass>()
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.getRemoteDevice("11:22:33:44:55:66") } returns device
        every { device.uuids } returns emptyArray<ParcelUuid>()
        every { device.bluetoothClass } returns bluetoothClass
        every { bluetoothClass.deviceClass } returns BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
        every { device.name } returns "CarBt-0214"

        val repo = BluetoothAdapterRepo(context)

        assertTrue(repo.isAndroidAutoDevice("11:22:33:44:55:66"))
    }
}
