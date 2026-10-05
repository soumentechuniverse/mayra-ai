package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.MayraCodeBg
import com.example.ui.theme.MayraCodeBorder
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraEmerald
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextMutedDark
import com.example.ui.theme.MayraViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Developer-friendly Code Block component with:
 * - Syntax highlighting (Kotlin, Python, Java, JS/TS, SQL, Bash)
 * - Header bar with language chip
 * - High-productivity 'Copy to Clipboard' button with 48dp touch target,
 *   smooth icon & label animations, haptic feedback, and toast notification
 * - Horizontal scrolling to preserve indentation and code readability
 */
@Composable
fun CodeBlockView(
    code: String,
    language: String = "code",
    modifier: Modifier = Modifier,
    onCopied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val highlightedText = remember(code, language) {
        highlightCode(code, language.trim().lowercase())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MayraCodeBg)
            .border(1.dp, MayraCodeBorder, RoundedCornerShape(12.dp))
            .testTag("code_block_view")
    ) {
        // Top Code Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MayraCodeBg.copy(alpha = 0.95f))
                .border(
                    width = 0.5.dp,
                    color = MayraCodeBorder,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language badge with terminal dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MayraCyan)
                )
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MayraCyan
                )
            }

            // Developer Copy Button: Min 48dp touch target with animated icon and label
            Surface(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("code", code)
                    clipboard?.setPrimaryClip(clip)
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (_: Exception) {}
                    isCopied = true
                    onCopied?.invoke()

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }

                    coroutineScope.launch {
                        delay(2000)
                        isCopied = false
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isCopied) MayraEmerald.copy(alpha = 0.15f) else Color.Transparent,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isCopied) MayraEmerald.copy(alpha = 0.55f) else MayraCodeBorder
                ),
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("copy_code_button")
                    .semantics {
                        contentDescription = if (isCopied) "Code copied to clipboard" else "Copy code to clipboard"
                        role = Role.Button
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedContent(
                        targetState = isCopied,
                        label = "copy_icon_anim"
                    ) { copied ->
                        if (copied) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MayraEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isCopied) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        color = if (isCopied) MayraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Code Content Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = highlightedText,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Lightweight, high-performance syntax highlighter for common programming languages.
 */
private fun highlightCode(code: String, lang: String): AnnotatedString {
    val keywords = when (lang) {
        "kotlin", "kt" -> setOf(
            "package", "import", "class", "interface", "object", "val", "var",
            "fun", "return", "if", "else", "when", "for", "while", "do", "try",
            "catch", "finally", "throw", "override", "private", "public", "protected",
            "internal", "data", "sealed", "suspend", "inline", "tailrec", "companion",
            "init", "constructor", "this", "super", "null", "true", "false", "is", "as", "in"
        )
        "java" -> setOf(
            "package", "import", "public", "private", "protected", "class", "interface",
            "extends", "implements", "static", "final", "void", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "new", "this",
            "super", "try", "catch", "finally", "throw", "throws", "null", "true", "false"
        )
        "python", "py" -> setOf(
            "def", "class", "import", "from", "return", "if", "elif", "else",
            "for", "while", "try", "except", "finally", "with", "as", "pass",
            "break", "continue", "lambda", "yield", "async", "await", "True", "False", "None"
        )
        "javascript", "js", "typescript", "ts" -> setOf(
            "const", "let", "var", "function", "return", "if", "else", "for",
            "while", "switch", "case", "break", "continue", "new", "this", "class",
            "extends", "import", "export", "default", "async", "await", "try", "catch",
            "finally", "throw", "typeof", "instanceof", "null", "undefined", "true", "false"
        )
        "sql" -> setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "join", "inner", "left", "right", "outer", "group", "by", "order",
            "having", "limit", "create", "table", "drop", "alter", "as", "and", "or", "not", "null"
        )
        else -> setOf(
            "func", "function", "def", "fun", "fn", "var", "val", "let", "const",
            "return", "if", "else", "for", "while", "class", "import", "true", "false", "null"
        )
    }

    return buildAnnotatedString {
        append(code)

        // Highlight line comments (//... or #...)
        val commentRegex = if (lang == "python" || lang == "py" || lang == "bash" || lang == "sh") {
            Regex("#.*")
        } else {
            Regex("(//.*)|(/\\*[\\s\\S]*?\\*/)")
        }
        commentRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(
                    color = MayraTextMutedDark,
                    fontStyle = FontStyle.Italic
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // Highlight string literals ("..." or '...')
        val stringRegex = Regex("(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")|('[^'\\\\]*(?:\\\\.[^'\\\\]*)*')")
        stringRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(color = MayraEmerald),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // Highlight Annotations (@Composable, @Test, @decorator)
        val annotationRegex = Regex("@[A-Za-z0-9_]+")
        annotationRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(color = MayraViolet, fontWeight = FontWeight.SemiBold),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // Highlight Keywords
        val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
        wordRegex.findAll(code).forEach { match ->
            val word = match.value
            val isKw = if (lang == "sql") keywords.contains(word.lowercase()) else keywords.contains(word)
            if (isKw) {
                addStyle(
                    style = SpanStyle(color = MayraCyan, fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }

        // Highlight Numbers
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberRegex.findAll(code).forEach { match ->
            addStyle(
                style = SpanStyle(color = MayraIndigo),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
