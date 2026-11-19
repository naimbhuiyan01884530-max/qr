package com.example.generator

import com.example.data.local.ScanRecordEntity
import com.example.export.LocalExportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineCodeGeneratorAndExportTest {

    @Test
    fun buildsStandardWifiAndVCardPayloads() {
        val wifi = OfflineCodeGenerator.buildWifiPayload("NaimNet;5G", "pass:123", "WPA", hidden = true)
        assertEquals("WIFI:T:WPA;S:NaimNet\\;5G;P:pass\\:123;H:true;;", wifi)

        val vcard = OfflineCodeGenerator.buildVCardPayload("Naim", "+8801700000000", "naim@example.com", "Lab")
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:Naim"))
        assertTrue(vcard.contains("TEL:+8801700000000"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun generatesValidQrAndCode128SvgAndBitmaps() {
        val qrSvg = OfflineCodeGenerator.generateSvg(
            payload = "https://example.com/naim",
            isCode128 = false,
            exportSizePx = 1024,
            palette = CodePalette.NEON_MINT,
            ecLevel = QrEcLevel.H
        )
        assertTrue(qrSvg.contains("<svg"))
        assertTrue(qrSvg.contains("width=\"1024\""))
        assertTrue(qrSvg.contains("fill=\"#2AF598\""))

        val code128Svg = OfflineCodeGenerator.generateSvg(
            payload = "NAIM-QR-2026",
            isCode128 = true,
            exportSizePx = 512,
            palette = CodePalette.CLASSIC_WHITE
        )
        assertTrue(code128Svg.contains("<svg"))
        assertTrue(code128Svg.contains("width=\"512\""))
        assertTrue(code128Svg.contains("fill=\"#0F172A\""))

        val qrBmp = OfflineCodeGenerator.generateBitmap(
            payload = "https://example.com/naim",
            isCode128 = false,
            sizePx = 512,
            palette = CodePalette.CYAN,
            ecLevel = QrEcLevel.Q
        )
        assertEquals(512, qrBmp.width)
        assertEquals(512, qrBmp.height)
    }

    @Test
    fun roundTripsJsonBackupAndEscapesCsv() {
        val records = listOf(
            ScanRecordEntity(
                id = 1L,
                rawValue = "https://example.com?a=1,2",
                formatCode = 256,
                formatLabel = "QR Code",
                category = "URL",
                title = "example.com",
                subtitle = "https://example.com?a=1,2",
                isPinned = true,
                scannedAtMillis = 1700000000000L,
                note = "Important \"work\" link",
                dataSizeBytes = 25,
                errorCorrection = "QR Level H",
                processingTimeMs = 11L
            )
        )

        val json = LocalExportHelper.exportVaultToJson(records)
        val restored = LocalExportHelper.importVaultFromJson(json)
        assertEquals(1, restored.size)
        assertEquals("Important \"work\" link", restored.first().note)
        assertEquals(true, restored.first().isPinned)
        assertEquals(11L, restored.first().processingTimeMs)

        val csv = LocalExportHelper.exportVaultToCsv(records)
        assertTrue(csv.contains("\"https://example.com?a=1,2\""))
        assertTrue(csv.contains("\"Important \"\"work\"\" link\""))
    }
}
