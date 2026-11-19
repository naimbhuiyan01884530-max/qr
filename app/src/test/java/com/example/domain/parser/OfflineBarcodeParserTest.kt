package com.example.domain.parser

import com.example.domain.model.ScanCategory
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineBarcodeParserTest {

    @Test
    fun parsesHttpsUrl() {
        val parsed = OfflineBarcodeParser.parse(
            rawValue = "https://github.com/android/camera-samples",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanCategory.URL, parsed.category)
        assertEquals("github.com", parsed.title)
        assertEquals("https://github.com/android/camera-samples", parsed.primaryCopyValue)
        assertEquals("https://github.com/android/camera-samples", parsed.actionUri)
    }

    @Test
    fun parsesWifiCredentialsWithEscapedChars() {
        val parsed = OfflineBarcodeParser.parse(
            rawValue = "WIFI:T:WPA;S:Studio\\;Net;P:s3cr3t\\:pass;H:true;;",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanCategory.WIFI, parsed.category)
        assertEquals("Studio;Net", parsed.title)
        assertEquals("s3cr3t:pass", parsed.primaryCopyValue)
        assertTrue(parsed.fields.any { it.label == "Password" && it.isSensitive && it.value == "s3cr3t:pass" })
    }

    @Test
    fun parsesOtpAuth2FaAccount() {
        val parsed = OfflineBarcodeParser.parse(
            rawValue = "otpauth://totp/GitHub:alice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanCategory.TWO_FA, parsed.category)
        assertEquals("GitHub (alice@example.com)", parsed.title)
        assertEquals("JBSWY3DPEHPK3PXP", parsed.primaryCopyValue)
        assertTrue(parsed.fields.any { it.label == "Secret Key" && it.isSensitive })
    }

    @Test
    fun parsesProductEan13Barcode() {
        val parsed = OfflineBarcodeParser.parse(
            rawValue = "0049000042566",
            formatCode = Barcode.FORMAT_EAN_13
        )
        assertEquals(ScanCategory.PRODUCT, parsed.category)
        assertEquals("EAN-13", parsed.formatLabel)
        assertEquals("0049000042566", parsed.primaryCopyValue)
    }

    @Test
    fun parsesVCardContact() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            ORG:Analytical Engine Lab
            TEL:+15550199
            EMAIL:ada@example.org
            END:VCARD
        """.trimIndent()
        val parsed = OfflineBarcodeParser.parse(vcard, Barcode.FORMAT_QR_CODE)
        assertEquals(ScanCategory.CONTACT, parsed.category)
        assertEquals("Ada Lovelace", parsed.title)
        assertEquals("+15550199", parsed.primaryCopyValue)
        assertEquals("tel:+15550199", parsed.actionUri)
    }

    @Test
    fun parsesPlainTextFallback() {
        val parsed = OfflineBarcodeParser.parse(
            rawValue = "Serial: SV-2026-0091",
            formatCode = Barcode.FORMAT_DATA_MATRIX
        )
        assertEquals(ScanCategory.TEXT, parsed.category)
        assertEquals("Data Matrix", parsed.formatLabel)
        assertEquals("Serial: SV-2026-0091", parsed.primaryCopyValue)
    }
}
