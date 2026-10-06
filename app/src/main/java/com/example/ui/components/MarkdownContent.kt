package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.parser.Parser

sealed class MarkdownBlock {
    data class HeadingBlock(val level: Int, val text: AnnotatedString) : MarkdownBlock()
    data class ParagraphBlock(val text: AnnotatedString) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class BlockQuoteBlock(val blocks: List<MarkdownBlock>) : MarkdownBlock()
    data class BulletListBlock(val items: List<AnnotatedString>) : MarkdownBlock()
    data class OrderedListBlock(val items: List<AnnotatedString>, val startNumber: Int) : MarkdownBlock()
    data class TableBlockItem(val table: TableUiModel) : MarkdownBlock()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MayraTextPrimary,
    onCodeCopied: (() -> Unit)? = null
) {
    val blocks = remember(content) {
        parseMarkdownToBlocks(content)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            RenderMarkdownBlock(
                block = block,
                defaultColor = textColor,
                onCodeCopied = onCodeCopied
            )
            if (index < blocks.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    defaultColor: Color,
    onCodeCopied: (() -> Unit)?
) {
    val context = LocalContext.current

    when (block) {
        is MarkdownBlock.HeadingBlock -> {
            val (fontSize, fontWeight) = when (block.level) {
                1 -> 22.sp to FontWeight.Bold
                2 -> 19.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.SemiBold
                else -> 15.sp to FontWeight.SemiBold
            }
            Text(
                text = block.text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = MayraCyan,
                lineHeight = (fontSize.value * 1.3).sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        is MarkdownBlock.ParagraphBlock -> {
            ClickableText(
                text = block.text,
                style = TextStyle(
                    color = defaultColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                onClick = { offset ->
                    block.text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                }
            )
        }

        is MarkdownBlock.CodeBlock -> {
            CodeBlockView(
                code = block.code,
                language = block.language,
                onCopied = onCodeCopied
            )
        }

        is MarkdownBlock.BlockQuoteBlock -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MayraDarkSurface.copy(alpha = 0.6f))
                    .border(
                        androidx.compose.foundation.BorderStroke(2.dp, MayraCyan),
                        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    block.blocks.forEach { innerBlock ->
                        RenderMarkdownBlock(innerBlock, defaultColor, onCodeCopied)
                    }
                }
            }
        }

        is MarkdownBlock.BulletListBlock -> {
            Column(modifier = Modifier.padding(start = 4.dp)) {
                block.items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 10.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MayraCyan)
                        )
                        ClickableText(
                            text = item,
                            style = TextStyle(
                                color = defaultColor,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            onClick = { offset ->
                                item.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                                        } catch (_: Exception) {}
                                    }
                            }
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedListBlock -> {
            Column(modifier = Modifier.padding(start = 4.dp)) {
                block.items.forEachIndexed { i, item ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.startNumber + i}.",
                            color = MayraIndigo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        ClickableText(
                            text = item,
                            style = TextStyle(
                                color = defaultColor,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            onClick = { offset ->
                                item.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                                        } catch (_: Exception) {}
                                    }
                            }
                        )
                    }
                }
            }
        }

        is MarkdownBlock.TableBlockItem -> {
            MarkdownTableView(table = block.table)
        }
    }
}

private val commonMarkParser: Parser by lazy {
    Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create()
            )
        )
        .build()
}

fun parseMarkdownToBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    return try {
        val document = commonMarkParser.parse(markdown)
        val blocks = mutableListOf<MarkdownBlock>()
        var child: Node? = document.firstChild
        while (child != null) {
            val converted = convertNodeToBlock(child)
            if (converted != null) {
                blocks.add(converted)
            }
            child = child.next
        }
        blocks.ifEmpty { listOf(MarkdownBlock.ParagraphBlock(buildAnnotatedString { append(markdown) })) }
    } catch (_: Exception) {
        listOf(MarkdownBlock.ParagraphBlock(buildAnnotatedString { append(markdown) }))
    }
}

