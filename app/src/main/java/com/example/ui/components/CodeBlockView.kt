package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCodeBlockBg
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraSuccessGreen
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary
import kotlinx.coroutines.delay

@Composable
fun CodeBlockView(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    onCopied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val displayLanguage = language?.trim()?.ifBlank { "code" } ?: "code"
    val highlightedCode = remember(code, language) {
        highlightSyntax(code, displayLanguage)
    }

    val copyBgColor by animateColorAsState(
        targetValue = if (isCopied) MayraSuccessGreen.copy(alpha = 0.2f) else MayraIndigo.copy(alpha = 0.15f),
        label = "copyBgColor"
    )
    val copyBorderColor by animateColorAsState(
        targetValue = if (isCopied) MayraSuccessGreen else MayraDarkBorder,
        label = "copyBorderColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MayraCodeBlockBg)
            .border(1.dp, MayraDarkBorder, RoundedCornerShape(10.dp))
    ) {
        // Terminal Window Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Window dots + Language label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))

                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = displayLanguage.lowercase(),
                    color = MayraCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Copy to Clipboard Button (48dp touch target accessible)
            Surface(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("code", code)
                    clipboard.setPrimaryClip(clip)
                    isCopied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    onCopied?.invoke()
                },
                modifier = Modifier
                    .testTag("copy_code_button")
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isCopied) "Code copied" else "Copy code to clipboard"
                    }
                    .heightIn(min = 36.dp),
                shape = RoundedCornerShape(8.dp),
                color = copyBgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, copyBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = isCopied,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "copyIcon"
                    ) { copied ->
                        if (copied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MayraSuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MayraTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        fontSize = 12.sp,
                        color = if (isCopied) MayraSuccessGreen else MayraTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Code Content (Horizontal scroll)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = highlightedCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MayraTextPrimary
            )
        }
    }
}

private fun highlightSyntax(code: String, language: String): AnnotatedString = buildAnnotatedString {
    append(code)

    val keywordColor = Color(0xFFFF7B72)
    val stringColor = Color(0xFFA5D6FF)
    val numberColor = Color(0xFF79C0FF)
    val commentColor = Color(0xFF8B949E)
    val typeColor = Color(0xFFFFA657)

    val keywords = setOf(
        "val", "var", "fun", "class", "interface", "object", "return", "if", "else",
        "for", "while", "when", "import", "package", "override", "private", "public",
        "protected", "internal", "data", "sealed", "suspend", "const", "def", "lambda",
        "function", "const", "let", "export", "import", "from", "async", "await",
        "try", "catch", "finally", "throw", "null", "true", "false", "this", "super",
        "new", "extends", "implements", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE"
    )

    // Highlight strings ("..." or '...')
    val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
    stringRegex.findAll(code).forEach { match ->
        addStyle(
            SpanStyle(color = stringColor),
            match.range.first,
            match.range.last + 1
        )
    }

    // Highlight comments (//... or /*...*/)
    val commentRegex = Regex("//.*|/\\*[\\s\\S]*?\\*/")
    commentRegex.findAll(code).forEach { match ->
        addStyle(
            SpanStyle(color = commentColor),
            match.range.first,
            match.range.last + 1
        )
    }

    // Highlight numbers
    val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
    numberRegex.findAll(code).forEach { match ->
        addStyle(
            SpanStyle(color = numberColor),
            match.range.first,
            match.range.last + 1
        )
    }

    // Highlight keywords
    val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    wordRegex.findAll(code).forEach { match ->
        val word = match.value
        if (word in keywords) {
            addStyle(
                SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        } else if (word.first().isUpperCase()) {
            addStyle(
                SpanStyle(color = typeColor),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}
