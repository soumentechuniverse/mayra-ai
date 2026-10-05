package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.components.MarkdownListItem
import com.example.ui.components.MarkdownParser
import com.example.ui.components.MarkdownUiBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkdownRendererTest {

    private val baseTextColor = Color.White

    @Test
    fun `parse empty or blank markdown returns empty block list`() {
        val emptyBlocks = MarkdownParser.parse("", baseTextColor)
        assertTrue(emptyBlocks.isEmpty())

        val blankBlocks = MarkdownParser.parse("   \n\n  \t  ", baseTextColor)
        assertTrue(blankBlocks.isEmpty())
    }

    @Test
    fun `parse headings of various levels extracts level and styled text`() {
        val markdown = """
            # Heading Level 1
            ## Heading Level 2
            ### Heading Level 3
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown, baseTextColor)
        assertEquals(3, blocks.size)

        val h1 = blocks[0] as MarkdownUiBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("Heading Level 1", h1.text.text)

        val h2 = blocks[1] as MarkdownUiBlock.Heading
        assertEquals(2, h2.level)
        assertEquals("Heading Level 2", h2.text.text)

        val h3 = blocks[2] as MarkdownUiBlock.Heading
        assertEquals(3, h3.level)
        assertEquals("Heading Level 3", h3.text.text)
    }

    @Test
    fun `parse formatted inline text captures bold italic strikethrough code and links`() {
        val markdown = "Hello **bold text** and *italic words* and ~~deleted text~~ and `val x = 10` and [Mayra Docs](https://example.com/docs)"
        val blocks = MarkdownParser.parse(markdown, baseTextColor)

        assertEquals(1, blocks.size)
        val paragraph = blocks[0] as MarkdownUiBlock.Paragraph
        val annotated = paragraph.text

        // Verify raw text contents are present
        assertTrue(annotated.text.contains("Hello"))
        assertTrue(annotated.text.contains("bold text"))
        assertTrue(annotated.text.contains("italic words"))
        assertTrue(annotated.text.contains("deleted text"))
        assertTrue(annotated.text.contains("val x = 10"))
        assertTrue(annotated.text.contains("Mayra Docs"))

        // Verify bold span style exists
        val boldSpans = annotated.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Must contain bold span", boldSpans.isNotEmpty())

        // Verify italic span style exists
        val italicSpans = annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertTrue("Must contain italic span", italicSpans.isNotEmpty())

        // Verify strikethrough span style exists
        val strikeSpans = annotated.spanStyles.filter { it.item.textDecoration == TextDecoration.LineThrough }
        assertTrue("Must contain strikethrough span", strikeSpans.isNotEmpty())

        // Verify link annotation tag exists
        val urlAnnotations = annotated.getStringAnnotations("URL", 0, annotated.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals("https://example.com/docs", urlAnnotations[0].item)
    }

    @Test
    fun `parse fenced code block extracts language and raw code contents`() {
        val markdown = """
            Here is Kotlin code:
            ```kotlin
            fun greet(name: String): String {
                return "Hello, " + name
            }
            ```
            End of code.
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown, baseTextColor)
        assertEquals(3, blocks.size)

        assertTrue(blocks[0] is MarkdownUiBlock.Paragraph)
        val codeBlock = blocks[1] as MarkdownUiBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("fun greet(name: String)"))
        assertTrue(codeBlock.code.contains("return \"Hello, \" + name"))
        assertTrue(blocks[2] is MarkdownUiBlock.Paragraph)
    }

    @Test
    fun `parse GFM tables extracts headers rows and column alignments`() {
        val markdown = """
            | Feature | Status | Alignment |
            | :--- | :---: | ---: |
            | Markdown | **Supported** | Left |
            | Tables | Ready | Center |
            | Code Blocks | `Complete` | Right |
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown, baseTextColor)
        assertEquals(1, blocks.size)

        val tableBlock = blocks[0] as MarkdownUiBlock.Table
        val table = tableBlock.table

        // Verify 3 headers
        assertEquals(3, table.headers.size)
        assertEquals("Feature", table.headers[0].content.text)
        assertEquals(TextAlign.Start, table.headers[0].alignment)
        assertTrue(table.headers[0].isHeader)

        assertEquals("Status", table.headers[1].content.text)
        assertEquals(TextAlign.Center, table.headers[1].alignment)

        assertEquals("Alignment", table.headers[2].content.text)
        assertEquals(TextAlign.End, table.headers[2].alignment)

        // Verify 3 body rows
        assertEquals(3, table.rows.size)

        // Row 1
        assertEquals("Markdown", table.rows[0][0].content.text)
        assertEquals("Supported", table.rows[0][1].content.text)
        // Verify inline bold formatting was preserved in table cell
        assertTrue(table.rows[0][1].content.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        // Row 3
        assertEquals("Code Blocks", table.rows[2][0].content.text)
        assertTrue(table.rows[2][1].content.text.contains("Complete"))
        assertEquals("Right", table.rows[2][2].content.text)
        assertEquals(TextAlign.End, table.rows[2][2].alignment)
    }

    @Test
    fun `parse bullet and numbered lists preserves item ordering and text`() {
        val bulletMarkdown = """
            * First item
            * Second item with **bold**
            * Third item
        """.trimIndent()

        val bulletBlocks = MarkdownParser.parse(bulletMarkdown, baseTextColor)
        assertEquals(1, bulletBlocks.size)
        val bulletList = bulletBlocks[0] as MarkdownUiBlock.ListGroup
        assertEquals(false, bulletList.isOrdered)
        assertEquals(3, bulletList.items.size)
        assertEquals("First item", bulletList.items[0].content.text)
        assertTrue(bulletList.items[1].content.text.contains("Second item with bold"))
        assertTrue(bulletList.items[1].content.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        val orderedMarkdown = """
            1. Step one
            2. Step two
            3. Step three
        """.trimIndent()

        val orderedBlocks = MarkdownParser.parse(orderedMarkdown, baseTextColor)
        assertEquals(1, orderedBlocks.size)
        val orderedList = orderedBlocks[0] as MarkdownUiBlock.ListGroup
        assertEquals(true, orderedList.isOrdered)
        assertEquals(1, orderedList.startNumber)
        assertEquals(3, orderedList.items.size)
        assertEquals("Step one", orderedList.items[0].content.text)
        assertEquals("Step two", orderedList.items[1].content.text)
        assertEquals("Step three", orderedList.items[2].content.text)
    }

    @Test
    fun `parse blockquote extracts inner quote content`() {
        val markdown = """
            > Mayra AI is an intelligent assistant.
            > It provides fast, accurate answers.
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown, baseTextColor)
        assertEquals(1, blocks.size)
        val quote = blocks[0] as MarkdownUiBlock.BlockQuote
        assertTrue(quote.blocks.isNotEmpty())
        val innerParagraph = quote.blocks[0] as MarkdownUiBlock.Paragraph
        assertTrue(innerParagraph.text.text.contains("Mayra AI is an intelligent assistant"))
    }

    @Test
    fun `parse complex AI response with mixed formatting tables and code blocks`() {
        val aiResponse = """
            ### Comparison of AI Models

            Here is a breakdown of the models available in **Mayra AI**:

            | Model | Latency | Primary Use Case |
            | :--- | :---: | :--- |
            | Gemini 2.5 Flash | Fast | Daily conversations & questions |
            | Gemini 2.5 Pro | Moderate | Complex reasoning & code generation |

            You can invoke the streaming API using:
            ```kotlin
            service.generateStream(prompt = "Hello", config = config)
            ```

            Key advantages:
            1. Native Jetpack Compose UI
            2. Zero web-view overhead
            3. Responsive tables with horizontal scroll
        """.trimIndent()

        val blocks = MarkdownParser.parse(aiResponse, baseTextColor)

        // Must contain Heading, Paragraph, Table, Paragraph, CodeBlock, Paragraph, ListGroup
        assertTrue("Should contain Heading", blocks.any { it is MarkdownUiBlock.Heading })
        assertTrue("Should contain Table", blocks.any { it is MarkdownUiBlock.Table })
        assertTrue("Should contain CodeBlock", blocks.any { it is MarkdownUiBlock.CodeBlock })
        assertTrue("Should contain ListGroup", blocks.any { it is MarkdownUiBlock.ListGroup })

        val table = (blocks.first { it is MarkdownUiBlock.Table } as MarkdownUiBlock.Table).table
        assertEquals(3, table.headers.size)
        assertEquals(2, table.rows.size)

        val codeBlock = blocks.first { it is MarkdownUiBlock.CodeBlock } as MarkdownUiBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("service.generateStream"))
    }
}
