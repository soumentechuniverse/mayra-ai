package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.AppUpdatePreferences
import com.example.data.service.AppUpdateInfo
import com.example.data.service.AppUpdateService
import com.example.data.service.DefaultSearchIntentDetector
import com.example.domain.model.AiModelConfig
import com.example.domain.model.SearchMode
import com.example.ui.screens.SettingsSheet
import com.example.ui.theme.MayraAITheme
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateAndImageRetrievalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val detector = DefaultSearchIntentDetector()

    @Test
    fun `detects image requests in English, Bengali, and Hindi accurately`() {
        assertTrue(detector.isImageSearch("show me a picture of the Eiffel tower"))
        assertTrue(detector.isImageSearch("Find photos of Bengal tiger"))
        assertTrue(detector.isImageSearch("images of James Webb telescope"))
        assertTrue(detector.isImageSearch("আমাকে সুন্দরবনের বাঘের ছবি দেখাও"))
        assertTrue(detector.isImageSearch("ताजमहल की तस्वीर दिखाओ"))

        // Extracts subject accurately
        val subject = detector.extractImageSubject("show me a photo of Red Panda")
        assertEquals("Red Panda", subject)

        val subjectBn = detector.extractImageSubject("সুন্দরবন এর ছবি দেখাও")
        assertEquals("সুন্দরবন", subjectBn)

        // Non-image queries
        assertFalse(detector.isImageSearch("Write a poem about the sea"))
        assertFalse(detector.isImageSearch("2 + 2 = ?"))
        assertFalse(detector.isImageSearch("What is the capital of France?"))
    }

    @Test
    fun `app update service parses release json correctly and identifies updates`() = runBlocking {
        val mockJson = """
            {
              "tag_name": "v2.0.0",
              "name": "Mayra AI 2.0 Release",
              "body": "Major UI enhancements and web grounding features.",
              "published_at": "2026-09-28T12:00:00Z",
              "assets": [
                {
                  "name": "MayraAI-v2.0.0.apk",
                  "browser_download_url": "https://github.com/soumen-tech/mayra-ai/releases/download/v2.0.0/MayraAI-v2.0.0.apk"
                }
              ]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = AppUpdateService(mockClient)
        val result = service.checkForUpdate("https://api.github.com/repos/soumen-tech/mayra-ai/releases/latest")

        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertEquals("2.0.0", info?.latestVersion)
        assertEquals("https://github.com/soumen-tech/mayra-ai/releases/download/v2.0.0/MayraAI-v2.0.0.apk", info?.downloadUrl)
        assertTrue(info?.isUpdateAvailable == true)
        assertEquals("Major UI enhancements and web grounding features.", info?.releaseNotes)
    }

    @Test
    fun `app update preferences allows custom release source URL configuration and reset`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = AppUpdatePreferences(context)

        assertEquals(AppUpdatePreferences.DEFAULT_UPDATE_URL, prefs.getUpdateUrl())

        val customUrl = "https://my-server.com/mayra/version.json"
        prefs.setUpdateUrl(customUrl)
        assertEquals(customUrl, prefs.getUpdateUrl())

        prefs.resetToDefault()
        assertEquals(AppUpdatePreferences.DEFAULT_UPDATE_URL, prefs.getUpdateUrl())
    }

    @Test
    fun `settings sheet displays App Update section with check button and version information`() {
        var checkClicked = false
        var toggleConfigClicked = false

        composeTestRule.setContent {
            MayraAITheme(darkTheme = true) {
                SettingsSheet(
                    isOpen = true,
                    selectedModel = AiModelConfig.AvailableModels.first(),
                    availableModels = AiModelConfig.AvailableModels,
                    isDarkTheme = true,
                    onModelSelected = {},
                    onTemperatureChanged = {},
                    onToggleTheme = {},
                    onClearChat = {},
                    onDismiss = {},
                    onCheckForUpdate = { checkClicked = true },
                    onToggleUpdateSourceConfig = { toggleConfigClicked = true },
                    updateStatusMessage = "Mayra AI is up to date (v1.0).",
                    updateInfo = AppUpdateInfo(
                        currentVersion = "1.0",
                        latestVersion = "1.0",
                        isUpdateAvailable = false,
                        releaseNotes = "No new updates.",
                        downloadUrl = ""
                    )
                )
            }
        }

        // Verify App Update section is displayed
        composeTestRule.onNodeWithTag("app_update_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_version_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("check_for_updates_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_status_message").performScrollTo().assertIsDisplayed()

        // Verify click on check for updates
        composeTestRule.onNodeWithTag("check_for_updates_button").performClick()
        assertTrue(checkClicked)

        // Verify configure release source toggle
        composeTestRule.onNodeWithTag("configure_update_source_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("configure_update_source_button").performClick()
        assertTrue(toggleConfigClicked)
    }
}
