package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Walkie Talkie Pro", appName)
    }

    @Test
    fun dedicatedRadarButtonNavigatesToRadarDashboardAndBack() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("radar_button").assertIsDisplayed()

        composeRule.onNodeWithTag("radar_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("radar_scope_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_back_button").assertIsDisplayed()

        composeRule.onNodeWithTag("radar_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("radar_button").assertIsDisplayed()
    }
}
