package com.blake.editor.markdown

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

sealed interface MarkdownInline {
    data class Plain(val text: String) : MarkdownInline
    data class Strong(val children: List<MarkdownInline>) : MarkdownInline
    data class Emphasis(val children: List<MarkdownInline>) : MarkdownInline
    data class Strike(val children: List<MarkdownInline>) : MarkdownInline
    data class InlineCode(val code: String) : MarkdownInline
    data class LinkSpan(val destination: String, val children: List<MarkdownInline>) : MarkdownInline
    data object LineBreak : MarkdownInline
}

data class MarkdownListItem(
    val marker: String,
    val checked: Boolean?,
    val blocks: List<MarkdownBlock>
)

sealed interface MarkdownBlock {
    data class HeadingBlock(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock
    data class ParagraphBlock(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class QuoteBlock(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<MarkdownListItem>) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class TableData(
        val headers: List<List<MarkdownInline>>,
        val rows: List<List<List<MarkdownInline>>>
    ) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Pure-Kotlin CommonMark + GFM parser producing an immutable AST for native
 * Compose Editorial Markdown reading — zero WebView, zero raw HTML execution.
 */
object MarkdownParser {
    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create()
            )
        )
        .build()

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val document = parser.parse(markdown)
        return collectBlocks(document)
    }

    private fun collectBlocks(parent: Node): List<MarkdownBlock> {
        val blocks = ArrayList<MarkdownBlock>()
        var child = parent.firstChild
        while (child != null) {
            convertBlock(child)?.let { blocks.add(it) }
            child = child.next
        }
        return blocks
    }

    private fun convertBlock(node: Node): MarkdownBlock? = when (node) {
        is Heading -> MarkdownBlock.HeadingBlock(
            level = node.level.coerceIn(1, 6),
            inlines = collectInlines(node)
        )
        is Paragraph -> MarkdownBlock.ParagraphBlock(collectInlines(node))
        is BlockQuote -> MarkdownBlock.QuoteBlock(collectBlocks(node))
        is BulletList -> MarkdownBlock.ListBlock(
            ordered = false,
            items = collectListItems(node, ordered = false, startNumber = 1)
        )
        is OrderedList -> MarkdownBlock.ListBlock(
            ordered = true,
            items = collectListItems(node, ordered = true, startNumber = node.markerStartNumber ?: 1)
        )
        is FencedCodeBlock -> MarkdownBlock.CodeBlock(
            language = node.info?.trim().orEmpty(),
            code = node.literal.orEmpty().trimEnd('\n')
        )
        is IndentedCodeBlock -> MarkdownBlock.CodeBlock(
            language = "",
            code = node.literal.orEmpty().trimEnd('\n')
        )
        is ThematicBreak -> MarkdownBlock.Divider
        is TableBlock -> parseTable(node)
        is HtmlBlock -> {
            // Render raw HTML safely as plain text rather than executing markup.
            val raw = node.literal.orEmpty().trim()
            if (raw.isEmpty()) null else MarkdownBlock.ParagraphBlock(listOf(MarkdownInline.Plain(raw)))
        }
        else -> null
    }

    private fun collectListItems(
        listNode: Node,
        ordered: Boolean,
        startNumber: Int
    ): List<MarkdownListItem> {
        val items = ArrayList<MarkdownListItem>()
        var index = startNumber
        var child = listNode.firstChild
        while (child != null) {
            if (child is ListItem) {
                val taskMarker = findTaskMarker(child)
                val marker = when {
                    taskMarker != null -> ""
                    ordered -> "$index."
                    else -> "•"
                }
                val itemBlocks = collectBlocks(child)
                items.add(
                    MarkdownListItem(
                        marker = marker,
                        checked = taskMarker?.isChecked,
                        blocks = itemBlocks
                    )
                )
                index++
            }
            child = child.next
        }
        return items
    }

    private fun findTaskMarker(listItem: ListItem): TaskListItemMarker? {
        var child = listItem.firstChild
        while (child != null) {
            if (child is TaskListItemMarker) return child
            if (child is Paragraph) {
                var pChild = child.firstChild
                while (pChild != null) {
                    if (pChild is TaskListItemMarker) return pChild
                    pChild = pChild.next
                }
            }
            child = child.next
        }
        return null
    }

    private fun parseTable(table: TableBlock): MarkdownBlock.TableData {
        val headers = ArrayList<List<MarkdownInline>>()
        val rows = ArrayList<List<List<MarkdownInline>>>()
        var section = table.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> {
                    var row = section.firstChild
                    while (row != null) {
                        if (row is TableRow && headers.isEmpty()) {
                            headers.addAll(collectCells(row))
                        }
                        row = row.next
                    }
                }
                is TableBody -> {
                    var row = section.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            rows.add(collectCells(row))
                        }
                        row = row.next
                    }
                }
            }
            section = section.next
        }
        return MarkdownBlock.TableData(headers = headers, rows = rows)
    }

    private fun collectCells(row: TableRow): List<List<MarkdownInline>> {
        val cells = ArrayList<List<MarkdownInline>>()
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                cells.add(collectInlines(cell))
            }
            cell = cell.next
        }
        return cells
    }

    private fun collectInlines(parent: Node): List<MarkdownInline> {
        val inlines = ArrayList<MarkdownInline>()
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is TaskListItemMarker -> { /* Handled at list-item level */ }
                is Text -> inlines.add(MarkdownInline.Plain(child.literal.orEmpty()))
                is StrongEmphasis -> inlines.add(MarkdownInline.Strong(collectInlines(child)))
                is Emphasis -> inlines.add(MarkdownInline.Emphasis(collectInlines(child)))
                is Strikethrough -> inlines.add(MarkdownInline.Strike(collectInlines(child)))
                is Code -> inlines.add(MarkdownInline.InlineCode(child.literal.orEmpty()))
                is Link -> inlines.add(
                    MarkdownInline.LinkSpan(
                        destination = child.destination.orEmpty(),
                        children = collectInlines(child).ifEmpty {
                            listOf(MarkdownInline.Plain(child.destination.orEmpty()))
                        }
                    )
                )
                is Image -> {
                    val alt = collectInlines(child).joinPlainText().ifBlank { child.title ?: child.destination.orEmpty() }
                    inlines.add(MarkdownInline.Plain("[$alt]"))
                }
                is HardLineBreak -> inlines.add(MarkdownInline.LineBreak)
                is SoftLineBreak -> inlines.add(MarkdownInline.Plain(" "))
                is HtmlInline -> inlines.add(MarkdownInline.Plain(child.literal.orEmpty()))
                else -> inlines.addAll(collectInlines(child))
            }
            child = child.next
        }
        return inlines
    }

    private fun List<MarkdownInline>.joinPlainText(): String = buildString {
        for (item in this@joinPlainText) {
            when (item) {
                is MarkdownInline.Plain -> append(item.text)
                is MarkdownInline.Strong -> append(item.children.joinPlainText())
                is MarkdownInline.Emphasis -> append(item.children.joinPlainText())
                is MarkdownInline.Strike -> append(item.children.joinPlainText())
                is MarkdownInline.InlineCode -> append(item.code)
                is MarkdownInline.LinkSpan -> append(item.children.joinPlainText())
                MarkdownInline.LineBreak -> append('\n')
            }
        }
    }
}
