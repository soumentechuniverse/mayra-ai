package com.example.domain.service

import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import java.util.Locale
import java.util.UUID

object MemoryDetector {

    private val REMEMBER_PATTERNS = listOf(
        Regex("^(?:please\\s+)?remember\\s+(?:that\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:please\\s+)?remember\\s*:\\s*(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:please\\s+)?remember\\s+to\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:please\\s+)?keep\\s+in\\s+mind\\s+(?:that\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:please\\s+)?make\\s+a\\s+note\\s+(?:that\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:please\\s+)?note\\s+that\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:save\\s+(?:this\\s+)?memory\\s*:?\\s*)(.+)", RegexOption.IGNORE_CASE),
        // Multilingual: Bengali
        Regex("^(?:দয়া করে\\s+)?মনে\\s+রাখো\\s*(?:যে\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:দয়া করে\\s+)?মনে\\s+রাখবে\\s*(?:যে\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:দয়া করে\\s+)?মনে\\s+রাখবেন\\s*(?:যে\\s+)?(.+)", RegexOption.IGNORE_CASE),
        // Multilingual: Hindi
        Regex("^(?:कृपया\\s+)?याद\\s+रखना\\s*(?:कि\\s+)?(.+)", RegexOption.IGNORE_CASE),
        Regex("^(?:कृपया\\s+)?याद\\s+रखें\\s*(?:कि\\s+)?(.+)", RegexOption.IGNORE_CASE)
    )

    private val SECRET_PATTERNS = listOf(
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bapi[\\s_-]?key\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsecret[\\s_-]?key\\b", RegexOption.IGNORE_CASE),
        Regex("\\botp\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpin(?:[\\s_-]?code)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcvv\\b", RegexOption.IGNORE_CASE),
        Regex("\\bauth[\\s_-]?token\\b", RegexOption.IGNORE_CASE),
        Regex("\\baccess[\\s_-]?token\\b", RegexOption.IGNORE_CASE),
        Regex("\\bbearer\\s+[a-zA-Z0-9_.-]+", RegexOption.IGNORE_CASE),
        Regex("\\bprivate[\\s_-]?key\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcredential(?:s)?\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * Checks if the user message contains an explicit request to remember.
     * If explicit request is present and NOT containing any obvious secret,
     * returns a new [MemoryItem], else null.
     */
    fun extractMemory(userMessage: String): MemoryItem? {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) return null

        var extractedContent: String? = null

        for (pattern in REMEMBER_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty()) {
                    extractedContent = candidate
                    break
                }
            }
        }

        if (extractedContent == null) return null

        // Strictly disallow saving secrets or credentials
        if (containsObviousSecrets(extractedContent) || containsObviousSecrets(trimmed)) {
            return null
        }

        val category = categorize(extractedContent)
        val now = System.currentTimeMillis()

        return MemoryItem(
            id = UUID.randomUUID().toString(),
            content = extractedContent,
            category = category,
            createdAt = now,
            updatedAt = now,
            enabled = true
        )
    }

    /**
     * Returns true if the content contains passwords, keys, tokens, OTPs, etc.
     */
    fun containsObviousSecrets(text: String): Boolean {
        for (pattern in SECRET_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                return true
            }
        }
        return false
    }

    /**
     * Categorizes memory into PREFERENCE, PROFILE, INSTRUCTION, PROJECT, OTHER.
     */
    fun categorize(content: String): MemoryCategory {
        val lower = content.lowercase(Locale.ROOT)

        val isPreference = listOf(
            "prefer", "like", "favorite", "love", "hate", "dislike",
            "language", "bengali", "english", "hindi", "spanish", "dark mode",
            "light mode", "theme", "tone", "style", "বাংলা", "পছন্দ"
        ).any { lower.contains(it) }
        if (isPreference) return MemoryCategory.PREFERENCE

        val isProfile = listOf(
            "my name is", "i am a", "i'm a", "i live in", "my age",
            "birthday", "email", "profession", "occupation", "job",
            "student", "engineer", "developer", "আমার নাম", "আমি"
        ).any { lower.contains(it) }
        if (isProfile) return MemoryCategory.PROFILE

        val isInstruction = listOf(
            "always", "never", "instruction", "rule", "guideline",
            "format as", "respond in", "keep answers", "concise", "detailed",
            "use markdown", "নিয়ম", "সবসময়"
        ).any { lower.contains(it) }
        if (isInstruction) return MemoryCategory.INSTRUCTION

        val isProject = listOf(
            "project", "repo", "repository", "codebase", "building",
            "app name", "working on", "mayra"
        ).any { lower.contains(it) }
        if (isProject) return MemoryCategory.PROJECT

        return MemoryCategory.OTHER
    }
}
