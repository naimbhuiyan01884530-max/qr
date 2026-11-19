package com.example.generator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
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
    PX_2048(2048);

    fun dimensionsLabel(isCode128: Boolean): String = if (isCode128) {
        "${px}×${ZxingBarcodeGeneratorService.code128HeightForWidth(px)}px"
    } else {
        "${px}×${px}px"
    }
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

    private val defaultService: BarcodeGeneratorService = ZxingBarcodeGeneratorService()

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
    ): BitMatrix = defaultService.encodeMatrix(
        payload = payload,
        format = if (isCode128) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE,
        widthPx = widthPx,
        heightPx = heightPx,
        ecLevel = ecLevel
    )

    fun generateBitmap(
        payload: String,
        isCode128: Boolean,
        sizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): Bitmap = defaultService.generateBitmap(
        payload = payload,
        format = if (isCode128) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE,
        sizePx = sizePx,
        palette = palette,
        ecLevel = ecLevel
    )

    fun generateSvg(
        payload: String,
        isCode128: Boolean,
        exportSizePx: Int,
        palette: CodePalette,
        ecLevel: QrEcLevel = QrEcLevel.M
    ): String = defaultService.generateSvg(
        payload = payload,
        format = if (isCode128) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE,
        exportSizePx = exportSizePx,
        palette = palette,
        ecLevel = ecLevel
    )

    private fun escapeWifi(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }
}
