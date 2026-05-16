package com.vibegravity.bluecruise.auth

import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoginActivityTest {

    @Test
    fun `login button reflects loading state`() {
        val activity = launchActivity()

        activity.renderForTest(
            LoginUiState(
                isLoading = true,
                phone = "0123456789",
                password = "secret"
            )
        )

        val loginButton = activity.findViewById<Button>(R.id.loginButton)

        assertFalse(loginButton.isEnabled)
        assertEquals(activity.getString(R.string.logging_in), loginButton.text.toString())
    }

    @Test
    fun `clicking login with missing password shows password validation`() {
        val activity = launchActivity()
        val phoneEditText = activity.findViewById<TextInputEditText>(R.id.phoneEditText)
        val passwordLayout = activity.findViewById<TextInputLayout>(R.id.passwordInputLayout)

        phoneEditText.setText("0123456789")
        activity.findViewById<Button>(R.id.loginButton).performClick()
        shadowOf(activity.mainLooper).idle()

        assertEquals("Password is required", passwordLayout.error)
    }

    @Test
    fun `successful state opens MainActivity and finishes LoginActivity`() {
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        val activity = controller.get()

        activity.renderForTest(LoginUiState(navigateToMain = true))

        val nextIntent = shadowOf(activity).nextStartedActivity

        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `secondary login copy does not use the low contrast gray token`() {
        val activity = launchActivity()
        val textGray = activity.getColor(R.color.text_gray)
        val subtitleText = activity.findViewById<TextView>(R.id.loginSubtitleText)
        val rememberMeCheckBox = activity.findViewById<CheckBox>(R.id.rememberMeCheckBox)
        val phoneInputLayout = activity.findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val passwordInputLayout = activity.findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val phoneEditText = activity.findViewById<TextInputEditText>(R.id.phoneEditText)
        val passwordEditText = activity.findViewById<TextInputEditText>(R.id.passwordEditText)

        assertNotEquals(textGray, subtitleText.currentTextColor)
        assertNotEquals(textGray, rememberMeCheckBox.currentTextColor)
        assertNotEquals(textGray, phoneInputLayout.defaultHintTextColor?.defaultColor ?: -1)
        assertNotEquals(textGray, passwordInputLayout.defaultHintTextColor?.defaultColor ?: -1)
        assertNotEquals(textGray, phoneEditText.currentHintTextColor)
        assertNotEquals(textGray, passwordEditText.currentHintTextColor)
    }

    private fun launchActivity(): LoginActivity {
        return Robolectric.buildActivity(LoginActivity::class.java)
            .setup()
            .get()
    }

    private fun LoginActivity.renderForTest(state: LoginUiState) {
        val renderMethod = LoginActivity::class.java.getDeclaredMethod("render", LoginUiState::class.java)
        renderMethod.isAccessible = true
        renderMethod.invoke(this, state)
    }
}
