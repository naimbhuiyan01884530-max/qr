package com.example.scanner

import android.content.Context
import android.net.Uri
import com.example.domain.model.ParsedBarcode
import com.example.domain.parser.OfflineBarcodeParser
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OfflineBarcodeDecoder {

    fun createAllFormatScanner(): BarcodeScanner {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        return BarcodeScanning.getClient(options)
    }

    suspend fun decodeFromImageUri(context: Context, uri: Uri): List<ParsedBarcode> {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = createAllFormatScanner()
        return try {
            val barcodes = suspendCancellableCoroutine<List<Barcode>> { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { results ->
                        if (continuation.isActive) {
                            continuation.resume(results)
                        }
                    }
                    .addOnFailureListener { e ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
            }
            barcodes.mapNotNull { barcode ->
                val raw = barcode.rawValue ?: barcode.displayValue
                if (raw.isNullOrBlank()) null
                else OfflineBarcodeParser.parse(raw, barcode.format)
            }
        } finally {
            scanner.close()
        }
    }
}
