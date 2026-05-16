package com.vibegravity.bluecruise.receiver

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.service.KeepAliveService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class KeepAliveServiceRestorerTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val baseContext = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `restoreIfEnabled starts KeepAliveService when keep alive is enabled`() = runTest {
        every { settingsRepository.keepAppAliveFlow } returns flowOf(true)
        every { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        val context = RecordingContext(baseContext)

        KeepAliveServiceRestorer.restoreIfEnabled(context, settingsRepository, isBluetoothEnabled = true)

        assertEquals(
            KeepAliveService::class.java.name,
            context.lastStartedService?.component?.className
        )
    }

    @Test
    fun `restoreIfEnabled does nothing when keep alive is disabled`() = runTest {
        every { settingsRepository.keepAppAliveFlow } returns flowOf(false)
        val context = RecordingContext(baseContext)

        KeepAliveServiceRestorer.restoreIfEnabled(context, settingsRepository, isBluetoothEnabled = true)

        assertNull(context.lastStartedService)
    }

    @Test
    fun `restoreIfEnabled does nothing when no target device configured`() = runTest {
        every { settingsRepository.keepAppAliveFlow } returns flowOf(true)
        every { settingsRepository.targetMacFlow } returns flowOf(null)
        val context = RecordingContext(baseContext)

        KeepAliveServiceRestorer.restoreIfEnabled(context, settingsRepository, isBluetoothEnabled = true)

        assertNull(context.lastStartedService)
    }

    @Test
    fun `restoreIfEnabled does nothing when bluetooth is disabled`() = runTest {
        every { settingsRepository.keepAppAliveFlow } returns flowOf(true)
        every { settingsRepository.targetMacFlow } returns flowOf("AA:BB:CC:DD:EE:FF")
        val context = RecordingContext(baseContext)

        KeepAliveServiceRestorer.restoreIfEnabled(context, settingsRepository, isBluetoothEnabled = false)

        assertNull(context.lastStartedService)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var lastStartedService: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName {
            lastStartedService = service
            return service.component ?: ComponentName(this, KeepAliveService::class.java)
        }

        override fun startService(service: Intent): ComponentName {
            lastStartedService = service
            return service.component ?: ComponentName(this, KeepAliveService::class.java)
        }
    }
}
