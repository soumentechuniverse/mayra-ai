package com.example.data.service

import com.example.domain.model.SearchDecision
import com.example.domain.model.SearchMode
import com.example.domain.service.SearchIntentDetector
import java.util.Locale
import java.util.regex.Pattern

/**
 * Intelligent, multilingual search intent detector for Mayra AI.
 * 
 * Accurately discerns whether a user query requires real-time web search grounding
 * without unnecessarily browsing for static concepts, mathematics, or creative writing.
 * Supports English, Bengali, and Hindi queries seamlessly.
 */
class DefaultSearchIntentDetector : SearchIntentDetector {

    companion object {
        // Explicit search requests
        private val EXPLICIT_SEARCH_PATTERN = Pattern.compile(
            """(?i)\b(search\s+(the\s+)?(web|internet|online)|look\s*up\s+online|find\s+online|google\s+(this|it)|browse\s+(the\s+)?web)\b"""
        )

        private val BENGALI_EXPLICIT_SEARCH = listOf(
            "ওয়েবে খোঁজ", "ওয়েবে সার্চ", "অনলাইনে খোঁজ", "অনলাইনে সার্চ",
            "ইন্টারনেটে খোঁজ", "ইন্টারনেটে সার্চ", "নেট ঘেঁটে"
        )

        private val HINDI_EXPLICIT_SEARCH = listOf(
            "वेब पर खोजें", "इंटरनेट पर खोजें", "ऑनलाइन खोजें"
        )

        // Dynamic / Temporal indicators that mandate fresh web grounding
        private val TEMPORAL_DYNAMIC_PATTERNS = listOf(
            // English indicators
            """(?i)\b(latest|current|today|now|recent|breaking|this\s+week|this\s+month|this\s+year|yesterday|tonight|right\s+now|upcoming)\b""",
            """(?i)\b(weather|forecast|news|headline|headlines|price|stock|stocks|crypto|bitcoin|inflation|score|match\s+result|release\s+date|gold\s+price|petrol\s+price|exchange\s+rate|who\s+won)\b""",
            """(?i)\b(current\s+(price|version|status|president|prime\s+minister|ceo|leader|population|weather))\b""",
            """(?i)\b(latest\s+(version|news|update|developments|android|ios|iphone|movie|event))\b""",
            """(?i)\b(what\s+happened\s+(today|yesterday|this\s+week))\b""",
            """(?i)\b(find\s+information\s+about\s+(this\s+website|https?://))\b"""
        ).map { Pattern.compile(it) }

        private val BENGALI_DYNAMIC_KEYWORDS = listOf(
            "আজকের", "সর্বশেষ", "সাম্প্রতিক", "বর্তমান", "আজ", "তাজা খবর",
            "নতুন আপডেট", "আবহাওয়া", "দাম কত", "দর কত", "আজকে",
            "এই সপ্তাহের", "কারেন্ট", "লেটেস্ট", "স্কোর", "ফলাফল", "খবর"
        )

        private val HINDI_DYNAMIC_KEYWORDS = listOf(
            "ताज़ा", "लेटेस्ट", "आज का", "वर्तमान", "मौसम", "समाचार",
            "कीमत", "भाव", "स्कोर", "परिणाम"
        )

        // Non-search indicators: Pure calculations, creative writing, static translations, evergreen definitions
        private val PURE_CALCULATION_PATTERN = Pattern.compile(
            """^[\d\s\+\-\*\/\^\(\)\.=\%\,xX÷×]+$"""
        )

        private val CREATIVE_WRITING_PATTERN = Pattern.compile(
            """(?i)\b(write\s+(an?\s+)?(essay|poem|story|haiku|script|song|dialogue|letter)|compose\s+a\s+(poem|story|song)|create\s+a\s+(fictional|story))\b"""
        )

        private val BENGALI_CREATIVE_WRITING = listOf(
            "কবিতা লেখো", "গল্প লেখো", "চিঠি লেখো", "একটি রচনা লেখো", "গান লেখো"
        )

        private val TRANSLATION_PATTERN = Pattern.compile(
            """(?i)\b(translate\s+(this|the\s+following|text)?\s+to|translate\s+into)\b"""
        )

        private val BENGALI_TRANSLATION = listOf(
            "অনুবাদ করো", "বাংলায় অনুবাদ", "ইংরেজিতে অনুবাদ"
        )

        private val EVERGREEN_CONCEPTS_PATTERN = Pattern.compile(
            """(?i)\b(how\s+does\s+(photosynthesis|gravity|a\s+refrigerator|an\s+engine|wifi)\s+work|what\s+is\s+(gravity|photosynthesis|dna|relativity|a\s+binary\s+tree|quicksort|recursion))\b"""
        )
    }

