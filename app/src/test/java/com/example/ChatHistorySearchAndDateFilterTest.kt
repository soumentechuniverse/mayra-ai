package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.repository.ChatRepository
import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import com.example.domain.model.MessageRole
import com.example.domain.model.SearchPhase
import com.example.ui.screens.HistoryDrawer
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.DateFilterHelper
import com.example.util.HistoryDateFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatHistorySearchAndDateFilterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeHistoryChatRepository
    private lateinit var viewModel: ChatViewModel

    private val now = System.currentTimeMillis()
    private val oneDayAgo = now - 24L * 60 * 60 * 1000
    private val fiveDaysAgo = now - 5L * 24 * 60 * 60 * 1000
    private val fortyDaysAgo = now - 40L * 24 * 60 * 60 * 1000

    private val convToday = Conversation(
        id = "conv-today",
        title = "Kotlin Coroutines Discussion",
        preview = "Discussing suspend functions and StateFlow",
        updatedAt = now
    )

    private val convYesterday = Conversation(
        id = "conv-yesterday",
        title = "Python Data Analysis",
        preview = "Pandas dataframe cleaning and visualization",
        updatedAt = oneDayAgo
    )

    private val convThisWeek = Conversation(
        id = "conv-week",
        title = "Jetpack Compose Architecture",
        preview = "Building responsive Material 3 layouts",
        updatedAt = fiveDaysAgo
    )

    private val convOld = Conversation(
        id = "conv-old",
        title = "Ancient History Project",
        preview = "Roman architecture and engineering",
        updatedAt = fortyDaysAgo
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeHistoryChatRepository(
            listOf(convToday, convYesterday, convThisWeek, convOld)
        )
        viewModel = ChatViewModel(repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `DateFilterHelper correctly matches today yesterday this week and custom date`() {
        assertTrue(DateFilterHelper.matchesDate(now, HistoryDateFilter.ALL, referenceTime = now))
        assertTrue(DateFilterHelper.matchesDate(now, HistoryDateFilter.TODAY, referenceTime = now))
        assertFalse(DateFilterHelper.matchesDate(oneDayAgo, HistoryDateFilter.TODAY, referenceTime = now))

        assertTrue(DateFilterHelper.matchesDate(oneDayAgo, HistoryDateFilter.YESTERDAY, referenceTime = now))
        assertFalse(DateFilterHelper.matchesDate(now, HistoryDateFilter.YESTERDAY, referenceTime = now))

        assertTrue(DateFilterHelper.matchesDate(now, HistoryDateFilter.THIS_WEEK, referenceTime = now))
        assertTrue(DateFilterHelper.matchesDate(fiveDaysAgo, HistoryDateFilter.THIS_WEEK, referenceTime = now))
        assertFalse(DateFilterHelper.matchesDate(fortyDaysAgo, HistoryDateFilter.THIS_WEEK, referenceTime = now))

        // Custom date matching
        val customCal = Calendar.getInstance().apply { timeInMillis = fiveDaysAgo }
        assertTrue(DateFilterHelper.matchesDate(fiveDaysAgo, HistoryDateFilter.CUSTOM, customCal.timeInMillis, referenceTime = now))
        assertFalse(DateFilterHelper.matchesDate(now, HistoryDateFilter.CUSTOM, customCal.timeInMillis, referenceTime = now))
    }

    @Test
    fun `DateFilterHelper matches search query by keyword and by date string`() {
        // Keyword match
        assertTrue(DateFilterHelper.matchesQueryOrDate(convToday, "Kotlin"))
        assertTrue(DateFilterHelper.matchesQueryOrDate(convToday, "suspend functions"))
        assertFalse(DateFilterHelper.matchesQueryOrDate(convToday, "Rust"))

        // Date keyword match ("today", "yesterday")
        assertTrue(DateFilterHelper.matchesQueryOrDate(convToday, "today"))
        assertTrue(DateFilterHelper.matchesQueryOrDate(convYesterday, "yesterday"))

        // Year match
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentYear = cal.get(Calendar.YEAR).toString()
        assertTrue(DateFilterHelper.matchesQueryOrDate(convToday, currentYear))
    }

    @Test
    fun `filtering by keyword updates filteredConversations in ViewModel`() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.filteredConversations.size)

        // Filter by keyword "Python"
        viewModel.onEvent(ChatUiEvent.HistorySearchQueryChanged("Python"))
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.filteredConversations
        assertEquals(1, filtered.size)
        assertEquals("conv-yesterday", filtered[0].id)

        // Filter by preview keyword "Material 3"
        viewModel.onEvent(ChatUiEvent.HistorySearchQueryChanged("Material 3"))
        advanceUntilIdle()

        val filteredPreview = viewModel.uiState.value.filteredConversations
        assertEquals(1, filteredPreview.size)
        assertEquals("conv-week", filteredPreview[0].id)

        // Clear query
        viewModel.onEvent(ChatUiEvent.HistorySearchQueryChanged(""))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.filteredConversations.size)
    }

    @Test
    fun `filtering by date preset updates filteredConversations in ViewModel`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Filter for TODAY
        viewModel.onEvent(ChatUiEvent.HistoryDateFilterChanged(HistoryDateFilter.TODAY))
        advanceUntilIdle()

        val todayFiltered = viewModel.uiState.value.filteredConversations
        assertEquals(1, todayFiltered.size)
        assertEquals("conv-today", todayFiltered[0].id)

        // Filter for YESTERDAY
        viewModel.onEvent(ChatUiEvent.HistoryDateFilterChanged(HistoryDateFilter.YESTERDAY))
        advanceUntilIdle()

        val yesterdayFiltered = viewModel.uiState.value.filteredConversations
        assertEquals(1, yesterdayFiltered.size)
        assertEquals("conv-yesterday", yesterdayFiltered[0].id)

        // Filter for THIS_WEEK (should include today, yesterday, and 5 days ago)
        viewModel.onEvent(ChatUiEvent.HistoryDateFilterChanged(HistoryDateFilter.THIS_WEEK))
        advanceUntilIdle()

        val weekFiltered = viewModel.uiState.value.filteredConversations
        assertEquals(3, weekFiltered.size)
        assertTrue(weekFiltered.any { it.id == "conv-today" })
        assertTrue(weekFiltered.any { it.id == "conv-yesterday" })
        assertTrue(weekFiltered.any { it.id == "conv-week" })
        assertFalse(weekFiltered.any { it.id == "conv-old" })
    }

    @Test
    fun `combined keyword and date filtering narrows results`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Filter by THIS_WEEK AND query "Kotlin"
        viewModel.onEvent(ChatUiEvent.HistoryDateFilterChanged(HistoryDateFilter.THIS_WEEK))
        viewModel.onEvent(ChatUiEvent.HistorySearchQueryChanged("Kotlin"))
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.filteredConversations
        assertEquals(1, filtered.size)
        assertEquals("conv-today", filtered[0].id)

        // Clear filters
        viewModel.onEvent(ChatUiEvent.ClearHistoryFilters)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.filteredConversations.size)
        assertEquals("", viewModel.uiState.value.historySearchQuery)
        assertEquals(HistoryDateFilter.ALL, viewModel.uiState.value.historyDateFilter)
    }

    @Test
    fun `HistoryDrawer UI displays search input and date filter chips`() {
        var searchedQuery = ""
        var selectedFilter: HistoryDateFilter = HistoryDateFilter.ALL

        composeTestRule.setContent {
            HistoryDrawer(
                isOpen = true,
                activeConversationId = "conv-today",
                conversations = listOf(convToday, convYesterday),
                onSelectConversation = {},
                onDeleteConversation = {},
                onNewChat = {},
                onDismiss = {},
                searchQuery = searchedQuery,
                onSearchQueryChanged = { searchedQuery = it },
                dateFilter = selectedFilter,
                onDateFilterChanged = { f, _ -> selectedFilter = f }
            )
        }

        // Verify Search input and Date filter chips are displayed
        composeTestRule.onNodeWithTag("history_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_open_datepicker_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_filter_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_filter_today").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_filter_yesterday").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_filter_week").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_filter_month").assertIsDisplayed()

        // Test typing into search input
        composeTestRule.onNodeWithTag("history_search_input").performTextInput("Coroutines")
        assertEquals("Coroutines", searchedQuery)

        // Test clicking Date filter chip
        composeTestRule.onNodeWithTag("date_filter_today").performClick()
        assertEquals(HistoryDateFilter.TODAY, selectedFilter)
    }

    private class FakeHistoryChatRepository(
        initialList: List<Conversation>
    ) : ChatRepository {
        private val _conversations = MutableStateFlow(initialList)
        override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

        private val _activeConversation = MutableStateFlow<Conversation?>(initialList.firstOrNull())
        override val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
        override val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

        private val _isMemoryEnabled = MutableStateFlow(true)
        override val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _searchPhase = MutableStateFlow(SearchPhase.IDLE)
        override val searchPhase: StateFlow<SearchPhase> = _searchPhase.asStateFlow()

        override suspend fun startNewConversation(title: String): Conversation {
            val conv = Conversation(id = UUID.randomUUID().toString(), title = title)
            _activeConversation.value = conv
            _conversations.value = listOf(conv) + _conversations.value
            return conv
        }

        override suspend fun selectConversation(conversationId: String) {
            val conv = _conversations.value.find { it.id == conversationId }
            _activeConversation.value = conv
        }

        override suspend fun renameConversation(conversationId: String, newTitle: String) {}
        override suspend fun togglePinConversation(conversationId: String) {}
        override suspend fun toggleArchiveConversation(conversationId: String) {}

        override suspend fun sendMessage(
            content: String,
            config: AiModelConfig,
            attachments: List<Attachment>
        ): Result<ChatMessage> {
            val msg = ChatMessage(
                conversationId = _activeConversation.value?.id ?: "conv-1",
                role = MessageRole.USER,
                content = content
            )
            return Result.success(msg)
        }

        override suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage> {
            return Result.success(ChatMessage(conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "retried"))
        }

        override suspend fun clearMessages() {
            _messages.value = emptyList()
        }

        override suspend fun deleteConversation(conversationId: String) {}
        override suspend fun cancelGeneration() {
            _isGenerating.value = false
        }

        override fun setMemoryEnabled(enabled: Boolean) {
            _isMemoryEnabled.value = enabled
        }

        override suspend fun saveMemory(content: String, category: MemoryCategory): MemoryItem {
            val mem = MemoryItem(id = UUID.randomUUID().toString(), content = content, category = category)
            _memories.value = _memories.value + mem
            return mem
        }

        override suspend fun toggleMemoryItemEnabled(memoryId: String, enabled: Boolean) {}
        override suspend fun deleteMemory(memoryId: String) {}
        override suspend fun clearAllMemories() {
            _memories.value = emptyList()
        }

        override suspend fun searchConversations(query: String): List<Conversation> = emptyList()
    }
}
