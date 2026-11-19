package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.parser.OfflineBarcodeParser
import com.example.ui.components.MultiScanResultsSheet
import com.example.ui.components.ScanResultInspector
import com.example.ui.i18n.BanglaStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.ScanVaultTheme
import kotlinx.coroutines.launch

const val ROUTE_SCAN = "scan"
const val ROUTE_HISTORY = "history"
const val ROUTE_GENERATE = "generate"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanVaultApp(
    viewModel: ScanVaultViewModel = viewModel(factory = ScanVaultViewModel.factory(LocalContext.current))
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val filteredHistory by viewModel.filteredHistory.collectAsStateWithLifecycle()
    val filterCounts by viewModel.filterCounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedHistoryId by viewModel.selectedHistoryId.collectAsStateWithLifecycle()
    val activeScanSheet by viewModel.activeScanSheet.collectAsStateWithLifecycle()
    val multiScanBatch by viewModel.multiScanBatch.collectAsStateWithLifecycle()
    val isMultiScanSheetOpen by viewModel.isMultiScanSheetOpen.collectAsStateWithLifecycle()
    val isScanningPaused by viewModel.isScanningPaused.collectAsStateWithLifecycle()
    val isDecodingImage by viewModel.isDecodingImage.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val appStrings = remember(preferences.languageCode) {
        if (preferences.languageCode == "bn") BanglaStrings else EnglishStrings
    }

    ScanVaultTheme(darkTheme = preferences.isDarkTheme) {
        CompositionLocalProvider(LocalAppStrings provides appStrings) {
            val copyToClipboard: (String, String) -> Unit = remember(context, appStrings) {
                { label, value ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(appStrings.copiedMsg(label))
                    }
                }
            }

            val copyAllMultiScan: () -> Unit = remember(context, appStrings, multiScanBatch) {
                {
                    if (multiScanBatch.isNotEmpty()) {
                        val combined = multiScanBatch.joinToString(separator = "\n") {
                            it.parsed.primaryCopyValue
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Naim QR Multi", combined))
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(appStrings.multiScanCopiedAllMsg(multiScanBatch.size))
                        }
                    }
                }
            }

            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.ShowMessage -> {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(event.text)
                        }
                        is UiEvent.AutoCopyPayload -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Naim", event.text))
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(appStrings.actionCopied)
                        }
                        UiEvent.TriggerHaptic -> triggerScanVibration(context)
                    }
                }
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_SCAN

            val navigateToTab: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_SCAN,
                                    onClick = { navigateToTab(ROUTE_SCAN) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_SCAN) {
                                                Icons.Rounded.QrCodeScanner
                                            } else {
                                                Icons.Outlined.QrCodeScanner
                                            },
                                            contentDescription = appStrings.tabScan
                                        )
                                    },
                                    label = { Text(appStrings.tabScan) },
                                    modifier = Modifier.testTag("nav_tab_scan")
                                )
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_HISTORY,
                                    onClick = { navigateToTab(ROUTE_HISTORY) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (allHistory.isNotEmpty()) {
                                                    Badge {
                                                        Text(
                                                            text = if (allHistory.size > 99) "99+" else allHistory.size.toString()
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (currentRoute == ROUTE_HISTORY) {
                                                    Icons.Rounded.History
                                                } else {
                                                    Icons.Outlined.History
                                                },
                                                contentDescription = appStrings.tabHistory
                                            )
                                        }
                                    },
                                    label = { Text(appStrings.tabHistory) },
                                    modifier = Modifier.testTag("nav_tab_history")
                                )
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_GENERATE,
                                    onClick = { navigateToTab(ROUTE_GENERATE) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_GENERATE) {
                                                Icons.Rounded.QrCode2
                                            } else {
                                                Icons.Outlined.QrCode2
                                            },
                                            contentDescription = appStrings.tabGenerate
                                        )
                                    },
                                    label = { Text(appStrings.tabGenerate) },
                                    modifier = Modifier.testTag("nav_tab_generate")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationRailItem(
                                    selected = currentRoute == ROUTE_SCAN,
                                    onClick = { navigateToTab(ROUTE_SCAN) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_SCAN) {
                                                Icons.Rounded.QrCodeScanner
                                            } else {
                                                Icons.Outlined.QrCodeScanner
                                            },
                                            contentDescription = appStrings.tabScan
                                        )
                                    },
                                    label = { Text(appStrings.tabScan) },
                                    modifier = Modifier.testTag("nav_rail_scan")
                                )
                                NavigationRailItem(
                                    selected = currentRoute == ROUTE_HISTORY,
                                    onClick = { navigateToTab(ROUTE_HISTORY) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (allHistory.isNotEmpty()) {
                                                    Badge {
                                                        Text(
                                                            text = if (allHistory.size > 99) "99+" else allHistory.size.toString()
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (currentRoute == ROUTE_HISTORY) {
                                                    Icons.Rounded.History
                                                } else {
                                                    Icons.Outlined.History
                                                },
                                                contentDescription = appStrings.tabHistory
                                            )
                                        }
                                    },
                                    label = { Text(appStrings.tabHistory) },
                                    modifier = Modifier.testTag("nav_rail_history")
                                )
                                NavigationRailItem(
                                    selected = currentRoute == ROUTE_GENERATE,
                                    onClick = { navigateToTab(ROUTE_GENERATE) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_GENERATE) {
                                                Icons.Rounded.QrCode2
                                            } else {
                                                Icons.Outlined.QrCode2
                                            },
                                            contentDescription = appStrings.tabGenerate
                                        )
                                    },
                                    label = { Text(appStrings.tabGenerate) },
                                    modifier = Modifier.testTag("nav_rail_generate")
                                )
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_SCAN,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_SCAN) {
                                ScannerScreen(
                                    isScanningPaused = isScanningPaused,
                                    isDecodingImage = isDecodingImage,
                                    preferences = preferences,
                                    multiScanBatch = multiScanBatch,
                                    onBarcodesDetected = viewModel::onLiveBarcodesDetected,
                                    onImageSelected = viewModel::decodeImageFromGallery,
                                    onOpenMultiScanSheet = viewModel::openMultiScanSheet,
                                    onCopyAllMultiScan = copyAllMultiScan,
                                    onClearMultiScanBatch = viewModel::clearMultiScanBatch,
                                    onToggleAutoCopy = viewModel::setAutoCopy,
                                    onToggleHaptic = viewModel::setHapticFeedback,
                                    onToggleContinuous = viewModel::setContinuousMode,
                                    onToggleTheme = { viewModel.setDarkTheme(!preferences.isDarkTheme) },
                                    onToggleLanguage = viewModel::toggleLanguage
                                )
                            }

                            composable(ROUTE_HISTORY) {
                                HistoryScreen(
                                    scans = filteredHistory,
                                    totalCount = allHistory.size,
                                    filterCounts = filterCounts,
                                    searchQuery = searchQuery,
                                    selectedFilter = selectedFilter,
                                    selectedRecordId = selectedHistoryId,
                                    isDarkTheme = preferences.isDarkTheme,
                                    onToggleTheme = { viewModel.setDarkTheme(!preferences.isDarkTheme) },
                                    onToggleLanguage = viewModel::toggleLanguage,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onFilterSelected = viewModel::selectFilter,
                                    onSelectRecord = viewModel::openHistoryRecord,
                                    onCloseRecord = viewModel::closeHistoryRecord,
                                    onCopyQuick = { record ->
                                        val parsed = OfflineBarcodeParser.parse(record.rawValue, record.formatCode)
                                        copyToClipboard(parsed.title, parsed.primaryCopyValue)
                                    },
                                    onCopyText = copyToClipboard,
                                    onSaveImageToGallery = viewModel::saveBitmapToGallery,
                                    onSaveNote = viewModel::savePersonalNote,
                                    onTogglePin = viewModel::togglePin,
                                    onDeleteRecord = viewModel::deleteRecord,
                                    onClearAll = viewModel::clearAllHistory,
                                    onExportJsonBackup = viewModel::exportJsonBackupToUri,
                                    onExportCsv = viewModel::exportCsvToUri,
                                    onRestoreJsonBackup = viewModel::restoreJsonBackupFromUri
                                )
                            }

                            composable(ROUTE_GENERATE) {
                                GeneratorScreen(
                                    isDarkTheme = preferences.isDarkTheme,
                                    onToggleTheme = { viewModel.setDarkTheme(!preferences.isDarkTheme) },
                                    onToggleLanguage = viewModel::toggleLanguage,
                                    onSavePngToGallery = viewModel::saveBitmapToGallery,
                                    onSaveToHistory = viewModel::saveGeneratedToHistory,
                                    onShowMessage = viewModel::showMessage
                                )
                            }
                        }
                    }
                }
            }

            if (isMultiScanSheetOpen && multiScanBatch.isNotEmpty()) {
                BackHandler { viewModel.dismissMultiScanSheet() }
                ModalBottomSheet(onDismissRequest = viewModel::dismissMultiScanSheet) {
                    MultiScanResultsSheet(
                        items = multiScanBatch,
                        onCopyAll = copyAllMultiScan,
                        onCopySingle = { item ->
                            copyToClipboard(item.parsed.title, item.parsed.primaryCopyValue)
                        },
                        onInspectItem = viewModel::inspectMultiScanItem,
                        onRemoveItem = viewModel::removeMultiScanItem,
                        onClearBatch = viewModel::clearMultiScanBatch,
                        onResumeScan = viewModel::dismissMultiScanSheet
                    )
                }
            }

            activeScanSheet?.let { sheet ->
                BackHandler { viewModel.dismissActiveScanSheet() }
                ModalBottomSheet(onDismissRequest = viewModel::dismissActiveScanSheet) {
                    ScanResultInspector(
                        parsed = sheet.parsed,
                        scannedAtMillis = sheet.scannedAtMillis,
                        isPinned = sheet.isPinned,
                        initialNote = sheet.note,
                        recordId = sheet.recordId,
                        onCopyText = copyToClipboard,
                        onSaveImageToGallery = viewModel::saveBitmapToGallery,
                        onSaveNote = viewModel::savePersonalNote,
                        onTogglePin = sheet.recordId?.let { id ->
                            { viewModel.togglePin(id, sheet.isPinned) }
                        },
                        onDelete = sheet.recordId?.let { id ->
                            { viewModel.deleteRecord(id) }
                        },
                        onClose = viewModel::dismissActiveScanSheet
                    )
                }
            }
        }
    }
}

private fun triggerScanVibration(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45L)
            }
        }
    }
}
