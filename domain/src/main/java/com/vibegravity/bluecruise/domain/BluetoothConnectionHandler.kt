package com.vibegravity.bluecruise.domain

import javax.inject.Inject

enum class BluetoothEventType {
    CONNECTED,
    DISCONNECTED
}

sealed class BluetoothConnectionAction {
    object StartPlayback : BluetoothConnectionAction()
    object StopPlayback : BluetoothConnectionAction()
    object NoOp : BluetoothConnectionAction()
}

class BluetoothConnectionHandler @Inject constructor(
    private val verifyTargetBluetoothDeviceUseCase: VerifyTargetBluetoothDeviceUseCase
) {
    suspend fun handle(eventType: BluetoothEventType, deviceMac: String?): BluetoothConnectionAction {
        if (deviceMac == null) {
            return BluetoothConnectionAction.NoOp
        }

        val isTarget = verifyTargetBluetoothDeviceUseCase(deviceMac)
        if (!isTarget) {
            return BluetoothConnectionAction.NoOp
        }

        return when (eventType) {
            BluetoothEventType.CONNECTED -> BluetoothConnectionAction.StartPlayback
            BluetoothEventType.DISCONNECTED -> BluetoothConnectionAction.StopPlayback
        }
    }
}

