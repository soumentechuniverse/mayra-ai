package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.CodeBlockView
import com.example.ui.components.MarkdownContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeBlockCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking copy code button copies code snippet to system clipboard and triggers callback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()

        val sampleCode = """
            fun calculateFibonacci(n: Int): Long {
                if (n <= 1) return n.toLong()
                return calculateFibonacci(n - 1) + calculateFibonacci(n - 2)
            }
        """.trimIndent()

        var callbackTriggered = false

        composeTestRule.setContent {
            CodeBlockView(
                code = sampleCode,
                language = "kotlin",
                onCopied = { callbackTriggered = true }
            )
        }

        // Verify button exists with testTag and initial state
        composeTestRule.onNodeWithTag("copy_code_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()

        // Click copy button
        composeTestRule.onNodeWithTag("copy_code_button").performClick()
        composeTestRule.waitForIdle()

        // 1. Verify callback was invoked
        assertTrue("onCopied callback should be triggered", callbackTriggered)

        // 2. Verify clipboard primary clip contains exact code snippet
        val primaryClip = clipboard.primaryClip
        assertNotNull("Primary clip should not be null", primaryClip)
        assertTrue("Primary clip must have items", primaryClip!!.itemCount > 0)
        val copiedText = primaryClip.getItemAt(0).text.toString()
        assertEquals(sampleCode, copiedText)

        // 3. Verify UI updated label to "Copied!"
        composeTestRule.onNodeWithText("Copied!").assertIsDisplayed()
    }

    @Test
    fun `markdown rendered code blocks display copy button and copy to clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()

        val markdown = """
            Here is a Python script:
            ```python
            def fetch_data():
                print("Retrieving information...")
                return {"status": 200}
            ```
            Run the code above to test.
        """.trimIndent()

        var copied = false

        composeTestRule.setContent {
            MarkdownContent(
                content = markdown,
                onCodeCopied = { copied = true }
            )
        }

        composeTestRule.onNodeWithTag("copy_code_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()

        // Perform copy on the code block rendered inside Markdown
        composeTestRule.onNodeWithTag("copy_code_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(copied)
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        val text = clip!!.getItemAt(0).text.toString()
        assertTrue(text.contains("def fetch_data():"))
        assertTrue(text.contains("print(\"Retrieving information...\")"))
        composeTestRule.onNodeWithText("Copied!").assertIsDisplayed()
    }
}
