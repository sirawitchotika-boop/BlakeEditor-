package com.blake.editor.ui.editor

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blake.editor.model.AppThemeMode
import com.blake.editor.model.EditorFontSize
import com.blake.editor.ui.theme.LocalBlakePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: AppThemeMode,
    fontSize: EditorFontSize,
    showMarkdownBar: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectFontSize: (EditorFontSize) -> Unit,
    onToggleMarkdownBar: (Boolean) -> Unit,
    onDismiss: () -> Unit
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
                .padding(bottom = 30.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                color = palette.ink
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "THEME",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = palette.inkMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (mode in AppThemeMode.entries) {
                    val selected = mode == themeMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) palette.accentSoft else palette.surface)
                            .border(
                                width = 1.dp,
                                color = if (selected) palette.accent else palette.hairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectTheme(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mode) {
                                AppThemeMode.SYSTEM -> "System"
                                AppThemeMode.PAPER_LIGHT -> "Paper"
                                AppThemeMode.CHARCOAL_DARK -> "Charcoal"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) palette.accent else palette.ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TEXT SIZE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = palette.inkMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (size in EditorFontSize.entries) {
                    val selected = size == fontSize
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) palette.accentSoft else palette.surface)
                            .border(
                                width = 1.dp,
                                color = if (selected) palette.accent else palette.hairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectFontSize(size) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (size) {
                                EditorFontSize.SMALL -> "S"
                                EditorFontSize.MEDIUM -> "M"
                                EditorFontSize.LARGE -> "L"
                                EditorFontSize.XLARGE -> "XL"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) palette.accent else palette.ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = palette.hairline)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Markdown formatting bar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.ink
                    )
                    Text(
                        text = "Quick buttons above the keyboard while writing",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.inkSecondary
                    )
                }
                Switch(
                    checked = showMarkdownBar,
                    onCheckedChange = onToggleMarkdownBar,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = palette.accent
                    )
                )
            }
        }
    }
}
