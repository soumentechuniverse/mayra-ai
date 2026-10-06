package com.example

import com.example.ui.components.MarkdownBlock
import com.example.ui.components.parseMarkdownToBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun testParseHeadings() {
        val markdown = "# Heading 1\n## Heading 2\n### Heading 3"
        val blocks = parseMarkdownToBlocks(markdown)
        assertTrue(blocks.isNotEmpty())
        val heading1 = blocks[0] as MarkdownBlock.HeadingBlock
        assertEquals(1, heading1.level)
        assertTrue(heading1.text.text.contains("Heading 1"))

        val heading2 = blocks[1] as MarkdownBlock.HeadingBlock
        assertEquals(2, heading2.level)
        assertTrue(heading2.text.text.contains("Heading 2"))
    }

    @Test
    fun testParseFencedCodeBlock() {
        val markdown = "```kotlin\nval x = 42\n```"
        val blocks = parseMarkdownToBlocks(markdown)
        val codeBlock = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().firstOrNull()
        assertNotNull(codeBlock)
        assertEquals("kotlin", codeBlock?.language)
        assertEquals("val x = 42", codeBlock?.code)
    }

    @Test
    fun testParseTable() {
        val markdown = """
            | Name | Role |
            | --- | --- |
            | Mayra | Assistant |
            | Soumen | Developer |
        """.trimIndent()
        val blocks = parseMarkdownToBlocks(markdown)
        val tableBlock = blocks.filterIsInstance<MarkdownBlock.TableBlockItem>().firstOrNull()
        assertNotNull(tableBlock)
        assertEquals(2, tableBlock?.table?.headers?.size)
        assertEquals("Name", tableBlock?.table?.headers?.get(0))
        assertEquals(2, tableBlock?.table?.rows?.size)
    }

    @Test
    fun testParseBulletList() {
        val markdown = "- Item 1\n- Item 2\n- Item 3"
        val blocks = parseMarkdownToBlocks(markdown)
        val listBlock = blocks.filterIsInstance<MarkdownBlock.BulletListBlock>().firstOrNull()
        assertNotNull(listBlock)
        assertEquals(3, listBlock?.items?.size)
    }

    @Test
    fun testParseEmptyMarkdown() {
        val blocks = parseMarkdownToBlocks("")
        assertTrue(blocks.isEmpty())
    }
}
