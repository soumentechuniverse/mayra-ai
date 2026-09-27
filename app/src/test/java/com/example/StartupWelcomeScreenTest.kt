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
@Config(sdk = [36])
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
    fun `welcome chat screen shows How can Mayra help you today and all 5 starter options`() {
        var clickedPrompt: String? = null

        composeTestRule.setContent {
            MayraAITheme(darkTheme = true) {
                EmptyChatView(
                    onSuggestionClicked = { clickedPrompt = it }
                )
            }
        }

        // Verify headline
        composeTestRule.onNodeWithText("How can Mayra help you today?").assertIsDisplayed()

        // Verify the 5 requested starter options are displayed
        composeTestRule.onNodeWithText("বাংলায় শুরু করুন").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start in English").assertIsDisplayed()
        composeTestRule.onNodeWithText("हिंदी में शुरू करें").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start a conversation").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ask anything").assertIsDisplayed()

        // Test clicking Bengali starter
        composeTestRule.onNodeWithText("বাংলায় শুরু করুন").performClick()
        assertEquals("নমস্কার Mayra! আমি বাংলায় কথা বলতে চাই।", clickedPrompt)

        // Test clicking English starter
        composeTestRule.onNodeWithText("Start in English").performClick()
        assertEquals("Hello Mayra! Let's get started in English.", clickedPrompt)

        // Test clicking Hindi starter
        composeTestRule.onNodeWithText("हिंदी में शुरू करें").performClick()
        assertEquals("नमस्ते Mayra! चलिए हिंदी में बातचीत शुरू करते हैं।", clickedPrompt)

        // Test clicking Start a conversation
        composeTestRule.onNodeWithText("Start a conversation").performClick()
        assertEquals("Hello Mayra, let's start a conversation.", clickedPrompt)

        // Test clicking Ask anything
        composeTestRule.onNodeWithText("Ask anything").performClick()
        assertEquals("What are some intriguing topics or questions we can explore today?", clickedPrompt)
    }
}
