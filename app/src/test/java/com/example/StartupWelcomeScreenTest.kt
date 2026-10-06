package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.EmptyChatView
import com.example.ui.screens.StartupScreen
import com.example.ui.theme.MayraAITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupWelcomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `startup screen displays Mayra AI, author Soumen Mondal, and can trigger finish`() {
        var finished = false

        composeTestRule.setContent {
            MayraAITheme(darkTheme = true) {
                StartupScreen(
                    onStartupFinished = { finished = true },
                    durationMillis = 10_000L
                )
            }
        }

        // Verify branding and creator attribution text
        composeTestRule.onNodeWithText("Mayra AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Created & Published by").assertIsDisplayed()
        composeTestRule.onNodeWithText("Soumen Mondal").assertIsDisplayed()

        // Verify tap skips/completes startup
        composeTestRule.onNodeWithTag("startup_screen").performClick()
        assertTrue("Expected startup finish callback to be invoked", finished)
    }

    @Test
    fun `welcome chat screen shows clean home with logo heading and subtitle without manual language buttons`() {
        composeTestRule.setContent {
            MayraAITheme(darkTheme = true) {
                EmptyChatView()
            }
        }

        // Verify Mayra logo emblem is displayed
        composeTestRule.onNodeWithTag("mayra_logo").assertIsDisplayed()

        // Verify clean headline
        composeTestRule.onNodeWithText("How can Mayra help you today?").assertIsDisplayed()

        // Verify clean subtitle
        composeTestRule.onNodeWithText("Ask anything, explore ideas, or search the web in any language.").assertIsDisplayed()

        // Verify that manual language buttons (বাংলা, English, हिंदी) are removed as required
        composeTestRule.onNodeWithText("বাংলা").assertDoesNotExist()
        composeTestRule.onNodeWithText("English").assertDoesNotExist()
        composeTestRule.onNodeWithText("हिंदी").assertDoesNotExist()
    }
}
