package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.example.data.local.ScanRecordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalExportHelper {

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileBaseName: String): Result<Uri> {
        return runCatching {
            val sanitized = fileBaseName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
            val fileName = "${sanitized}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NaimQR")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create MediaStore image entry")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Failed to open gallery output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                // Legacy pre-Q fallback saved to app external pictures dir (no storage permission required)
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "NaimQR")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }

    fun createShareablePngUri(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val dir = File(context.cacheDir, "shared_codes")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun printBitmap(context: Context, bitmap: Bitmap, jobName: String) {
        val helper = PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
        }
        helper.printBitmap(jobName, bitmap)
    }

    fun exportVaultToJson(records: List<ScanRecordEntity>): String {
        val root = JSONObject()
        root.put("app", "Naim QR")
        root.put("version", 2)
        root.put("exportedAtMillis", System.currentTimeMillis())

        val array = JSONArray()
        records.forEach { rec ->
            array.put(
                JSONObject().apply {
                    put("rawValue", rec.rawValue)
                    put("formatCode", rec.formatCode)
                    put("formatLabel", rec.formatLabel)
                    put("category", rec.category)
                    put("title", rec.title)
                    put("subtitle", rec.subtitle)
                    put("isPinned", rec.isPinned)
                    put("scannedAtMillis", rec.scannedAtMillis)
                    put("note", rec.note)
                    put("dataSizeBytes", rec.dataSizeBytes)
                    put("errorCorrection", rec.errorCorrection)
                    put("processingTimeMs", rec.processingTimeMs)
                }
            )
        }
        root.put("scans", array)
        return root.toString(2)
    }

    fun importVaultFromJson(jsonText: String): List<ScanRecordEntity> {
        val trimmed = jsonText.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            obj.getJSONArray("scans")
        }
        val list = mutableListOf<ScanRecordEntity>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val rawValue = item.optString("rawValue", "")
            if (rawValue.isBlank()) continue
            list += ScanRecordEntity(
                rawValue = rawValue,
                formatCode = item.optInt("formatCode", 256),
                formatLabel = item.optString("formatLabel", "QR Code"),
                category = item.optString("category", "TEXT"),
                title = item.optString("title", rawValue.take(60)),
                subtitle = item.optString("subtitle", ""),
                isPinned = item.optBoolean("isPinned", false),
                scannedAtMillis = item.optLong("scannedAtMillis", System.currentTimeMillis()),
                note = item.optString("note", ""),
                dataSizeBytes = item.optInt("dataSizeBytes", rawValue.toByteArray(Charsets.UTF_8).size),
                errorCorrection = item.optString("errorCorrection", ""),
                processingTimeMs = item.optLong("processingTimeMs", 0L)
            )
        }
        return list
    }

    fun exportVaultToCsv(records: List<ScanRecordEntity>): String {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("id,category,format,title,subtitle,raw_value,note,bookmarked,data_size_bytes,error_correction,processing_ms,scanned_at")
        records.forEach { rec ->
            val fields = listOf(
                rec.id.toString(),
                rec.category,
                rec.formatLabel,
                rec.title,
                rec.subtitle,
                rec.rawValue,
                rec.note,
                rec.isPinned.toString(),
                rec.dataSizeBytes.toString(),
                rec.errorCorrection,
                rec.processingTimeMs.toString(),
                isoFormatter.format(Date(rec.scannedAtMillis))
            )
            sb.appendLine(fields.joinToString(",") { csvEscape(it) })
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
