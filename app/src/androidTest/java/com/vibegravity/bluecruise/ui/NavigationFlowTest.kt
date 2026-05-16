package com.vibegravity.bluecruise.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.auth.LoginActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @Test
    fun shouldDisplayMainActivity_whenLaunched() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.fragment_container))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldDisplayLoginActivity_whenLaunched() {
        val scenario = ActivityScenario.launch(LoginActivity::class.java)

        onView(withId(R.id.loginButton))
            .check(matches(isDisplayed()))
    }
}
