package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ScanRecordEntity
import com.example.domain.model.VaultFilter
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.FormatTagPill
import com.example.ui.components.ScanResultInspector
import com.example.ui.components.ThemeAndLanguageActions
import com.example.ui.components.accentColor
import com.example.ui.components.parsedCategory
import com.example.ui.components.toParsedBarcode
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.NaimQrStrings
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    scans: List<ScanRecordEntity>,
    totalCount: Int,
    filterCounts: Map<VaultFilter, Int>,
    searchQuery: String,
    selectedFilter: VaultFilter,
    selectedRecordId: Long?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (VaultFilter) -> Unit,
    onSelectRecord: (ScanRecordEntity) -> Unit,
    onCloseRecord: () -> Unit,
    onCopyQuick: (ScanRecordEntity) -> Unit,
    onCopyText: (String, String) -> Unit,
    onSaveImageToGallery: (Bitmap, String) -> Unit,
    onSaveNote: (Long, String) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    onExportJsonBackup: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onRestoreJsonBackup: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showClearDialog by remember { mutableStateOf(false) }
    val selectedRecord = remember(scans, selectedRecordId) {
        scans.firstOrNull { it.id == selectedRecordId }
    }

    val jsonBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportJsonBackup(uri) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportCsv(uri) }

    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onRestoreJsonBackup(uri) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        val isTwoPane = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(if (isTwoPane) 1.15f else 1f)
                    .fillMaxHeight()
            ) {
                // Header + Top Bar Theme & Language Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.vaultTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.vaultSubtitleCount(totalCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ThemeAndLanguageActions(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onToggleLanguage = onToggleLanguage
                    )
                }

                // Local Backup / Export / Restore / Clear Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        enabled = totalCount > 0,
                        onClick = { jsonBackupLauncher.launch("naim_qr_backup_${System.currentTimeMillis()}.json") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_backup_json_button")
                    ) {
                        Icon(Icons.Rounded.DataObject, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.backupJson, style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        enabled = totalCount > 0,
                        onClick = { csvExportLauncher.launch("naim_qr_export_${System.currentTimeMillis()}.csv") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_export_csv_button")
                    ) {
                        Icon(Icons.Rounded.TableChart, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportCsv, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { jsonRestoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_restore_json_button")
                    ) {
                        Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.restoreJson, style = MaterialTheme.typography.labelMedium)
                    }

                    if (totalCount > 0) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_all_history_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.clearAll)
                        }
                    }
                }

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = strings.searchPlaceholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .testTag("history_search_input")
                )

                // Vault Filter Chips (All, Bookmarks, Links, Wi-Fi, Contacts, Barcodes, 2FA, Text)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VaultFilter.entries.forEach { filter ->
                        val count = filterCounts[filter] ?: 0
                        val selected = selectedFilter == filter
                        val label = filterLabel(filter, strings)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onFilterSelected(
                                    if (selected && filter != VaultFilter.ALL) VaultFilter.ALL else filter
                                )
                            },
                            label = {
                                Text(if (count > 0 || filter == VaultFilter.ALL) "$label ($count)" else label)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }

                // History List or Empty State
                if (scans.isEmpty()) {
                    EmptyHistoryState(
                        isFiltered = searchQuery.isNotBlank() || selectedFilter != VaultFilter.ALL,
                        onResetFilters = {
                            onSearchQueryChange("")
                            onFilterSelected(VaultFilter.ALL)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("history_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(scans, key = { it.id }) { record ->
                            HistoryItemCard(
                                record = record,
                                isSelected = isTwoPane && record.id == selectedRecord?.id,
                                onClick = { onSelectRecord(record) },
                                onQuickCopy = { onCopyQuick(record) },
                                onDelete = { onDeleteRecord(record.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }

            // Expanded Tablet/Foldable Side Detail Pane
            if (isTwoPane) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (selectedRecord != null) {
                        val parsed = remember(selectedRecord.id, selectedRecord.rawValue) {
                            selectedRecord.toParsedBarcode()
                        }
                        ScanResultInspector(
                            parsed = parsed,
                            scannedAtMillis = selectedRecord.scannedAtMillis,
                            isPinned = selectedRecord.isPinned,
                            initialNote = selectedRecord.note,
                            recordId = selectedRecord.id,
                            onCopyText = onCopyText,
                            onSaveImageToGallery = onSaveImageToGallery,
                            onSaveNote = onSaveNote,
                            onTogglePin = { onTogglePin(selectedRecord.id, selectedRecord.isPinned) },
                            onDelete = { onDeleteRecord(selectedRecord.id) }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.selectItemHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!isTwoPane && selectedRecord != null) {
            BackHandler { onCloseRecord() }
            val parsed = remember(selectedRecord.id, selectedRecord.rawValue) {
                selectedRecord.toParsedBarcode()
            }
            ModalBottomSheet(onDismissRequest = onCloseRecord) {
                ScanResultInspector(
                    parsed = parsed,
                    scannedAtMillis = selectedRecord.scannedAtMillis,
                    isPinned = selectedRecord.isPinned,
                    initialNote = selectedRecord.note,
                    recordId = selectedRecord.id,
                    onCopyText = onCopyText,
                    onSaveImageToGallery = onSaveImageToGallery,
                    onSaveNote = onSaveNote,
                    onTogglePin = { onTogglePin(selectedRecord.id, selectedRecord.isPinned) },
                    onDelete = { onDeleteRecord(selectedRecord.id) }
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(strings.clearDialogTitle) },
            text = { Text(strings.clearDialogBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text(strings.confirmDeleteAll)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

private fun filterLabel(filter: VaultFilter, strings: NaimQrStrings): String = when (filter) {
    VaultFilter.ALL -> strings.catAll
    VaultFilter.BOOKMARKS -> strings.catBookmarks
    VaultFilter.URL -> strings.catLinks
    VaultFilter.WIFI -> strings.catWifi
    VaultFilter.CONTACT -> strings.catContacts
    VaultFilter.PRODUCT -> strings.catBarcodes
    VaultFilter.TWO_FA -> strings.catTwoFa
    VaultFilter.TEXT -> strings.catText
}

@Composable
private fun HistoryItemCard(
    record: ScanRecordEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQuickCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val category = remember(record.category) { record.parsedCategory() }
    val accent = category.accentColor()
    val timestamp = remember(record.scannedAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.scannedAtMillis))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .testTag("history_item_${record.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryIconBadge(category = category)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (record.isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Bookmarked",
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = record.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (record.note.isNotBlank()) {
                    Text(
                        text = "📝 ${record.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormatTagPill(label = record.formatLabel, accentColor = accent)
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onQuickCopy,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_copy_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy ${record.title}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_delete_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${record.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(
    isFiltered: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = if (isFiltered) strings.emptyFilterTitle else strings.emptyVaultTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isFiltered) strings.emptyFilterBody else strings.emptyVaultBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (isFiltered) {
                TextButton(
                    onClick = onResetFilters,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(strings.resetFilters)
                }
            }
        }
    }
}
