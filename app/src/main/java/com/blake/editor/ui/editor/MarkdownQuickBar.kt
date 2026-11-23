package com.blake.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.markdown.MarkdownAction
import com.blake.editor.ui.theme.LocalBlakePalette

/**
 * Subtle, hideable formatting strip docked right above the keyboard.
 * Gives fast one-tap Undo/Redo and Markdown markers without 90s button clutter.
 */
@Composable
fun MarkdownQuickBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormatAction: (MarkdownAction) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlakePalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                QuickBarIcon(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Undo",
                    enabled = canUndo,
                    onClick = onUndo
                )
                QuickBarIcon(
                    icon = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = "Redo",
                    enabled = canRedo,
                    onClick = onRedo
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(palette.hairline)
                )

                QuickBarGlyph(
                    text = "H",
                    fontWeight = FontWeight.Bold,
                    contentDescription = "Heading",
                    onClick = { onFormatAction(MarkdownAction.HEADING) }
                )
                QuickBarGlyph(
                    text = "B",
                    fontWeight = FontWeight.ExtraBold,
                    contentDescription = "Bold",
                    onClick = { onFormatAction(MarkdownAction.BOLD) }
                )
                QuickBarGlyph(
                    text = "I",
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    contentDescription = "Italic",
                    onClick = { onFormatAction(MarkdownAction.ITALIC) }
                )
                QuickBarIcon(
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = "Bullet list",
                    onClick = { onFormatAction(MarkdownAction.BULLET_LIST) }
                )
                QuickBarIcon(
                    icon = Icons.Rounded.CheckBox,
                    contentDescription = "Checklist",
                    onClick = { onFormatAction(MarkdownAction.CHECKLIST) }
                )
                QuickBarIcon(
                    icon = Icons.Rounded.FormatQuote,
                    contentDescription = "Quote",
                    onClick = { onFormatAction(MarkdownAction.QUOTE) }
                )
                QuickBarIcon(
                    icon = Icons.Rounded.Link,
                    contentDescription = "Link",
                    onClick = { onFormatAction(MarkdownAction.LINK) }
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(palette.hairline)
            )

            QuickBarIcon(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Hide formatting bar",
                onClick = onHide
            )
        }
    }
}

@Composable
private fun QuickBarIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = LocalBlakePalette.current
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) palette.ink else palette.inkMuted.copy(alpha = 0.45f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun QuickBarGlyph(
    text: String,
    fontWeight: FontWeight,
    fontStyle: FontStyle = FontStyle.Normal,
    contentDescription: String,
    onClick: () -> Unit
) {
    val palette = LocalBlakePalette.current
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = palette.ink,
            fontSize = 15.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle
        )
    }
}
