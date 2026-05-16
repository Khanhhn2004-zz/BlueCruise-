package com.vibegravity.bluecruise.receiver

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.vibegravity.bluecruise.domain.repository.IBluetoothAdapterRepo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoReadinessProbeTest {
    private val repo = mockk<IBluetoothAdapterRepo>()

    @Test
    fun `candidate is true when any fast signal is true and false when all are false`() {
        assertFalse(AndroidAutoReadinessProbe.isCandidate(
            AndroidAutoReadinessSnapshot(true, false, false, false)
        ))
        assertTrue(AndroidAutoReadinessProbe.isCandidate(
            AndroidAutoReadinessSnapshot(false, true, false, false)
        ))
        assertTrue(AndroidAutoReadinessProbe.isCandidate(
            AndroidAutoReadinessSnapshot(false, false, true, false)
        ))
        assertTrue(AndroidAutoReadinessProbe.isCandidate(
            AndroidAutoReadinessSnapshot(false, false, false, true)
        ))
        assertFalse(AndroidAutoReadinessProbe.isCandidate(
            AndroidAutoReadinessSnapshot(false, false, false, false)
        ))
    }

    @Test
    fun `ready ignores cached target device flag and only requires fast readiness signals`() {
        assertTrue(AndroidAutoReadinessProbe.isReady(
            AndroidAutoReadinessSnapshot(false, true, true, true)
        ))
        assertFalse(AndroidAutoReadinessProbe.isReady(
            AndroidAutoReadinessSnapshot(true, false, true, true)
        ))
        assertFalse(AndroidAutoReadinessProbe.isReady(
            AndroidAutoReadinessSnapshot(true, true, false, true)
        ))
        assertFalse(AndroidAutoReadinessProbe.isReady(
            AndroidAutoReadinessSnapshot(true, true, true, false)
        ))
    }

    @Test
    fun `sampleFastSignals reads repo ui mode and audio outputs without re-running device heuristic`() {
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val audioManager = mockk<AudioManager>()
        val remoteSubmix = mockk<AudioDeviceInfo>()
        val probe = AndroidAutoReadinessProbe(context, repo)

        every { repo.isAndroidAutoProcessRunning() } returns true
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_CAR
        every { remoteSubmix.type } returns AudioDeviceInfo.TYPE_REMOTE_SUBMIX
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(remoteSubmix)

        val fastSignals = probe.sampleFastSignals()

        assertEquals(AndroidAutoFastSignals(true, true, true), fastSignals)
        coVerify(exactly = 0) { repo.isAndroidAutoDevice(any()) }
    }
}
