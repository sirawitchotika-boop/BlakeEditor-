package com.blake.editor.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.file.SupportedFileTypes
import com.blake.editor.model.RecentFile
import com.blake.editor.model.ScratchDraft
import com.blake.editor.ui.theme.LocalBlakePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsSheet(
    activeDraftId: String,
    drafts: List<ScratchDraft>,
    recentFiles: List<RecentFile>,
    onDismiss: () -> Unit,
    onNewNote: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onSelectDraft: (ScratchDraft) -> Unit,
    onDeleteDraft: (ScratchDraft) -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onRemoveRecent: (RecentFile) -> Unit
) {
    val palette = LocalBlakePalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.canvas,
        contentColor = palette.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Documents",
                style = MaterialTheme.typography.titleLarge,
                color = palette.ink
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary actions: New Note & Open from phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.accent,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            onNewNote()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Note",
                            style = MaterialTheme.typography.labelLarge,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.surface,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            onOpenFilePicker()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = palette.ink,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open File",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (drafts.isNotEmpty()) {
                    item {
                        SectionHeading("DRAFTS & RECOVERY")
                    }
                    items(drafts, key = { "draft_${it.id}" }) { draft ->
                        val isCurrent = draft.id == activeDraftId
                        DocumentRow(
                            title = draft.title,
                            subtitle = buildString {
                                if (draft.uriString != null) append("Linked file") else append("Local draft")
                                if (draft.isDirty) append(" · Unsaved")
                            },
                            badge = if (SupportedFileTypes.isMarkdownFile(draft.title, draft.mimeType)) "MD" else "TXT",
                            isCurrent = isCurrent,
                            actionIcon = Icons.Rounded.DeleteOutline,
                            actionDescription = "Delete draft",
                            onClick = {
                                onDismiss()
                                onSelectDraft(draft)
                            },
                            onAction = { onDeleteDraft(draft) }
                        )
                    }
                }

                if (recentFiles.isNotEmpty()) {
                    item {
                        if (drafts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = palette.hairline)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        SectionHeading("RECENT FILES")
                    }
                    items(recentFiles, key = { "recent_${it.uriString}" }) { recent ->
                        DocumentRow(
                            title = recent.title,
                            subtitle = "External storage",
                            badge = if (SupportedFileTypes.isMarkdownFile(recent.title, recent.mimeType)) "MD" else "TXT",
                            isCurrent = false,
                            actionIcon = Icons.Rounded.Close,
                            actionDescription = "Remove from recent",
                            onClick = {
                                onDismiss()
                                onOpenRecent(recent)
                            },
                            onAction = { onRemoveRecent(recent) }
                        )
                    }
                }

                if (drafts.isEmpty() && recentFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your drafts and recent files will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    val palette = LocalBlakePalette.current
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = palette.inkMuted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun DocumentRow(
    title: String,
    subtitle: String,
    badge: String,
    isCurrent: Boolean,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    val palette = LocalBlakePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) palette.accentSoft.copy(alpha = 0.65f) else palette.canvas)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = if (isCurrent) palette.accent else palette.inkSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surfaceVariant)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkSecondary
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSecondary
            )
        }
        IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionDescription,
                tint = palette.inkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
