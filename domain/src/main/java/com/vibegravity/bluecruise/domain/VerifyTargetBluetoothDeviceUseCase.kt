package com.vibegravity.bluecruise.domain

import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Determines whether the connected Bluetooth device is the user's target car and auto-play is enabled.
 * Auto-play runs only when: (1) user has selected a target car (MAC) in the app, and (2) "Tự động phát" is on.
 */
class VerifyTargetBluetoothDeviceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(connectedDeviceMac: String?): Boolean {
        if (connectedDeviceMac == null) return false

        val targetMac = settingsRepository.targetMacFlow.firstOrNull()
        val isAutoPlayEnabled = settingsRepository.autoPlayEnabledFlow.firstOrNull() ?: true

        if (targetMac.isNullOrEmpty()) return false

        // Compare case-insensitive: BluetoothDevice.address may differ by OEM (e.g. AA:BB vs aa:bb)
        return isAutoPlayEnabled && targetMac.equals(connectedDeviceMac, ignoreCase = true)
    }
}

