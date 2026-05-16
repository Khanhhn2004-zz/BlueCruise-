package com.vibegravity.bluecruise.domain

import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import javax.inject.Inject

data class BluetoothDeviceDomain(
    val name: String,
    val macAddress: String
)

class GetPairedDevicesUseCase @Inject constructor(
    private val bluetoothAdapterRepo: IBluetoothAdapterRepo
) {
    operator fun invoke(): List<BluetoothDeviceDomain> {
        return bluetoothAdapterRepo.getPairedDevices()
    }
}

