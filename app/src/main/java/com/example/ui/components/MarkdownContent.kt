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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
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
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

sealed interface MarkdownUiBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MarkdownUiBlock
    data class Paragraph(val text: AnnotatedString) : MarkdownUiBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownUiBlock
    data class Table(val table: TableUiModel) : MarkdownUiBlock
    data class BlockQuote(val blocks: List<MarkdownUiBlock>) : MarkdownUiBlock
    data class ListGroup(val isOrdered: Boolean, val startNumber: Int, val items: List<MarkdownListItem>) : MarkdownUiBlock
    data class Image(val alt: String, val url: String) : MarkdownUiBlock
    data object Divider : MarkdownUiBlock
}

data class MarkdownListItem(
    val content: AnnotatedString,
    val nestedBlocks: List<MarkdownUiBlock> = emptyList()
)

object MarkdownParser {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create()
    )

    private val parser = Parser.builder()
        .extensions(extensions)
        .build()

    fun parse(markdown: String, textColor: Color): List<MarkdownUiBlock> {
        if (markdown.isBlank()) return emptyList()
        val document = parser.parse(markdown)
        return convertChildrenToBlocks(document, textColor)
    }

    private fun convertChildrenToBlocks(parent: Node, textColor: Color): List<MarkdownUiBlock> {
        val blocks = mutableListOf<MarkdownUiBlock>()
        var child = parent.firstChild

        while (child != null) {
            when (child) {
                is Heading -> {
                    val text = buildInlineAnnotatedString(child, MayraCyan)
                    blocks.add(MarkdownUiBlock.Heading(child.level, text))
                }

                is Paragraph -> {
                    // Check if paragraph contains only an image
                    val singleImage = child.firstChild as? Image
                    if (singleImage != null && child.firstChild == child.lastChild) {
                        blocks.add(
                            MarkdownUiBlock.Image(
                                alt = singleImage.title ?: buildInlinePlainText(singleImage),
                                url = singleImage.destination
                            )
                        )
                    } else {
                        val text = buildInlineAnnotatedString(child, textColor)
                        if (text.isNotBlank()) {
                            blocks.add(MarkdownUiBlock.Paragraph(text))
                        }
                    }
                }

                is FencedCodeBlock -> {
                    blocks.add(
                        MarkdownUiBlock.CodeBlock(
                            language = child.info ?: "code",
                            code = child.literal.trimEnd()
                        )
                    )
                }

                is IndentedCodeBlock -> {
                    blocks.add(
                        MarkdownUiBlock.CodeBlock(
                            language = "code",
                            code = child.literal.trimEnd()
                        )
                    )
                }

                is TableBlock -> {
                    val tableModel = extractTableBlock(child, textColor)
                    blocks.add(MarkdownUiBlock.Table(tableModel))
                }

                is BlockQuote -> {
                    val quoteBlocks = convertChildrenToBlocks(child, textColor)
                    blocks.add(MarkdownUiBlock.BlockQuote(quoteBlocks))
                }

                is BulletList -> {
                    val items = extractListItems(child, textColor)
                    blocks.add(MarkdownUiBlock.ListGroup(isOrdered = false, startNumber = 1, items = items))
                }

                is OrderedList -> {
                    val items = extractListItems(child, textColor)
                    blocks.add(
                        MarkdownUiBlock.ListGroup(
                            isOrdered = true,
                            startNumber = child.startNumber,
                            items = items
                        )
                    )
                }

                is ThematicBreak -> {
                    blocks.add(MarkdownUiBlock.Divider)
                }

                is CustomBlock -> {
                    if (child is TableBlock) {
                        val tableModel = extractTableBlock(child, textColor)
                        blocks.add(MarkdownUiBlock.Table(tableModel))
                    }
                }
            }
            child = child.next
        }

        return blocks
    }

    private fun extractListItems(listNode: Node, textColor: Color): List<MarkdownListItem> {
        val items = mutableListOf<MarkdownListItem>()
        var itemChild = listNode.firstChild

        while (itemChild != null) {
            if (itemChild is ListItem) {
                val inlineBuilder = AnnotatedString.Builder()
                val nestedBlocks = mutableListOf<MarkdownUiBlock>()

                var node = itemChild.firstChild
                while (node != null) {
                    when (node) {
                        is Paragraph -> {
                            appendNodeInline(node, inlineBuilder, textColor)
                        }
                        is BulletList, is OrderedList, is TableBlock, is FencedCodeBlock -> {
                            nestedBlocks.addAll(convertChildrenToBlocks(node.parent ?: node, textColor))
                        }
                        else -> {
                            appendNodeInline(node, inlineBuilder, textColor)
                        }
                    }
                    node = node.next
                }

                items.add(
                    MarkdownListItem(
                        content = inlineBuilder.toAnnotatedString(),
                        nestedBlocks = nestedBlocks
                    )
                )
            }
            itemChild = itemChild.next
        }
        return items
    }

    private fun extractTableBlock(tableBlock: TableBlock, textColor: Color): TableUiModel {
        val headers = mutableListOf<TableUiCell>()
        val rows = mutableListOf<List<TableUiCell>>()

        var child = tableBlock.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    var rowChild = child.firstChild
                    while (rowChild != null) {
                        if (rowChild is TableRow) {
                            var cellChild = rowChild.firstChild
                            while (cellChild != null) {
                                if (cellChild is TableCell) {
                                    val align = when (cellChild.alignment) {
                                        TableCell.Alignment.LEFT -> TextAlign.Start
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        null -> TextAlign.Start
                                    }
                                    val text = buildInlineAnnotatedString(cellChild, MayraCyan)
                                    headers.add(TableUiCell(content = text, alignment = align, isHeader = true))
                                }
                                cellChild = cellChild.next
                            }
                        }
                        rowChild = rowChild.next
                    }
                }

                is TableBody -> {
                    var rowChild = child.firstChild
                    while (rowChild != null) {
                        if (rowChild is TableRow) {
                            val rowCells = mutableListOf<TableUiCell>()
                            var cellChild = rowChild.firstChild
                            while (cellChild != null) {
                                if (cellChild is TableCell) {
                                    val align = when (cellChild.alignment) {
                                        TableCell.Alignment.LEFT -> TextAlign.Start
                                        TableCell.Alignment.CENTER -> TextAlign.Center
                                        TableCell.Alignment.RIGHT -> TextAlign.End
                                        null -> TextAlign.Start
                                    }
                                    val text = buildInlineAnnotatedString(cellChild, textColor)
                                    rowCells.add(TableUiCell(content = text, alignment = align, isHeader = false))
                                }
                                cellChild = cellChild.next
                            }
                            if (rowCells.isNotEmpty()) {
                                rows.add(rowCells)
                            }
                        }
                        rowChild = rowChild.next
                    }
                }
            }
            child = child.next
        }

        return TableUiModel(headers = headers, rows = rows)
    }

    private fun buildInlineAnnotatedString(parentNode: Node, baseColor: Color): AnnotatedString {
        val builder = AnnotatedString.Builder()
        var child = parentNode.firstChild
        while (child != null) {
            appendNodeInline(child, builder, baseColor)
            child = child.next
        }
        return builder.toAnnotatedString()
    }

    private fun buildInlinePlainText(parentNode: Node): String {
        val sb = StringBuilder()
        var child = parentNode.firstChild
        while (child != null) {
            if (child is org.commonmark.node.Text) {
                sb.append(child.literal)
            } else {
                sb.append(buildInlinePlainText(child))
            }
            child = child.next
        }
        return sb.toString()
    }

    private fun appendNodeInline(node: Node, builder: AnnotatedString.Builder, baseColor: Color) {
        when (node) {
            is org.commonmark.node.Text -> {
                builder.append(node.literal)
            }

            is StrongEmphasis -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                var inner = node.firstChild
                while (inner != null) {
                    appendNodeInline(inner, builder, baseColor)
                    inner = inner.next
                }
                builder.pop()
            }

            is Emphasis -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                var inner = node.firstChild
                while (inner != null) {
                    appendNodeInline(inner, builder, baseColor)
                    inner = inner.next
                }
                builder.pop()
            }

            is Strikethrough -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor.copy(alpha = 0.75f)))
                var inner = node.firstChild
                while (inner != null) {
                    appendNodeInline(inner, builder, baseColor)
                    inner = inner.next
                }
                builder.pop()
            }

            is Code -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = MayraCyan,
                        background = Color(0x3038BDF8),
                        fontSize = 13.sp
                    )
                )
                builder.append(" ${node.literal} ")
                builder.pop()
            }

            is Link -> {
                builder.pushStringAnnotation(tag = "URL", annotation = node.destination)
                builder.pushStyle(
                    SpanStyle(
                        color = MayraCyan,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                )
                var inner = node.firstChild
                while (inner != null) {
                    appendNodeInline(inner, builder, MayraCyan)
                    inner = inner.next
                }
                builder.pop()
                builder.pop()
            }

            is Image -> {
                val alt = node.title ?: buildInlinePlainText(node)
                builder.append("[Image: $alt]")
            }

            is HardLineBreak -> {
                builder.append("\n")
            }

            is SoftLineBreak -> {
                builder.append(" ")
            }

            is CustomNode -> {
                if (node is Strikethrough) {
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    var inner = node.firstChild
                    while (inner != null) {
                        appendNodeInline(inner, builder, baseColor)
                        inner = inner.next
                    }
                    builder.pop()
                } else {
                    var inner = node.firstChild
                    while (inner != null) {
                        appendNodeInline(inner, builder, baseColor)
                        inner = inner.next
                    }
                }
            }

            else -> {
                var inner = node.firstChild
                while (inner != null) {
                    appendNodeInline(inner, builder, baseColor)
                    inner = inner.next
                }
            }
        }
    }
}

