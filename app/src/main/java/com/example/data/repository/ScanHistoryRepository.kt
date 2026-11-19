package com.example.data.repository

import com.example.data.local.ScanRecordDao
import com.example.data.local.ScanRecordEntity
import com.example.domain.model.ParsedBarcode
import com.example.export.LocalExportHelper
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanRecordDao) {

    val allScans: Flow<List<ScanRecordEntity>> = dao.observeAllScans()

    @Volatile
    private var lastSavedRaw: String? = null

    @Volatile
    private var lastSavedAtMillis: Long = 0L

    suspend fun saveScan(parsed: ParsedBarcode, force: Boolean = false): ScanRecordEntity {
        val now = System.currentTimeMillis()
        if (!force && lastSavedRaw == parsed.rawValue && (now - lastSavedAtMillis) < 2500L) {
            return ScanRecordEntity(
                rawValue = parsed.rawValue,
                formatCode = parsed.formatCode,
                formatLabel = parsed.formatLabel,
                category = parsed.category.name,
                title = parsed.title,
                subtitle = parsed.subtitle,
                scannedAtMillis = lastSavedAtMillis,
                dataSizeBytes = parsed.dataSizeBytes,
                errorCorrection = parsed.errorCorrection,
                processingTimeMs = parsed.processingTimeMs
            )
        }
        val entity = ScanRecordEntity(
            rawValue = parsed.rawValue,
            formatCode = parsed.formatCode,
            formatLabel = parsed.formatLabel,
            category = parsed.category.name,
            title = parsed.title,
            subtitle = parsed.subtitle,
            scannedAtMillis = now,
            dataSizeBytes = parsed.dataSizeBytes,
            errorCorrection = parsed.errorCorrection,
            processingTimeMs = parsed.processingTimeMs
        )
        val insertedId = dao.insertScan(entity)
        lastSavedRaw = parsed.rawValue
        lastSavedAtMillis = now
        return entity.copy(id = insertedId)
    }

    suspend fun togglePin(id: Long, currentlyPinned: Boolean) {
        dao.updatePinned(id, !currentlyPinned)
    }

    suspend fun updateNote(id: Long, note: String) {
        dao.updateNote(id, note.trim())
    }

    suspend fun deleteScan(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        lastSavedRaw = null
        lastSavedAtMillis = 0L
        dao.clearAll()
    }

    suspend fun exportJson(): String {
        return LocalExportHelper.exportVaultToJson(dao.getAllSnapshot())
    }

    suspend fun exportCsv(): String {
        return LocalExportHelper.exportVaultToCsv(dao.getAllSnapshot())
    }

    suspend fun restoreFromJson(jsonText: String): Int {
        val incoming = LocalExportHelper.importVaultFromJson(jsonText)
        if (incoming.isEmpty()) return 0
        val existingKeys = dao.getAllSnapshot()
            .map { "${it.rawValue}::${it.scannedAtMillis}" }
            .toHashSet()
        val toInsert = incoming.filter { "${it.rawValue}::${it.scannedAtMillis}" !in existingKeys }
        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert)
        }
        return toInsert.size
    }
}
