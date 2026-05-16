package com.vibegravity.bluecruise.receiver

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AndroidAutoFastSignals(
    val hasGearheadProcess: Boolean,
    val isCarMode: Boolean,
    val hasRemoteSubmixOutput: Boolean
)

data class AndroidAutoReadinessSnapshot(
    val isAndroidAutoTargetDevice: Boolean,
    val hasGearheadProcess: Boolean,
    val isCarMode: Boolean,
    val hasRemoteSubmixOutput: Boolean
)

class AndroidAutoReadinessProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapterRepo: IBluetoothAdapterRepo
) {
    fun sampleFastSignals(): AndroidAutoFastSignals {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()

        return AndroidAutoFastSignals(
            hasGearheadProcess = bluetoothAdapterRepo.isAndroidAutoProcessRunning(),
            isCarMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR,
            hasRemoteSubmixOutput = outputs.any { it.type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX }
        )
    }

    companion object {
        fun combine(
            isAndroidAutoTargetDevice: Boolean,
            fastSignals: AndroidAutoFastSignals
        ): AndroidAutoReadinessSnapshot {
            return AndroidAutoReadinessSnapshot(
                isAndroidAutoTargetDevice = isAndroidAutoTargetDevice,
                hasGearheadProcess = fastSignals.hasGearheadProcess,
                isCarMode = fastSignals.isCarMode,
                hasRemoteSubmixOutput = fastSignals.hasRemoteSubmixOutput
            )
        }

        fun isCandidate(snapshot: AndroidAutoReadinessSnapshot): Boolean {
            return snapshot.hasGearheadProcess ||
                snapshot.isCarMode ||
                snapshot.hasRemoteSubmixOutput
        }

        fun isReady(snapshot: AndroidAutoReadinessSnapshot): Boolean {
            return snapshot.hasGearheadProcess &&
                snapshot.isCarMode &&
                snapshot.hasRemoteSubmixOutput
        }
    }
}
