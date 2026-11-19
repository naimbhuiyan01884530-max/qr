package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rawValue: String,
    val formatCode: Int,
    val formatLabel: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val isPinned: Boolean = false,
    val scannedAtMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val dataSizeBytes: Int = 0,
    val errorCorrection: String = "",
    val processingTimeMs: Long = 0L
)
