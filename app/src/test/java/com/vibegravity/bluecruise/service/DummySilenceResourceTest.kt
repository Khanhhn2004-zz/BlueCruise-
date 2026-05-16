package com.vibegravity.bluecruise.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DummySilenceResourceTest {

    @Test
    fun `dummy silence resource is a valid wav header`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.resources.openRawResource(R.raw.dummy_silence).use { stream ->
            val header = ByteArray(12)
            val read = stream.read(header)
            assertEquals(12, read)

            val riff = String(header.copyOfRange(0, 4), Charsets.US_ASCII)
            val wave = String(header.copyOfRange(8, 12), Charsets.US_ASCII)
            assertEquals("RIFF", riff)
            assertEquals("WAVE", wave)

            val totalBytes = 12 + stream.readBytes().size
            assertTrue(totalBytes >= 44)
        }
    }
}