private fun convertNodeToBlock(node: Node): MarkdownBlock? {
    return when (node) {
        is Heading -> {
            MarkdownBlock.HeadingBlock(node.level, buildAnnotatedText(node))
        }

        is Paragraph -> {
            MarkdownBlock.ParagraphBlock(buildAnnotatedText(node))
        }

        is FencedCodeBlock -> {
            MarkdownBlock.CodeBlock(node.literal.trimEnd(), node.info)
        }

        is IndentedCodeBlock -> {
            MarkdownBlock.CodeBlock(node.literal.trimEnd(), null)
        }

        is BlockQuote -> {
            val innerBlocks = mutableListOf<MarkdownBlock>()
            var inner: Node? = node.firstChild
            while (inner != null) {
                convertNodeToBlock(inner)?.let { innerBlocks.add(it) }
                inner = inner.next
            }
            MarkdownBlock.BlockQuoteBlock(innerBlocks)
        }

        is BulletList -> {
            val items = mutableListOf<AnnotatedString>()
            var item: Node? = node.firstChild
            while (item != null) {
                if (item is ListItem) {
                    items.add(buildAnnotatedText(item))
                }
                item = item.next
            }
            MarkdownBlock.BulletListBlock(items)
        }

        is OrderedList -> {
            val items = mutableListOf<AnnotatedString>()
            var item: Node? = node.firstChild
            while (item != null) {
                if (item is ListItem) {
                    items.add(buildAnnotatedText(item))
                }
                item = item.next
            }
            MarkdownBlock.OrderedListBlock(items, node.startNumber)
        }

        is TableBlock -> {
            var headers = emptyList<String>()
            val rows = mutableListOf<List<String>>()

            var section: Node? = node.firstChild
            while (section != null) {
                when (section) {
                    is TableHead -> {
                        var row: Node? = section.firstChild
                        if (row is TableRow) {
                            headers = extractRowCells(row)
                        }
                    }
                    is TableBody -> {
                        var row: Node? = section.firstChild
                        while (row != null) {
                            if (row is TableRow) {
                                rows.add(extractRowCells(row))
                            }
                            row = row.next
                        }
                    }
                }
                section = section.next
            }
            MarkdownBlock.TableBlockItem(TableUiModel(headers, rows))
        }

        else -> null
    }
}

private fun extractRowCells(row: TableRow): List<String> {
    val cells = mutableListOf<String>()
    var cell: Node? = row.firstChild
    while (cell != null) {
        if (cell is TableCell) {
            cells.add(extractPlainText(cell))
        }
        cell = cell.next
    }
    return cells
}

private fun extractPlainText(node: Node): String {
    val sb = java.lang.StringBuilder()
    var current: Node? = node.firstChild
    while (current != null) {
        when (current) {
            is MarkdownText -> sb.append(current.literal)
            is Code -> sb.append(current.literal)
            else -> sb.append(extractPlainText(current))
        }
        current = current.next
    }
    return sb.toString().trim()
}

fun buildAnnotatedText(node: Node): AnnotatedString = buildAnnotatedString {
    appendNodeChildren(node, this)
}

private fun appendNodeChildren(parent: Node, builder: AnnotatedString.Builder) {
    var child: Node? = parent.firstChild
    while (child != null) {
        when (child) {
            is MarkdownText -> {
                builder.append(child.literal)
            }

            is StrongEmphasis -> {
                val start = builder.length
                appendNodeChildren(child, builder)
                builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    builder.length
                )
            }

            is Emphasis -> {
                val start = builder.length
                appendNodeChildren(child, builder)
                builder.addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    builder.length
                )
            }

            is Strikethrough -> {
                val start = builder.length
                appendNodeChildren(child, builder)
                builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start,
                    builder.length
                )
            }

            is Code -> {
                val start = builder.length
                builder.append(child.literal)
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MayraDarkSurfaceVariant,
                        color = MayraCyan,
                        fontSize = 13.sp
                    ),
                    start,
                    builder.length
                )
            }

            is Link -> {
                val start = builder.length
                appendNodeChildren(child, builder)
                builder.addStyle(
                    SpanStyle(
                        color = MayraCyan,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    builder.length
                )
                builder.addStringAnnotation(
                    tag = "URL",
                    annotation = child.destination,
                    start = start,
                    end = builder.length
                )
            }

            is SoftLineBreak -> {
                builder.append(" ")
            }

            is HardLineBreak -> {
                builder.append("\n")
            }

            else -> {
                appendNodeChildren(child, builder)
            }
        }
        child = child.next
    }
}
