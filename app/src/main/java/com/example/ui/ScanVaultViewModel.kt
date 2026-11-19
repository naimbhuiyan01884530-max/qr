package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScanDatabase
import com.example.data.local.ScanPreferencesRepository
import com.example.data.local.ScanRecordEntity
import com.example.data.local.ScannerPreferences
import com.example.data.repository.ScanHistoryRepository
import com.example.domain.model.ParsedBarcode
import com.example.domain.model.ScanCategory
import com.example.domain.model.VaultFilter
import com.example.domain.parser.OfflineBarcodeParser
import com.example.export.LocalExportHelper
import com.example.generator.QrEcLevel
import com.example.scanner.OfflineBarcodeDecoder
import com.example.ui.i18n.BanglaStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.NaimQrStrings
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data class AutoCopyPayload(val text: String) : UiEvent
    data object TriggerHaptic : UiEvent
}

data class ActiveScanSheet(
    val recordId: Long?,
    val parsed: ParsedBarcode,
    val scannedAtMillis: Long,
    val isPinned: Boolean,
    val note: String = ""
)

class ScanVaultViewModel(
    private val appContext: Context,
    private val historyRepository: ScanHistoryRepository,
    private val preferencesRepository: ScanPreferencesRepository
) : AndroidViewModel(appContext.applicationContext as Application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(VaultFilter.ALL)
    val selectedFilter: StateFlow<VaultFilter> = _selectedFilter.asStateFlow()

    private val _activeScanSheet = MutableStateFlow<ActiveScanSheet?>(null)
    val activeScanSheet: StateFlow<ActiveScanSheet?> = _activeScanSheet.asStateFlow()

    private val _selectedHistoryId = MutableStateFlow<Long?>(null)
    val selectedHistoryId: StateFlow<Long?> = _selectedHistoryId.asStateFlow()

    private val _isScanningPaused = MutableStateFlow(false)
    val isScanningPaused: StateFlow<Boolean> = _isScanningPaused.asStateFlow()

    private val _isDecodingImage = MutableStateFlow(false)
    val isDecodingImage: StateFlow<Boolean> = _isDecodingImage.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val preferences: StateFlow<ScannerPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScannerPreferences()
        )

    private fun currentStrings(): NaimQrStrings =
        if (preferences.value.languageCode == "bn") BanglaStrings else EnglishStrings

    val allHistory: StateFlow<List<ScanRecordEntity>> = historyRepository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<ScanRecordEntity>> = combine(
        allHistory,
        _searchQuery,
        _selectedFilter
    ) { scans, query, filter ->
        val normalizedQuery = query.trim().lowercase()
        scans.filter { item ->
            val filterMatches = when (filter) {
                VaultFilter.ALL -> true
                VaultFilter.BOOKMARKS -> item.isPinned
                VaultFilter.URL -> item.category == ScanCategory.URL.name
                VaultFilter.WIFI -> item.category == ScanCategory.WIFI.name
                VaultFilter.CONTACT -> item.category == ScanCategory.CONTACT.name
                VaultFilter.PRODUCT -> item.category == ScanCategory.PRODUCT.name
                VaultFilter.TWO_FA -> item.category == ScanCategory.TWO_FA.name
                VaultFilter.TEXT -> item.category == ScanCategory.TEXT.name
            }
            val queryMatches = normalizedQuery.isEmpty() ||
                item.title.lowercase().contains(normalizedQuery) ||
                item.subtitle.lowercase().contains(normalizedQuery) ||
                item.rawValue.lowercase().contains(normalizedQuery) ||
                item.formatLabel.lowercase().contains(normalizedQuery) ||
                item.note.lowercase().contains(normalizedQuery)
            filterMatches && queryMatches
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filterCounts: StateFlow<Map<VaultFilter, Int>> = allHistory.map { scans ->
        mapOf(
            VaultFilter.ALL to scans.size,
            VaultFilter.BOOKMARKS to scans.count { it.isPinned },
            VaultFilter.URL to scans.count { it.category == ScanCategory.URL.name },
            VaultFilter.WIFI to scans.count { it.category == ScanCategory.WIFI.name },
            VaultFilter.CONTACT to scans.count { it.category == ScanCategory.CONTACT.name },
            VaultFilter.PRODUCT to scans.count { it.category == ScanCategory.PRODUCT.name },
            VaultFilter.TWO_FA to scans.count { it.category == ScanCategory.TWO_FA.name },
            VaultFilter.TEXT to scans.count { it.category == ScanCategory.TEXT.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private var lastLiveScanRaw: String? = null
    private var lastLiveScanTime: Long = 0L

    fun onLiveBarcodeDetected(rawValue: String, formatCode: Int, processingTimeMs: Long = 1L) {
        if (_isScanningPaused.value || _activeScanSheet.value != null) return
        val now = System.currentTimeMillis()
        if (rawValue == lastLiveScanRaw && (now - lastLiveScanTime) < 2500L) return

        lastLiveScanRaw = rawValue
        lastLiveScanTime = now

        val parsed = OfflineBarcodeParser.parse(
            rawValue = rawValue,
            formatCode = formatCode,
            processingTimeMs = processingTimeMs.coerceAtLeast(1L)
        )
        val prefs = preferences.value

        viewModelScope.launch {
            val saved = historyRepository.saveScan(parsed)
            if (prefs.hapticFeedback) {
                _events.tryEmit(UiEvent.TriggerHaptic)
            }
            if (prefs.autoCopyOnScan) {
                _events.tryEmit(UiEvent.AutoCopyPayload(parsed.primaryCopyValue))
            }
            if (prefs.continuousMode) {
                _events.tryEmit(UiEvent.ShowMessage("${parsed.title}"))
            } else {
                _isScanningPaused.value = true
                _activeScanSheet.value = ActiveScanSheet(
                    recordId = saved.id,
                    parsed = parsed,
                    scannedAtMillis = saved.scannedAtMillis,
                    isPinned = saved.isPinned,
                    note = saved.note
                )
            }
        }
    }

    fun decodeImageFromGallery(uri: Uri) {
        viewModelScope.launch {
            _isDecodingImage.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    OfflineBarcodeDecoder.decodeFromImageUri(appContext, uri)
                }
                if (results.isEmpty()) {
                    _events.tryEmit(UiEvent.ShowMessage("No QR code or barcode detected in image"))
                } else {
                    val primary = results.first()
                    val saved = historyRepository.saveScan(primary, force = true)
                    if (results.size > 1) {
                        results.drop(1).forEach { extra ->
                            historyRepository.saveScan(extra, force = true)
                        }
                    }
                    val prefs = preferences.value
                    if (prefs.hapticFeedback) {
                        _events.tryEmit(UiEvent.TriggerHaptic)
                    }
                    if (prefs.autoCopyOnScan) {
                        _events.tryEmit(UiEvent.AutoCopyPayload(primary.primaryCopyValue))
                    }
                    _isScanningPaused.value = true
                    _activeScanSheet.value = ActiveScanSheet(
                        recordId = saved.id,
                        parsed = primary,
                        scannedAtMillis = saved.scannedAtMillis,
                        isPinned = saved.isPinned,
                        note = saved.note
                    )
                }
            } catch (e: Exception) {
                _events.tryEmit(UiEvent.ShowMessage("Could not read image: ${e.localizedMessage ?: "error"}"))
            } finally {
                _isDecodingImage.value = false
            }
        }
    }

    fun dismissActiveScanSheet() {
        _activeScanSheet.value = null
        _isScanningPaused.value = false
    }

    fun openHistoryRecord(record: ScanRecordEntity) {
        _selectedHistoryId.value = record.id
    }

    fun closeHistoryRecord() {
        _selectedHistoryId.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: VaultFilter) {
        _selectedFilter.value = filter
    }

    fun togglePin(id: Long, currentlyPinned: Boolean) {
        viewModelScope.launch {
            historyRepository.togglePin(id, currentlyPinned)
            _activeScanSheet.value?.let { active ->
                if (active.recordId == id) {
                    _activeScanSheet.value = active.copy(isPinned = !currentlyPinned)
                }
            }
        }
    }

    fun savePersonalNote(id: Long, note: String) {
        viewModelScope.launch {
            historyRepository.updateNote(id, note)
            _activeScanSheet.value?.let { active ->
                if (active.recordId == id) {
                    _activeScanSheet.value = active.copy(note = note.trim())
                }
            }
            _events.tryEmit(UiEvent.ShowMessage(currentStrings().noteSavedMsg))
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteScan(id)
            if (_selectedHistoryId.value == id) {
                _selectedHistoryId.value = null
            }
            if (_activeScanSheet.value?.recordId == id) {
                dismissActiveScanSheet()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            _selectedHistoryId.value = null
        }
    }

    fun saveGeneratedToHistory(
        payload: String,
        isCode128: Boolean,
        ecLevel: QrEcLevel,
        genTimeMs: Long
    ) {
        viewModelScope.launch {
            val formatCode = if (isCode128) Barcode.FORMAT_CODE_128 else Barcode.FORMAT_QR_CODE
            val ecText = if (isCode128) "Mod-103 Checksum" else "QR Level ${ecLevel.shortCode}"
            val parsed = OfflineBarcodeParser.parse(
                rawValue = payload,
                formatCode = formatCode,
                processingTimeMs = genTimeMs.coerceAtLeast(1L),
                errorCorrectionOverride = ecText
            )
            historyRepository.saveScan(parsed, force = true)
            _events.tryEmit(UiEvent.ShowMessage(currentStrings().savedToHistoryMsg))
        }
    }

    fun saveBitmapToGallery(bitmap: Bitmap, label: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                LocalExportHelper.saveBitmapToGallery(appContext, bitmap, label)
            }
            result.onSuccess {
                _events.tryEmit(UiEvent.ShowMessage(currentStrings().qrSavedToGalleryMsg))
            }.onFailure { err ->
                _events.tryEmit(UiEvent.ShowMessage(err.localizedMessage ?: "Failed to save image"))
            }
        }
    }

    fun exportJsonBackupToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { historyRepository.exportJson() }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file")
                }
            }.onSuccess {
                _events.tryEmit(UiEvent.ShowMessage(currentStrings().backupSuccess))
            }.onFailure { err ->
                _events.tryEmit(UiEvent.ShowMessage(err.localizedMessage ?: "Backup failed"))
            }
        }
    }

    fun exportCsvToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val csv = withContext(Dispatchers.IO) { historyRepository.exportCsv() }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file")
                }
            }.onSuccess {
                _events.tryEmit(UiEvent.ShowMessage(currentStrings().csvSuccess))
            }.onFailure { err ->
                _events.tryEmit(UiEvent.ShowMessage(err.localizedMessage ?: "CSV export failed"))
            }
        }
    }

    fun restoreJsonBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Unable to read backup file")
                }
                withContext(Dispatchers.IO) {
                    historyRepository.restoreFromJson(text)
                }
            }.onSuccess { restoredCount ->
                _events.tryEmit(UiEvent.ShowMessage(currentStrings().restoreSuccess(restoredCount)))
            }.onFailure {
                _events.tryEmit(UiEvent.ShowMessage(currentStrings().restoreFailed))
            }
        }
    }

    fun showMessage(text: String) {
        _events.tryEmit(UiEvent.ShowMessage(text))
    }

    fun setAutoCopy(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCopy(enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticFeedback(enabled) }
    }

    fun setContinuousMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setContinuousMode(enabled) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkTheme(enabled) }
    }

    fun toggleLanguage() {
        viewModelScope.launch { preferencesRepository.toggleLanguage() }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val db = ScanDatabase.getInstance(appContext)
                val historyRepo = ScanHistoryRepository(db.scanRecordDao())
                val prefsRepo = ScanPreferencesRepository(appContext)
                return ScanVaultViewModel(appContext, historyRepo, prefsRepo) as T
            }
        }
    }
}