    override suspend fun detect(
        query: String,
        mode: SearchMode,
        hasAttachments: Boolean
    ): SearchDecision {
        val trimmed = query.trim()

        // 1. Honor explicit user search mode overrides
        if (mode == SearchMode.DISABLED) {
            return SearchDecision(
                needsSearch = false,
                reason = "Web search is disabled in preferences."
            )
        }

        if (mode == SearchMode.ENABLED) {
            return SearchDecision(
                needsSearch = true,
                reason = "Web search is explicitly enabled in preferences."
            )
        }

        if (trimmed.isEmpty()) {
            return SearchDecision(needsSearch = false, reason = "Empty query.")
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 2. Explicit search requests (always triggers search even with attachments)
        val isExplicit = isExplicitSearch(trimmed, lower)
        if (isExplicit) {
            return SearchDecision(
                needsSearch = true,
                reason = "User explicitly requested a web search.",
                isExplicitRequest = true
            )
        }

        // 3. Privacy Preservation with attachments:
        // Do NOT automatically web-search attached files unless explicitly asked
        if (hasAttachments) {
            return SearchDecision(
                needsSearch = false,
                reason = "Attachments present without explicit search instruction; maintaining user privacy."
            )
        }

        // 4. Non-search heuristics (calculations, creative writing, translations, evergreen facts)
        if (isNonSearchQuery(trimmed, lower)) {
            // Verify there are no explicit temporal overrides (e.g. "write a poem about today's news")
            val hasTemporal = hasTemporalOrDynamicSignal(trimmed, lower)
            if (!hasTemporal) {
                return SearchDecision(
                    needsSearch = false,
                    reason = "Query is computational, creative, linguistic translation, or evergreen concept."
                )
            }
        }

        // 5. Dynamic and temporal signals
        if (hasTemporalOrDynamicSignal(trimmed, lower)) {
            return SearchDecision(
                needsSearch = true,
                reason = "Query references fresh, current, or volatile real-world information."
            )
        }

        // 6. Default to standard knowledge / reasoning
        return SearchDecision(
            needsSearch = false,
            reason = "General conversation or static conceptual request."
        )
    }

    private fun isExplicitSearch(raw: String, lower: String): Boolean {
        if (EXPLICIT_SEARCH_PATTERN.matcher(lower).find()) return true
        if (BENGALI_EXPLICIT_SEARCH.any { raw.contains(it) }) return true
        if (HINDI_EXPLICIT_SEARCH.any { raw.contains(it) }) return true
        return false
    }

    private fun isNonSearchQuery(raw: String, lower: String): Boolean {
        // Pure arithmetic or calculation
        if (PURE_CALCULATION_PATTERN.matcher(raw.replace(" ", "")).matches()) return true
        if (lower.startsWith("what is ") && raw.length < 25 && raw.matches(Regex("""(?i)^what\s+is\s+[\d\s\+\-\*\/\^\(\)\.=\%\,xX÷×\?]+$"""))) return true

        // Creative writing
        if (CREATIVE_WRITING_PATTERN.matcher(lower).find()) return true
        if (BENGALI_CREATIVE_WRITING.any { raw.contains(it) }) return true

        // Translation
        if (TRANSLATION_PATTERN.matcher(lower).find()) return true
        if (BENGALI_TRANSLATION.any { raw.contains(it) }) return true

        // Evergreen science and classic programming
        if (EVERGREEN_CONCEPTS_PATTERN.matcher(lower).find()) return true

        return false
    }

    private fun hasTemporalOrDynamicSignal(raw: String, lower: String): Boolean {
        // Check regex patterns
        for (pattern in TEMPORAL_DYNAMIC_PATTERNS) {
            if (pattern.matcher(lower).find()) return true
        }

        // Check Bengali dynamic signals
        for (keyword in BENGALI_DYNAMIC_KEYWORDS) {
            if (raw.contains(keyword)) return true
        }

        // Check Hindi dynamic signals
        for (keyword in HINDI_DYNAMIC_KEYWORDS) {
            if (raw.contains(keyword)) return true
        }

        return false
    }
}
