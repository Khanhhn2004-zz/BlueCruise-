package com.vibegravity.bluecruise.service

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FloatingBubbleColorsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `bubble palette uses fresh pastel aqua and red tones`() {
        assertEquals(0xFF56CCC0.toInt(), ContextCompat.getColor(context, R.color.bubble_greeting_idle))
        assertEquals(0xFF6FE3D6.toInt(), ContextCompat.getColor(context, R.color.bubble_greeting_active))
        assertEquals(0xFFF28B82.toInt(), ContextCompat.getColor(context, R.color.bubble_goodbye_idle))
        assertEquals(0xFFFF9E95.toInt(), ContextCompat.getColor(context, R.color.bubble_goodbye_active))
    }
}
