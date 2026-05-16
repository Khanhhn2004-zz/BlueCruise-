package com.vibegravity.bluecruise.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.data.PreferencesManager
import com.vibegravity.bluecruise.service.FloatingBubbleController
import com.vibegravity.bluecruise.service.KeepAliveService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BluetoothFragmentTest {

    private lateinit var application: Application
    private lateinit var preferencesManager: PreferencesManager
    private var activityController: ActivityController<MainActivity>? = null
    private val originalManufacturer = Build.MANUFACTURER

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(application)

        shadowOf(application).grantPermissions(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
        runBlocking(Dispatchers.IO) {
            preferencesManager.setKeepAppAlive(false)
            preferencesManager.saveAudioFilePath(null)
            preferencesManager.setAutoStartDismissed(false)
        }

        setManufacturer("google")
        setIgnoringBatteryOptimizations(true)
        setAutoStartMode(AppOpsManager.MODE_IGNORED)
    }

    @After
    fun tearDown() {
        destroyActivity()
        setManufacturer(originalManufacturer)
        shadowOf(application).clearStartedServices()
    }

    @Test
    fun `keep alive sync helper starts and stops service`() {
        val controller = launchActivity()
        val activity = controller.get()
        val fragment = activity.requireBluetoothFragment()
        val shadowActivity = shadowOf(activity)

        shadowActivity.clearStartedServices()

        fragment.syncKeepAliveService(enabled = true)

        assertEquals(
            KeepAliveService::class.java.name,
            shadowActivity.nextStartedService.component?.className
        )

        fragment.syncKeepAliveService(enabled = false)

        assertEquals(
            KeepAliveService::class.java.name,
            shadowActivity.nextStoppedService.component?.className
        )
    }

    @Test
    fun `onResume refresh hides battery and auto-start banners after exemption is granted on Google`() {
        setManufacturer("google")
        setIgnoringBatteryOptimizations(false)

        val controller = launchActivity()
        val activity = controller.get()
        val fragment = activity.requireBluetoothFragment()

        assertTrue(fragment.screenAdapter().showBatteryBanner)
        assertTrue(fragment.screenAdapter().showAutoStartBanner)

        setIgnoringBatteryOptimizations(true)
        controller.pause().resume()
        idleUi(activity)

        assertFalse(fragment.screenAdapter().showBatteryBanner)
        assertFalse(fragment.screenAdapter().showAutoStartBanner)
    }

    @Test
    fun `onResume refresh hides auto start banner after Xiaomi grant changes`() {
        setManufacturer("xiaomi")
        setIgnoringBatteryOptimizations(true)
        // MIUI uses AppOps 10008 for auto-start permission.
        setAutoStartMode(AppOpsManager.MODE_IGNORED)

        val controller = launchActivity()
        val activity = controller.get()
        val fragment = activity.requireBluetoothFragment()

        assertFalse(fragment.screenAdapter().showBatteryBanner)
        assertTrue(fragment.screenAdapter().showAutoStartBanner)

        setAutoStartMode(AppOpsManager.MODE_ALLOWED)
        controller.pause().resume()
        idleUi(activity)

        assertFalse(fragment.screenAdapter().showAutoStartBanner)
    }

    @Test
    fun `onResume restarts floating bubble service when setting remains enabled`() = runBlocking {
        preferencesManager.setFloatingBubbleEnabled(true)

        val controller = launchActivity()
        val activity = controller.get()
        val fragment = activity.requireBluetoothFragment()
        val floatingBubbleController = mockk<FloatingBubbleController>(relaxed = true)
        every { floatingBubbleController.hasOverlayPermission() } returns true
        fragment.setFloatingBubbleController(floatingBubbleController)

        controller.pause().resume()
        idleUi(activity)

        verify(atLeast = 1) { floatingBubbleController.startService() }
    }

    private fun launchActivity(): ActivityController<MainActivity> {
        destroyActivity()
        activityController = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
        return activityController!!.also {
            idleUi(it.get())
        }
    }

    private fun destroyActivity() {
        val controller = activityController ?: return
        controller.get().finish()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        activityController = null
    }

    private fun idleUi(activity: MainActivity) {
        activity.supportFragmentManager.executePendingTransactions()
    }

    private fun MainActivity.requireBluetoothFragment(): BluetoothFragment {
        return supportFragmentManager.findFragmentById(R.id.fragment_container) as BluetoothFragment
    }

    private fun BluetoothFragment.screenAdapter(): BluetoothScreenAdapter {
        val field = BluetoothFragment::class.java.getDeclaredField("screenAdapter")
        field.isAccessible = true
        return field.get(this) as BluetoothScreenAdapter
    }

    private fun BluetoothFragment.syncKeepAliveService(enabled: Boolean) {
        val method = BluetoothFragment::class.java.getDeclaredMethod(
            "syncKeepAliveService",
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, enabled)
    }

    private fun BluetoothFragment.setFloatingBubbleController(controller: FloatingBubbleController) {
        val field = BluetoothFragment::class.java.getDeclaredField("floatingBubbleController")
        field.isAccessible = true
        field.set(this, controller)
    }

    private fun setIgnoringBatteryOptimizations(ignoring: Boolean) {
        val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIgnoringBatteryOptimizations(application.packageName, ignoring)
    }

    private fun setAutoStartMode(mode: Int) {
        val appOpsManager = application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        shadowOf(appOpsManager).setMode(XIAOMI_AUTO_START_OP, Process.myUid(), application.packageName, mode)
    }

    private fun setManufacturer(manufacturer: String) {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
    }

    private companion object {
        const val XIAOMI_AUTO_START_OP = 10008
    }
}
