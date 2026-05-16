package com.vibegravity.bluecruise.domain

interface IBluetoothManagerWrapper {
    fun isBluetoothEnabled(): Boolean
    fun hasRequiredPermissions(): Boolean
    fun isBatteryOptimizationIgnored(): Boolean
    fun isAutoStartGranted(): Boolean
}

