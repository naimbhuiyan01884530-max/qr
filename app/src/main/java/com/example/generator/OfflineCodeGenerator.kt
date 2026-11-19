package com.example.generator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class GeneratorType {
    URL,
    TEXT,
    WIFI,
    VCARD,
    CODE_128
}

enum class CodePalette(
    val fgColor: Int,
    val bgColor: Int,
    val fgHex: String,
    val bgHex: String
) {
    NEON_MINT(
        fgColor = Color.rgb(0x2A, 0xF5, 0x98),
        bgColor = Color.rgb(0x0B, 0x11, 0x1A),
        fgHex = "#2AF598",
        bgHex = "#0B111A"
    ),
    CYAN(
        fgColor = Color.rgb(0x00, 0xE5, 0xFF),
        bgColor = Color.rgb(0x0B, 0x11, 0x1A),
        fgHex = "#00E5FF",
        bgHex = "#0B111A"
    ),
    CLASSIC_WHITE(
        fgColor = Color.rgb(0x0F, 0x17, 0x2A),
        bgColor = Color.WHITE,
        fgHex = "#0F172A",
        bgHex = "#FFFFFF"
    )
}

enum class CodeSize(val px: Int) {
    PX_512(512),
    PX_1024(1024),
    PX_2048(2048)
}

enum class QrEcLevel(
    val zxingLevel: ErrorCorrectionLevel,
    val shortCode: String,
    val description: String
) {
    L(ErrorCorrectionLevel.L, "L", "L (7%)"),
    M(ErrorCorrectionLevel.M, "M", "M (15%)"),
    Q(ErrorCorrectionLevel.Q, "Q", "Q (25%)"),
    H(ErrorCorrectionLevel.H, "H", "H (30%)")
}

object OfflineCodeGenerator {

    fun buildWifiPayload(ssid: String, password: String, security: String, hidden: Boolean): String {
        if (ssid.isBlank()) return ""
        val escapedSsid = escapeWifi(ssid.trim())
        val escapedPass = escapeWifi(password)
        val secToken = when (security.uppercase()) {
            "NONE", "OPEN" -> "nopass"
            "WEP" -> "WEP"
            else -> "WPA"
        }
        return buildString {
            append("WIFI:T:").append(secToken).append(";")
            append("S:").append(escapedSsid).append(";")
            if (secToken != "nopass" && escapedPass.isNotEmpty()) {
                append("P:").append(escapedPass).append(";")
            }
            if (hidden) append("H:true;")
            append(";")
        }
    }

    fun buildVCardPayload(name: String, phone: String, email: String, org: String): String {
        if (name.isBlank() && phone.isBlank() && email.isBlank() && org.isBlank()) return ""
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            if (name.isNotBlank()) appendLine("FN:${name.trim()}")
            if (org.isNotBlank()) appendLine("ORG:${org.trim()}")
            if (phone.isNotBlank()) appendLine("TEL:${phone.trim()}")
            if (email.isNotBlank()) appendLine("EMAIL:${email.trim()}")
            append("END:VCARD")
        }
    }

    fun normalizeUrlPayload(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (!trimmed.contains("://") && trimmed.contains('.')) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun encodeMatrix(
        payload: String,
        isCode128: Boolean,
        widthPx: Int,
        heightPx: Int,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): BitMatrix {
        val trimmed = payload.trim()
        require(trimmed.isNotEmpty()) { "Payload cannot be empty" }
        return if (isCode128) {
            Code128Writer().encode(trimmed, BarcodeFormat.CODE_128, widthPx, heightPx)
        } else {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ecLevel.zxingLevel,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2
            )
            QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, widthPx, heightPx, hints)
        }
    }

    fun generateBitmap(
        payload: String,
        isCode128: Boolean,
        sizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): Bitmap {
        val width = sizePx
        val height = if (isCode128) (sizePx * 0.44f).toInt().coerceAtLeast(180) else sizePx
        val matrix = encodeMatrix(payload, isCode128, width, height, ecLevel)
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

    fun generateSvg(
        payload: String,
        isCode128: Boolean,
        exportSizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): String {
        // Encode minimal natural module matrix for crisp, compact vector SVG
        val matrix = if (isCode128) {
            Code128Writer().encode(payload.trim(), BarcodeFormat.CODE_128, 1, 1)
        } else {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ecLevel.zxingLevel,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2
            )
            QRCodeWriter().encode(payload.trim(), BarcodeFormat.QR_CODE, 0, 0, hints)
        }

        val mw = matrix.width
        val mh = if (isCode128) (mw * 0.44f).toInt().coerceAtLeast(40) else matrix.height
        val outWidth = exportSizePx
        val outHeight = if (isCode128) (exportSizePx * 0.44f).toInt() else exportSizePx

        val pathData = StringBuilder()
        if (isCode128) {
            // Merge vertical bars cleanly
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

    private fun escapeWifi(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }
}
