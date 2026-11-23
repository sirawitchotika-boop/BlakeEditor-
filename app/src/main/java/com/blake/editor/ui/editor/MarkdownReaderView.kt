package com.blake.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.markdown.MarkdownBlock
import com.blake.editor.markdown.MarkdownInline
import com.blake.editor.markdown.MarkdownParser
import com.blake.editor.ui.theme.BlakePalette
import com.blake.editor.ui.theme.LocalBlakePalette

/**
 * Native Compose Editorial Markdown Reader — zero WebView cold start,
 * calm book-like typography tuned for Thai + English reading.
 */
@Composable
fun MarkdownReaderView(
    markdown: String,
    baseFontSp: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlakePalette.current
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }

    if (blocks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = "Nothing to read yet.",
                color = palette.inkMuted,
                fontSize = baseFontSp.sp
            )
        }
        return
    }

    SelectionContainer(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (block in blocks) {
                MarkdownBlockView(
                    block = block,
                    baseFontSp = baseFontSp,
                    palette = palette
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    baseFontSp: Float,
    palette: BlakePalette
) {
    when (block) {
        is MarkdownBlock.HeadingBlock -> {
            val scale = when (block.level) {
                1 -> 1.55f
                2 -> 1.32f
                3 -> 1.16f
                else -> 1.04f
            }
            val weight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold
            val text = remember(block.inlines, palette) {
                buildInlineString(block.inlines, palette, baseFontSp * scale)
            }
            Column(modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp)) {
                Text(
                    text = text,
                    style = TextStyle(
                        color = palette.ink,
                        fontSize = (baseFontSp * scale).sp,
                        lineHeight = (baseFontSp * scale * 1.32f).sp,
                        fontWeight = weight
                    )
                )
                if (block.level == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                }
            }
        }

        is MarkdownBlock.ParagraphBlock -> {
            val text = remember(block.inlines, palette, baseFontSp) {
                buildInlineString(block.inlines, palette, baseFontSp)
            }
            Text(
                text = text,
                style = TextStyle(
                    color = palette.ink,
                    fontSize = baseFontSp.sp,
                    lineHeight = (baseFontSp * 1.62f).sp
                )
            )
        }

        is MarkdownBlock.QuoteBlock -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.accent.copy(alpha = 0.75f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (inner in block.blocks) {
                        MarkdownBlockView(
                            block = inner,
                            baseFontSp = baseFontSp * 0.97f,
                            palette = palette.copy(ink = palette.inkSecondary)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.ListBlock -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in block.items) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 24.dp)
                                .padding(top = 3.dp, end = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            when (item.checked) {
                                true -> Icon(
                                    imageVector = Icons.Rounded.CheckBox,
                                    contentDescription = "Checked",
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                false -> Icon(
                                    imageVector = Icons.Rounded.CheckBoxOutlineBlank,
                                    contentDescription = "Unchecked",
                                    tint = palette.inkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                null -> Text(
                                    text = item.marker,
                                    color = palette.inkSecondary,
                                    fontSize = baseFontSp.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (inner in item.blocks) {
                                MarkdownBlockView(inner, baseFontSp, palette)
                            }
                        }
                    }
                }
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.codeBg)
                    .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                if (block.language.isNotBlank()) {
                    Text(
                        text = block.language,
                        color = palette.inkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        text = block.code,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            color = palette.ink,
                            fontSize = (baseFontSp * 0.86f).sp,
                            lineHeight = (baseFontSp * 1.35f).sp
                        )
                    )
                }
            }
        }

        is MarkdownBlock.TableData -> {
            val colCount = maxOf(
                block.headers.size,
                block.rows.maxOfOrNull { it.size } ?: 0
            )
            if (colCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                    ) {
                        if (block.headers.isNotEmpty()) {
                            Row(modifier = Modifier.background(palette.surface)) {
                                for (c in 0 until colCount) {
                                    val cellInlines = block.headers.getOrElse(c) { emptyList() }
                                    val cellText = remember(cellInlines, palette, baseFontSp) {
                                        buildInlineString(cellInlines, palette, baseFontSp * 0.9f)
                                    }
                                    Text(
                                        text = cellText,
                                        style = TextStyle(
                                            color = palette.ink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = (baseFontSp * 0.9f).sp,
                                            lineHeight = (baseFontSp * 1.35f).sp
                                        ),
                                        modifier = Modifier
                                            .widthIn(min = 100.dp, max = 240.dp)
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        }
                        block.rows.forEachIndexed { rowIdx, row ->
                            Row {
                                for (c in 0 until colCount) {
                                    val cellInlines = row.getOrElse(c) { emptyList() }
                                    val cellText = remember(cellInlines, palette, baseFontSp) {
                                        buildInlineString(cellInlines, palette, baseFontSp * 0.9f)
                                    }
                                    Text(
                                        text = cellText,
                                        style = TextStyle(
                                            color = palette.ink,
                                            fontSize = (baseFontSp * 0.9f).sp,
                                            lineHeight = (baseFontSp * 1.35f).sp
                                        ),
                                        modifier = Modifier
                                            .widthIn(min = 100.dp, max = 240.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            if (rowIdx < block.rows.lastIndex) {
                                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            }
                        }
                    }
                }
            }
        }

        MarkdownBlock.Divider -> {
            HorizontalDivider(
                thickness = 1.dp,
                color = palette.hairline,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

private fun buildInlineString(
    inlines: List<MarkdownInline>,
    palette: BlakePalette,
    baseFontSp: Float
): AnnotatedString = buildAnnotatedString {
    fun appendNodes(nodes: List<MarkdownInline>) {
        for (node in nodes) {
            when (node) {
                is MarkdownInline.Plain -> append(node.text)
                is MarkdownInline.Strong -> {
                    val start = length
                    appendNodes(node.children)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                is MarkdownInline.Emphasis -> {
                    val start = length
                    appendNodes(node.children)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                is MarkdownInline.Strike -> {
                    val start = length
                    appendNodes(node.children)
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = palette.inkSecondary
                        ),
                        start,
                        length
                    )
                }
                is MarkdownInline.InlineCode -> {
                    val start = length
                    append(" ${node.code} ")
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = (baseFontSp * 0.88f).sp,
                            background = palette.codeBg,
                            color = palette.ink
                        ),
                        start,
                        length
                    )
                }
                is MarkdownInline.LinkSpan -> {
                    val safeUrl = node.destination.trim()
                    val isWebUrl = safeUrl.startsWith("https://", ignoreCase = true) ||
                        safeUrl.startsWith("http://", ignoreCase = true) ||
                        safeUrl.startsWith("mailto:", ignoreCase = true)
                    if (isWebUrl) {
                        val start = length
                        appendNodes(node.children)
                        addLink(
                            url = LinkAnnotation.Url(
                                url = safeUrl,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = palette.accent,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            ),
                            start = start,
                            end = length
                        )
                    } else {
                        val start = length
                        appendNodes(node.children)
                        addStyle(SpanStyle(color = palette.accent), start, length)
                    }
                }
                MarkdownInline.LineBreak -> append('\n')
            }
        }
    }
    appendNodes(inlines)
}
