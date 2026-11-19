package com.example.generator

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter

data class BarcodeGenerationRequest(
    val payload: String,
    val format: BarcodeFormat,
    val sizePreset: CodeSize,
    val palette: CodePalette = CodePalette.CLASSIC_WHITE,
    val errorCorrection: QrEcLevel = QrEcLevel.M
)

data class GeneratedBarcodeResult(
    val bitmap: Bitmap,
    val format: BarcodeFormat,
    val widthPx: Int,
    val heightPx: Int,
    val errorCorrection: QrEcLevel,
    val generationTimeMs: Long
)

interface BarcodeGeneratorService {
    fun validatePayload(payload: String, format: BarcodeFormat): String?
    fun generate(request: BarcodeGenerationRequest): GeneratedBarcodeResult
    fun encodeMatrix(
        payload: String,
        format: BarcodeFormat,
        widthPx: Int,
        heightPx: Int,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): BitMatrix

    fun generateBitmap(
        payload: String,
        format: BarcodeFormat,
        sizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): Bitmap

    fun generateSvg(
        payload: String,
        format: BarcodeFormat,
        exportSizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): String
}

class ZxingBarcodeGeneratorService : BarcodeGeneratorService {

    override fun validatePayload(payload: String, format: BarcodeFormat): String? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            return "Payload cannot be empty"
        }
        return when (format) {
            BarcodeFormat.CODE_128 -> {
                if (trimmed.length > 80) {
                    "Code-128 supports up to 80 characters"
                } else if (trimmed.any { it.code < 0x20 || it.code > 0x7E }) {
                    "Code-128 requires standard printable ASCII characters"
                } else {
                    null
                }
            }
            BarcodeFormat.QR_CODE -> {
                if (trimmed.toByteArray(Charsets.UTF_8).size > 2953) {
                    "QR payload exceeds maximum capacity (2,953 bytes)"
                } else {
                    null
                }
            }
            else -> null
        }
    }

    override fun generate(request: BarcodeGenerationRequest): GeneratedBarcodeResult {
        val startNs = System.nanoTime()
        val bitmap = generateBitmap(
            payload = request.payload,
            format = request.format,
            sizePx = request.sizePreset.px,
            palette = request.palette,
            ecLevel = request.errorCorrection
        )
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        return GeneratedBarcodeResult(
            bitmap = bitmap,
            format = request.format,
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            errorCorrection = request.errorCorrection,
            generationTimeMs = elapsedMs
        )
    }

    override fun encodeMatrix(
        payload: String,
        format: BarcodeFormat,
        widthPx: Int,
        heightPx: Int,
        ecLevel: QrEcLevel
    ): BitMatrix {
        val trimmed = payload.trim()
        validatePayload(trimmed, format)?.let { error ->
            throw IllegalArgumentException(error)
        }
        return when (format) {
            BarcodeFormat.CODE_128 -> {
                val hints = mapOf(
                    EncodeHintType.MARGIN to CODE128_QUIET_ZONE_MODULES
                )
                Code128Writer().encode(trimmed, BarcodeFormat.CODE_128, widthPx, heightPx, hints)
            }
            BarcodeFormat.QR_CODE -> {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ecLevel.zxingLevel,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES
                )
                QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, widthPx, heightPx, hints)
            }
            else -> throw IllegalArgumentException("Unsupported barcode format: $format")
        }
    }

    override fun generateBitmap(
        payload: String,
        format: BarcodeFormat,
        sizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel
    ): Bitmap {
        require(sizePx > 0) { "Size must be positive" }
        val width = sizePx
        val height = if (format == BarcodeFormat.CODE_128) {
            code128HeightForWidth(sizePx)
        } else {
            sizePx
        }
        val matrix = encodeMatrix(payload, format, width, height, ecLevel)
        val pixels = IntArray(width * height)
        val fg = palette.fgColor
        val bg = palette.bgColor
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (matrix[x, y]) fg else bg
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    override fun generateSvg(
        payload: String,
        format: BarcodeFormat,
        exportSizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel
    ): String {
        val trimmed = payload.trim()
        validatePayload(trimmed, format)?.let { error ->
            throw IllegalArgumentException(error)
        }
        val isCode128 = format == BarcodeFormat.CODE_128
        val matrix = if (isCode128) {
            val hints = mapOf(EncodeHintType.MARGIN to CODE128_QUIET_ZONE_MODULES)
            Code128Writer().encode(trimmed, BarcodeFormat.CODE_128, 1, 1, hints)
        } else {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ecLevel.zxingLevel,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES
            )
            QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, 0, 0, hints)
        }

        val mw = matrix.width
        val mh = if (isCode128) (mw * 0.44f).toInt().coerceAtLeast(40) else matrix.height
        val outWidth = exportSizePx
        val outHeight = if (isCode128) code128HeightForWidth(exportSizePx) else exportSizePx

        val pathData = StringBuilder()
        if (isCode128) {
            var x = 0
            while (x < mw) {
                if (matrix[x, 0]) {
                    var run = 1
                    while (x + run < mw && matrix[x + run, 0]) run++
                    pathData.append("M${x},4h${run}v${mh - 8}h-${run}z")
                    x += run
                } else {
                    x++
                }
            }
        } else {
            for (y in 0 until mh) {
                var x = 0
                while (x < mw) {
                    if (matrix[x, y]) {
                        var run = 1
                        while (x + run < mw && matrix[x + run, y]) run++
                        pathData.append("M${x},${y}h${run}v1h-${run}z")
                        x += run
                    } else {
                        x++
                    }
                }
            }
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="$outWidth" height="$outHeight" viewBox="0 0 $mw $mh" shape-rendering="crispEdges">
              <rect width="100%" height="100%" fill="${palette.bgHex}"/>
              <path d="$pathData" fill="${palette.fgHex}"/>
            </svg>
        """.trimIndent()
    }

    companion object {
        private const val QR_QUIET_ZONE_MODULES = 2
        private const val CODE128_QUIET_ZONE_MODULES = 10

        fun code128HeightForWidth(widthPx: Int): Int =
            (widthPx * 0.44f).toInt().coerceAtLeast(180)
    }
}
