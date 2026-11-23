package com.blake.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.ui.theme.LocalBlakePalette

/**
 * Slim, non-blocking inline Find & Replace bar docked directly below the top bar.
 * Shrinks the editor viewport cleanly instead of floating over document text.
 */
@Composable
fun FindReplaceBar(
    query: String,
    replacement: String,
    showReplace: Boolean,
    matchCase: Boolean,
    activeMatchIndex: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleReplace: () -> Unit,
    onToggleMatchCase: () -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlakePalette.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleReplace,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (showReplace) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (showReplace) "Hide Replace" else "Show Replace",
                    tint = palette.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Slim pill search input
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.canvas)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Find…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkMuted
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = palette.ink,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val counterText = if (totalMatches == 0) {
                        "0/0"
                    } else {
                        "${activeMatchIndex + 1}/$totalMatches"
                    }
                    Text(
                        text = counterText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (totalMatches == 0) palette.danger else palette.inkSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Case sensitivity pill (Aa)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (matchCase) palette.accentSoft else palette.surface)
                    .clickable(onClick = onToggleMatchCase),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    fontSize = 13.sp,
                    fontWeight = if (matchCase) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (matchCase) palette.accent else palette.inkSecondary
                )
            }

            IconButton(
                onClick = onPreviousMatch,
                enabled = totalMatches > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    tint = if (totalMatches > 0) palette.ink else palette.inkMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onNextMatch,
                enabled = totalMatches > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Next match",
                    tint = if (totalMatches > 0) palette.ink else palette.inkMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close find",
                    tint = palette.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showReplace,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.canvas)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (replacement.isEmpty()) {
                        Text(
                            text = "Replace with…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkMuted
                        )
                    }
                    BasicTextField(
                        value = replacement,
                        onValueChange = onReplacementChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = palette.ink,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (totalMatches > 0) onReplaceCurrent()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val canReplace = query.isNotEmpty() && totalMatches > 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (canReplace) palette.accentSoft else palette.surfaceVariant,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canReplace, onClick = onReplaceCurrent)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Replace",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (canReplace) palette.accent else palette.inkMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (canReplace) palette.surfaceVariant else palette.surface,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canReplace, onClick = onReplaceAll)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (canReplace) palette.ink else palette.inkMuted
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
    }
}
