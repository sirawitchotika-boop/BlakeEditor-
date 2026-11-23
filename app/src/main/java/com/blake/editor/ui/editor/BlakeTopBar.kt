package com.blake.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blake.editor.ui.theme.LocalBlakePalette

@Composable
fun BlakeTopBar(
    title: String,
    isDirty: Boolean,
    isExternal: Boolean,
    isReadMode: Boolean,
    isFindOpen: Boolean,
    showMarkdownBar: Boolean,
    onOpenDocuments: () -> Unit,
    onRenameDraft: () -> Unit,
    onToggleReadMode: () -> Unit,
    onSave: () -> Unit,
    onToggleFind: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onToggleMarkdownBar: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlakePalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.canvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDocuments) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = "Documents",
                    tint = palette.ink
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isExternal, onClick = onRenameDraft)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDirty) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                }
            }

            IconButton(onClick = onToggleReadMode) {
                Icon(
                    imageVector = if (isReadMode) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = if (isReadMode) "Edit document" else "Read Markdown",
                    tint = if (isReadMode) palette.accent else palette.ink
                )
            }

            if (!isReadMode) {
                IconButton(onClick = onToggleFind) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Find and replace",
                        tint = if (isFindOpen) palette.accent else palette.ink
                    )
                }
            }

            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = "Save",
                    tint = if (isDirty || !isExternal) palette.accent else palette.inkSecondary
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = palette.ink
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = palette.surface
                ) {
                    DropdownMenuItem(
                        text = { Text("Save As…", color = palette.ink) },
                        leadingIcon = {
                            Icon(Icons.Rounded.SaveAs, contentDescription = null, tint = palette.inkSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onSaveAs()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Text", color = palette.ink) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Share, contentDescription = null, tint = palette.inkSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    if (!isReadMode) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showMarkdownBar) "Hide Formatting Bar" else "Show Formatting Bar",
                                    color = palette.ink
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.ViewAgenda, contentDescription = null, tint = palette.inkSecondary)
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleMarkdownBar()
                            }
                        )
                    }
                    HorizontalDivider(color = palette.hairline)
                    DropdownMenuItem(
                        text = { Text("Appearance & Settings", color = palette.ink) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Settings, contentDescription = null, tint = palette.inkSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
    }
}
