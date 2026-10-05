package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.SearchPhase
import com.example.ui.components.ThinkingIndicator
import com.example.ui.screens.ChatScreen
import com.example.ui.viewmodel.ChatUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThinkingTypingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ThinkingIndicator displays Mayra is thinking text and typing dots in idle or generating phase`() {
        composeTestRule.setContent {
            ThinkingIndicator(searchPhase = SearchPhase.IDLE)
        }

        composeTestRule.onNodeWithTag("thinking_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("typing_indicator_dots").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mayra is thinking...").assertIsDisplayed()
    }

    @Test
    fun `ThinkingIndicator displays web search phases when searching`() {
        composeTestRule.setContent {
            ThinkingIndicator(searchPhase = SearchPhase.SEARCHING)
        }

        composeTestRule.onNodeWithTag("thinking_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Searching the web...").assertIsDisplayed()
    }

    @Test
    fun `ThinkingIndicator displays reading sources phase`() {
        composeTestRule.setContent {
            ThinkingIndicator(searchPhase = SearchPhase.READING_SOURCES)
        }

        composeTestRule.onNodeWithTag("thinking_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reading sources...").assertIsDisplayed()
    }

    @Test
    fun `ChatScreen displays Mayra is thinking indicator when generating and waiting for assistant response`() {
        val userMessage = ChatMessage(
            id = "msg-user-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Explain quantum computing simply"
        )

        val state = ChatUiState(
            messages = listOf(userMessage),
            isGenerating = true,
            searchPhase = SearchPhase.IDLE
        )

        composeTestRule.setContent {
            ChatScreen(
                state = state,
                onEvent = {}
            )
        }

        // Verify the animated typing indicator is shown in the chat stream
        composeTestRule.onNodeWithTag("thinking_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("typing_indicator_dots").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mayra is thinking...").assertIsDisplayed()
    }

    @Test
    fun `ChatScreen does not display thinking indicator when assistant content has arrived`() {
        val userMessage = ChatMessage(
            id = "msg-user-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Explain quantum computing simply"
        )
        val assistantMessage = ChatMessage(
            id = "msg-ai-1",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "Quantum computing uses qubits..."
        )

        val state = ChatUiState(
            messages = listOf(userMessage, assistantMessage),
            isGenerating = false,
            searchPhase = SearchPhase.IDLE
        )

        composeTestRule.setContent {
            ChatScreen(
                state = state,
                onEvent = {}
            )
        }

        // Verify thinking indicator is not present
        composeTestRule.onNodeWithTag("thinking_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mayra is thinking...").assertDoesNotExist()
    }
}
