package com.blake.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesHeadingsParagraphsListsChecklistsQuotesTablesAndCode() {
        val md = """
            # สวัสดี Blake

            A paragraph with **bold**, *italic*, ~~strike~~, `code`, and [link](https://example.com).

            > Quoted thought

            - [x] Done task
            - [ ] Open task

            | Name | Role |
            | --- | --- |
            | Blake | Notes |

            ```
            plain block
            ```

            ---
        """.trimIndent()

        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks[0] is MarkdownBlock.HeadingBlock)
        assertEquals(1, (blocks[0] as MarkdownBlock.HeadingBlock).level)

        val paragraph = blocks[1] as MarkdownBlock.ParagraphBlock
        assertTrue(paragraph.inlines.any { it is MarkdownInline.Strong })
        assertTrue(paragraph.inlines.any { it is MarkdownInline.Emphasis })
        assertTrue(paragraph.inlines.any { it is MarkdownInline.Strike })
        assertTrue(paragraph.inlines.any { it is MarkdownInline.InlineCode })
        assertTrue(paragraph.inlines.any { it is MarkdownInline.LinkSpan })

        assertTrue(blocks[2] is MarkdownBlock.QuoteBlock)

        val list = blocks[3] as MarkdownBlock.ListBlock
        assertEquals(2, list.items.size)
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)

        val table = blocks[4] as MarkdownBlock.TableData
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)

        val code = blocks[5] as MarkdownBlock.CodeBlock
        assertEquals("plain block", code.code)

        assertTrue(blocks[6] is MarkdownBlock.Divider)
    }

    @Test
    fun rendersRawHtmlSafelyAsPlainText() {
        val blocks = MarkdownParser.parse("<script>alert(1)</script>")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.ParagraphBlock
        assertEquals(listOf(MarkdownInline.Plain("<script>alert(1)</script>")), p.inlines)
    }
}
