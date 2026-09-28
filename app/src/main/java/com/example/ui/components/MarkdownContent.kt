package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated

private sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Header(val level: Int, val text: String) : ContentBlock
    data class BulletItem(val text: String, val level: Int = 0) : ContentBlock
    data class NumberedItem(val number: String, val text: String) : ContentBlock
    data class CodeBlock(val language: String, val code: String) : ContentBlock
    data class Image(val alt: String, val url: String) : ContentBlock
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onCodeCopied: (() -> Unit)? = null
) {
    val blocks = parseContentToBlocks(content)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.CodeBlock -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language,
                        onCopied = onCodeCopied
                    )
                }

                is ContentBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = parseInlineFormatting(block.text, textColor),
                        style = style.copy(
                            color = MayraCyan,
                            textDirection = TextDirection.ContentOrLtr
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is ContentBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 12).dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MayraCyan)
                        )
                        Text(
                            text = parseInlineFormatting(block.text, textColor),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDirection = TextDirection.ContentOrLtr,
                                lineHeight = 22.sp
                            ),
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ContentBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MayraCyan
                            )
                        )
                        Text(
                            text = parseInlineFormatting(block.text, textColor),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDirection = TextDirection.ContentOrLtr,
                                lineHeight = 22.sp
                            ),
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ContentBlock.Paragraph -> {
                    Text(
                        text = parseInlineFormatting(block.text, textColor),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.ContentOrLtr,
                            lineHeight = 23.sp
                        ),
                        color = textColor
                    )
                }

                is ContentBlock.Image -> {
                    val context = LocalContext.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MayraDarkSurface)
                            .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(block.url)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                } catch (_: Exception) {}
                            }
                    ) {
                        AsyncImage(
                            model = block.url,
                            contentDescription = block.alt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = block.alt.ifBlank { "Retrieved image" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Retrieved via Wikimedia Commons / Open Web",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = "Open full image",
                                tint = MayraCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Parses markdown into sequential logical blocks.
 */
private fun parseContentToBlocks(text: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = text.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        // Markdown image: ![alt](url)
        val imageMatch = Regex("""^!\[(.*?)\]\((.*?)\)$""").find(trimmed)
        if (imageMatch != null) {
            val alt = imageMatch.groupValues[1]
            val url = imageMatch.groupValues[2]
            blocks.add(ContentBlock.Image(alt, url))
            index++
            continue
        }

        if (trimmed.startsWith("```")) {
            // Code block start
            val language = trimmed.removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeBuilder.appendLine(lines[index])
                index++
            }
            blocks.add(ContentBlock.CodeBlock(language, codeBuilder.toString().trimEnd()))
            index++ // Skip closing ```
            continue
        }

        if (trimmed.startsWith("### ")) {
            blocks.add(ContentBlock.Header(3, trimmed.removePrefix("### ")))
            index++
            continue
        }
        if (trimmed.startsWith("## ")) {
            blocks.add(ContentBlock.Header(2, trimmed.removePrefix("## ")))
            index++
            continue
        }
        if (trimmed.startsWith("# ")) {
            blocks.add(ContentBlock.Header(1, trimmed.removePrefix("# ")))
            index++
            continue
        }

        // Bullet items (*, -, •)
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
            val bulletContent = trimmed.substring(2).trim()
            blocks.add(ContentBlock.BulletItem(bulletContent))
            index++
            continue
        }

        // Numbered lists (1. , 2. )
        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val rest = numberedMatch.groupValues[2]
            blocks.add(ContentBlock.NumberedItem(num, rest))
            index++
            continue
        }

        if (trimmed.isNotBlank()) {
            // Accumulate regular paragraph lines
            val pBuilder = StringBuilder(line)
            index++
            while (index < lines.size) {
                val nextTrimmed = lines[index].trim()
                if (nextTrimmed.isBlank() ||
                    nextTrimmed.startsWith("```") ||
                    nextTrimmed.startsWith("#") ||
                    nextTrimmed.startsWith("* ") ||
                    nextTrimmed.startsWith("- ") ||
                    nextTrimmed.startsWith("• ") ||
                    nextTrimmed.startsWith("![") ||
                    Regex("^(\\d+)\\.\\s+.*").matches(nextTrimmed)
                ) {
                    break
                }
                pBuilder.append("\n").append(lines[index])
                index++
            }
            blocks.add(ContentBlock.Paragraph(pBuilder.toString()))
            continue
        }

        index++
    }

    return blocks
}

/**
 * Formats inline bold (**text**), italic (*text*), and code (`text`).
 */
private fun parseInlineFormatting(raw: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = raw.length

        while (cursor < len) {
            // Inline code `code`
            if (raw[cursor] == '`') {
                val nextTick = raw.indexOf('`', cursor + 1)
                if (nextTick != -1) {
                    val codeSnippet = raw.substring(cursor + 1, nextTick)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = MayraCyan,
                            background = Color(0x3038BDF8),
                            fontSize = 13.sp
                        )
                    )
                    append(" $codeSnippet ")
                    pop()
                    cursor = nextTick + 1
                    continue
                }
            }

            // Bold **text**
            if (cursor + 1 < len && raw[cursor] == '*' && raw[cursor + 1] == '*') {
                val nextStar = raw.indexOf("**", cursor + 2)
                if (nextStar != -1) {
                    val boldText = raw.substring(cursor + 2, nextStar)
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = baseColor
                        )
                    )
                    append(boldText)
                    pop()
                    cursor = nextStar + 2
                    continue
                }
            }

            // Italic *text*
            if (raw[cursor] == '*' && (cursor + 1 >= len || raw[cursor + 1] != '*')) {
                val nextStar = raw.indexOf('*', cursor + 1)
                if (nextStar != -1 && (nextStar + 1 >= len || raw[nextStar + 1] != '*')) {
                    val italicText = raw.substring(cursor + 1, nextStar)
                    pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = baseColor
                        )
                    )
                    append(italicText)
                    pop()
                    cursor = nextStar + 1
                    continue
                }
            }

            append(raw[cursor])
            cursor++
        }
    }
}
