package com.vibegravity.bluecruise.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibegravity.bluecruise.MainActivity
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.auth.LoginActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothFlowTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun shouldDisplayBluetoothScreen_whenMainActivityStarts() {
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))

        onView(withText("Cài đặt Bluetooth"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldDisplayRecyclerView_whenBluetoothScreenLoads() {
        onView(withId(R.id.rv_main))
            .check(matches(isDisplayed()))
    }

    @Test
    fun shouldDisplayAppBarLayout() {
        onView(withId(R.id.appBarLayout))
            .check(matches(isDisplayed()))
    }
}
