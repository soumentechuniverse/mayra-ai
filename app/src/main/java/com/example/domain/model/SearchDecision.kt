package com.example.domain.model

enum class SearchMode {
    AUTO,
    ENABLED,
    DISABLED;

    val displayName: String
        get() = when (this) {
            AUTO -> "Auto (Recommended)"
            ENABLED -> "Always Search"
            DISABLED -> "Off"
        }

    companion object {
        val OFF: SearchMode get() = DISABLED
        val ALWAYS: SearchMode get() = ENABLED
    }
}

enum class SearchPhase {
    IDLE,
    SEARCHING,
    READING_SOURCES,
    GENERATING
}

/**
 * Result of the search intent detection evaluation.
 */
data class SearchDecision(
    val needsSearch: Boolean,
    val reason: String,
    val suggestedQuery: String? = null,
    val isExplicitRequest: Boolean = false
)
