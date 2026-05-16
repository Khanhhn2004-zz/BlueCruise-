package com.vibegravity.bluecruise.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.vibegravity.bluecruise.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLocaleResourcesTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `english locale resolves english strings`() {
        val englishContext = contextFor(Locale.ENGLISH)

        assertEquals("Sign In", englishContext.getString(R.string.login_title))
    }

    @Test
    fun `non english locale falls back to vietnamese strings`() {
        val frenchContext = contextFor(Locale.FRENCH)

        assertEquals("Đăng nhập", frenchContext.getString(R.string.login_title))
    }

    private fun contextFor(locale: Locale): Context {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return appContext.createConfigurationContext(configuration)
    }
}
