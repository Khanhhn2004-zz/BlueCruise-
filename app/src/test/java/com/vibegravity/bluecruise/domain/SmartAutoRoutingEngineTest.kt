package com.vibegravity.bluecruise.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SmartAutoRoutingEngineTest {

    private lateinit var context: Application
    private lateinit var engine: SmartAutoRoutingEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = SmartAutoRoutingEngine(context)
    }

    @Test
    fun `isAndroidAutoConnected returns false when not in car mode`() {
        // Default should be false (not in car mode)
        assertFalse(engine.isAndroidAutoConnected())
    }
}
