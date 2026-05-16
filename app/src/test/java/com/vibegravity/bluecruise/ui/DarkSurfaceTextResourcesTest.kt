package com.vibegravity.bluecruise.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DarkSurfaceTextResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_BlueCruise)
    private val textGray = context.getColor(R.color.text_gray)

    @Test
    fun `target car descriptions use full opacity readable text`() {
        val root = inflate(R.layout.item_target_car)
        val descriptionIds = listOf(
            R.id.tvAutoPlayDesc,
            R.id.tvAutoPlayOnAndroidAutoDesc,
            R.id.tvAftermarketAndroidAutoTargetDesc,
            R.id.tvConnectionStartDelayDesc,
            R.id.tvKeepAliveDesc,
            R.id.tvFloatingBubbleDesc,
            R.id.tvTierDescription
        )

        descriptionIds.forEach { viewId ->
            val textView = root.findViewById<TextView>(viewId)
            assertEquals(1f, textView.alpha, 0.001f)
            assertNotEquals(textGray, textView.currentTextColor)
        }
    }

    @Test
    fun `secondary copy on dark surfaces does not use the low contrast gray token`() {
        val appSettings = inflate(R.layout.item_app_settings)
        val autoStartBanner = inflate(R.layout.item_auto_start_banner)
        val batteryBanner = inflate(R.layout.item_battery_banner)
        val bluetoothDevice = inflate(R.layout.item_bluetooth_device)
        val emptyState = inflate(R.layout.item_empty_state)
        val sectionTitle = inflate(R.layout.item_section_title) as TextView

        val settingsDescription = appSettings.findViewById<TextView>(R.id.tv_setting_description)

        assertEquals(1f, settingsDescription.alpha, 0.001f)
        assertNotEquals(textGray, settingsDescription.currentTextColor)
        assertNotEquals(
            textGray,
            autoStartBanner.findViewById<TextView>(R.id.tv_autostart_desc).currentTextColor
        )
        assertNotEquals(
            textGray,
            batteryBanner.findViewById<TextView>(R.id.tv_battery_desc).currentTextColor
        )
        assertNotEquals(
            textGray,
            bluetoothDevice.findViewById<TextView>(R.id.tv_device_mac).currentTextColor
        )
        assertNotEquals(
            textGray,
            emptyState.findViewById<TextView>(R.id.tv_empty_desc).currentTextColor
        )
        assertNotEquals(textGray, sectionTitle.currentTextColor)
    }

    private fun inflate(layoutResId: Int): View {
        val parent = FrameLayout(themedContext)
        return LayoutInflater.from(themedContext).inflate(layoutResId, parent, false)
    }
}
