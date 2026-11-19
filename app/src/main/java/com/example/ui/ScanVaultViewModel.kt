package com.example.ui

import android.app.Application
import android.content.Context
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
import com.example.domain.parser.OfflineBarcodeParser
import com.example.scanner.OfflineBarcodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val isPinned: Boolean
)

class ScanVaultViewModel(
    private val appContext: Context,
    private val historyRepository: ScanHistoryRepository,
    private val preferencesRepository: ScanPreferencesRepository
) : AndroidViewModel(appContext.applicationContext as Application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ScanCategory?>(null)
    val selectedCategory: StateFlow<ScanCategory?> = _selectedCategory.asStateFlow()

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

    val allHistory: StateFlow<List<ScanRecordEntity>> = historyRepository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<ScanRecordEntity>> = combine(
        allHistory,
        _searchQuery,
        _selectedCategory
    ) { scans, query, category ->
        val normalizedQuery = query.trim().lowercase()
        scans.filter { item ->
            val categoryMatches = category == null || item.category == category.name
            val queryMatches = normalizedQuery.isEmpty() ||
                item.title.lowercase().contains(normalizedQuery) ||
                item.subtitle.lowercase().contains(normalizedQuery) ||
                item.rawValue.lowercase().contains(normalizedQuery) ||
                item.formatLabel.lowercase().contains(normalizedQuery)
            categoryMatches && queryMatches
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categoryCounts: StateFlow<Map<ScanCategory, Int>> = allHistory.combine(_searchQuery) { scans, _ ->
        ScanCategory.entries.associateWith { cat ->
            scans.count { it.category == cat.name }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private var lastLiveScanRaw: String? = null
    private var lastLiveScanTime: Long = 0L

    fun onLiveBarcodeDetected(rawValue: String, formatCode: Int) {
        if (_isScanningPaused.value || _activeScanSheet.value != null) return
        val now = System.currentTimeMillis()
        if (rawValue == lastLiveScanRaw && (now - lastLiveScanTime) < 2500L) return

        lastLiveScanRaw = rawValue
        lastLiveScanTime = now

        val parsed = OfflineBarcodeParser.parse(rawValue, formatCode)
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
                _events.tryEmit(UiEvent.ShowMessage("Saved: ${parsed.title}"))
            } else {
                _isScanningPaused.value = true
                _activeScanSheet.value = ActiveScanSheet(
                    recordId = saved.id,
                    parsed = parsed,
                    scannedAtMillis = saved.scannedAtMillis,
                    isPinned = saved.isPinned
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
                    // Save any extra barcodes found in the same image silently to history
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
                        isPinned = saved.isPinned
                    )
                    if (results.size > 1) {
                        _events.tryEmit(
                            UiEvent.ShowMessage("Found ${results.size} codes in image — all saved to history")
                        )
                    }
                }
            } catch (e: Exception) {
                _events.tryEmit(UiEvent.ShowMessage("Could not read image: ${e.localizedMessage ?: "unknown error"}"))
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

    fun selectCategoryFilter(category: ScanCategory?) {
        _selectedCategory.value = category
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

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteScan(id)
            if (_selectedHistoryId.value == id) {
                _selectedHistoryId.value = null
            }
            if (_activeScanSheet.value?.recordId == id) {
                dismissActiveScanSheet()
            }
            _events.tryEmit(UiEvent.ShowMessage("Scan deleted"))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            _selectedHistoryId.value = null
            _events.tryEmit(UiEvent.ShowMessage("All local scan history cleared"))
        }
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
