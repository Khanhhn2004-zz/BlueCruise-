package com.vibegravity.bluecruise.domain.repository

import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain
import kotlinx.coroutines.flow.Flow

interface IBluetoothAdapterRepo {
    fun getPairedDevices(): List<BluetoothDeviceDomain>
    fun isBluetoothEnabled(): Boolean
    /** Emits current and future Bluetooth on/off state. */
    fun bluetoothEnabledFlow(): Flow<Boolean>
    /** Returns MAC addresses of devices currently connected via A2DP (audio). */
    suspend fun getConnectedA2dpAddresses(): List<String>
    /** Check if a Bluetooth device is an Android Auto head unit. */
    suspend fun isAndroidAutoDevice(deviceAddress: String): Boolean
    /** Check if Android Auto process is currently running. */
    fun isAndroidAutoProcessRunning(): Boolean
}

