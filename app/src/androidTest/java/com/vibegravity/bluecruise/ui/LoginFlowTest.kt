package com.vibegravity.bluecruise.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.auth.LoginActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginFlowTest {

    private lateinit var scenario: ActivityScenario<LoginActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(LoginActivity::class.java)
    }

    @Test
    fun shouldDisplayLoginScreen_whenActivityStarts() {
        onView(withId(R.id.loginTitleText))
            .check(matches(withText(R.string.login_title)))
        onView(withId(R.id.loginSubtitleText))
            .check(matches(withText(R.string.login_subtitle)))
        onView(withId(R.id.loginButton))
            .check(matches(isDisplayed()))
        onView(withId(R.id.phoneEditText))
            .check(matches(isDisplayed()))
        onView(withId(R.id.passwordEditText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldShowPhoneError_whenEmptyPhoneSubmitted() {
        onView(withId(R.id.loginButton))
            .perform(click())

        onView(withId(R.id.phoneInputLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldCheckRememberMeCheckbox() {
        onView(withId(R.id.rememberMeCheckBox))
            .perform(click())
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldDisplayForgotPasswordText() {
        onView(withId(R.id.forgotPasswordText))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.forgot_password)))
    }

    @Test
    fun shouldDisplaySignUpPrompt() {
        onView(withId(R.id.signUpHereText))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.sign_up_here)))
    }

    @Test
    fun shouldDisplayLoginButtonText() {
        onView(withId(R.id.loginButton))
            .check(matches(withText(R.string.login_button)))
    }
}