/**
 * Production-ready CommonMark Markdown Renderer for Jetpack Compose.
 *
 * Supports:
 * - Formatted text (Bold, Italic, Strikethrough, Monospace inline code, Clickable links)
 * - Code blocks with language tags, copy action, and syntax highlighting
 * - GFM Markdown tables with aligned columns, headers, and horizontal scroll
 * - Blockquotes with accent line
 * - Numbered & bullet lists with nested items
 * - Thematic break dividers
 * - Images with Coil async preview
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onCodeCopied: (() -> Unit)? = null
) {
    val blocks = remember(content, textColor) {
        MarkdownParser.parse(content, textColor)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("markdown_content_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            RenderMarkdownBlock(block, textColor, onCodeCopied)
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownUiBlock,
    textColor: Color,
    onCodeCopied: (() -> Unit)?
) {
    val uriHandler = LocalUriHandler.current

    when (block) {
        is MarkdownUiBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Text(
                text = block.text,
                style = style.copy(
                    color = MayraCyan,
                    textDirection = TextDirection.ContentOrLtr
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        is MarkdownUiBlock.Paragraph -> {
            val hasLinks = block.text.getStringAnnotations("URL", 0, block.text.length).isNotEmpty()
            if (hasLinks) {
                ClickableText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        lineHeight = 23.sp,
                        color = textColor
                    ),
                    onClick = { offset ->
                        block.text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    uriHandler.openUri(annotation.item)
                                } catch (_: Exception) {}
                            }
                    }
                )
            } else {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        lineHeight = 23.sp
                    ),
                    color = textColor
                )
            }
        }

        is MarkdownUiBlock.CodeBlock -> {
            CodeBlockView(
                code = block.code,
                language = block.language,
                onCopied = onCodeCopied
            )
        }

        is MarkdownUiBlock.Table -> {
            MarkdownTableView(
                table = block.table,
                textColor = textColor
            )
        }

        is MarkdownUiBlock.BlockQuote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(MayraDarkSurfaceElevated.copy(alpha = 0.5f))
                    .border(
                        width = 0.5.dp,
                        color = MayraDarkSurfaceBorder,
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MayraCyan)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.blocks.forEach { childBlock ->
                        RenderMarkdownBlock(childBlock, textColor, onCodeCopied)
                    }
                }
            }
        }

        is MarkdownUiBlock.ListGroup -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                block.items.forEachIndexed { itemIndex, listItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (block.isOrdered) {
                            Text(
                                text = "${block.startNumber + itemIndex}.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MayraCyan
                                )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 9.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MayraCyan)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listItem.content,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = TextDirection.ContentOrLtr,
                                    lineHeight = 22.sp,
                                    color = textColor
                                )
                            )
                            if (listItem.nestedBlocks.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listItem.nestedBlocks.forEach { nb ->
                                        RenderMarkdownBlock(nb, textColor, onCodeCopied)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is MarkdownUiBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 1.dp,
                color = MayraDarkSurfaceBorder
            )
        }

        is MarkdownUiBlock.Image -> {
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
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open full image",
                        tint = MayraCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
