package com.vibegravity.bluecruise.receiver

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.data.PreferencesManager
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import com.vibegravity.bluecruise.service.KeepAliveService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE, sdk = [30])
class AndroidAutoDetectionReceiverTest {

    @Test
    fun `android auto detection receiver restores keep alive only and never starts playback`() = runTest {
        val preferences = mockk<PreferencesManager>()
        every { preferences.keepAppAliveFlow } returns flowOf(true)
        every { preferences.autoPlayOnAndroidAutoFlow } returns flowOf(true)
        val context = RecordingDetectionContext(ApplicationProvider.getApplicationContext<Application>())
        val receiver = AndroidAutoDetectionReceiver(
            receiverScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            restoreDelayMs = 0L
        ).also {
            setField(it, "preferencesManager", preferences)
        }

        receiver.restoreServicesIfNeeded(context)
        advanceUntilIdle()

        assertTrue(context.startedServices.any { it.component?.className == KeepAliveService::class.java.name })
        assertFalse(context.startedServices.any { it.action == AutoPlayMusicService.ACTION_START_PLAYBACK })
    }

    private fun setField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private class RecordingDetectionContext(base: Context) : ContextWrapper(base) {
        val startedServices = mutableListOf<Intent>()

        override fun startForegroundService(service: Intent): ComponentName {
            startedServices += service
            return service.component ?: ComponentName(this, KeepAliveService::class.java)
        }

        override fun startService(service: Intent): ComponentName {
            startedServices += service
            return service.component ?: ComponentName(this, KeepAliveService::class.java)
        }
    }
}
