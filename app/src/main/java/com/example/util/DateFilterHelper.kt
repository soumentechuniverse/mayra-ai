package com.example.util

import com.example.domain.model.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryDateFilter {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

object DateFilterHelper {

    fun matchesDate(
        timestamp: Long,
        filter: HistoryDateFilter,
        customDateEpoch: Long? = null,
        referenceTime: Long = System.currentTimeMillis()
    ): Boolean {
        if (filter == HistoryDateFilter.ALL) return true

        val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val refCal = Calendar.getInstance().apply { timeInMillis = referenceTime }

        return when (filter) {
            HistoryDateFilter.ALL -> true
            HistoryDateFilter.TODAY -> isSameDay(itemCal, refCal)
            HistoryDateFilter.YESTERDAY -> {
                refCal.add(Calendar.DAY_OF_YEAR, -1)
                isSameDay(itemCal, refCal)
            }
            HistoryDateFilter.THIS_WEEK -> {
                val sevenDaysAgo = Calendar.getInstance().apply {
                    timeInMillis = referenceTime
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                timestamp >= sevenDaysAgo.timeInMillis
            }
            HistoryDateFilter.THIS_MONTH -> {
                (itemCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) &&
                    itemCal.get(Calendar.MONTH) == refCal.get(Calendar.MONTH)) ||
                    (referenceTime - timestamp <= 30L * 24 * 60 * 60 * 1000)
            }
            HistoryDateFilter.CUSTOM -> {
                if (customDateEpoch == null) true
                else {
                    val customCal = Calendar.getInstance().apply { timeInMillis = customDateEpoch }
                    isSameDay(itemCal, customCal)
                }
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun matchesQueryOrDate(
        conversation: Conversation,
        query: String
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true

        // 1. Keyword match on Title
        if (conversation.title.contains(trimmed, ignoreCase = true)) return true

        // 2. Keyword match on Preview
        if (conversation.preview.contains(trimmed, ignoreCase = true)) return true

        // 3. Formatted Date text matches
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()),
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()),
            SimpleDateFormat("MMM d", Locale.getDefault()),
            SimpleDateFormat("MMMM d", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy", Locale.getDefault()),
            SimpleDateFormat("MMMM", Locale.getDefault()),
            SimpleDateFormat("MMM", Locale.getDefault()),
            SimpleDateFormat("EEEE", Locale.getDefault())
        )

        val updatedAtDate = Date(conversation.updatedAt)
        for (format in dateFormats) {
            val formatted = format.format(updatedAtDate)
            if (formatted.contains(trimmed, ignoreCase = true)) return true
        }

        // 4. Relative keywords ("today", "yesterday")
        val now = System.currentTimeMillis()
        if (trimmed.equals("today", ignoreCase = true) && matchesDate(conversation.updatedAt, HistoryDateFilter.TODAY, referenceTime = now)) return true
        if (trimmed.equals("yesterday", ignoreCase = true) && matchesDate(conversation.updatedAt, HistoryDateFilter.YESTERDAY, referenceTime = now)) return true

        return false
    }

    fun formatConversationDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val item = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            isSameDay(now, item) -> "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            run {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                isSameDay(yesterday, item)
            } -> "Yesterday, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            now.get(Calendar.YEAR) == item.get(Calendar.YEAR) -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatCustomDateHeader(epochMillis: Long): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
    }
}
