package com.example.generator

import android.graphics.Bitmap
import com.example.data.local.ScanRecordEntity
import com.example.export.LocalExportHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.Code128Reader
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineCodeGeneratorAndExportTest {

    private val generatorService: BarcodeGeneratorService = ZxingBarcodeGeneratorService()

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
    fun generatesAndDecodesQrAcrossAllErrorCorrectionLevels() {
        val payload = "https://naimqr.example/verify?ec=test"
        QrEcLevel.entries.forEach { ecLevel ->
            val generated = generatorService.generate(
                BarcodeGenerationRequest(
                    payload = payload,
                    format = BarcodeFormat.QR_CODE,
                    sizePreset = CodeSize.PX_512,
                    palette = CodePalette.CLASSIC_WHITE,
                    errorCorrection = ecLevel
                )
            )
            assertEquals(512, generated.widthPx)
            assertEquals(512, generated.heightPx)
            assertEquals(BarcodeFormat.QR_CODE, generated.format)
            assertEquals(ecLevel, generated.errorCorrection)

            val decoded = decodeQrBitmap(generated.bitmap)
            assertEquals(payload, decoded.text)
            assertEquals(
                ecLevel.shortCode,
                decoded.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL]
            )
        }
    }

    @Test
    fun generatesAndDecodesQrAndCode128AcrossAllSizePresets() {
        val qrPayload = "Naim QR Studio 2026"
        val code128Payload = "NAIM-QR-128-90210"

        CodeSize.entries.forEach { sizePreset ->
            val qrResult = generatorService.generate(
                BarcodeGenerationRequest(
                    payload = qrPayload,
                    format = BarcodeFormat.QR_CODE,
                    sizePreset = sizePreset,
                    palette = CodePalette.CLASSIC_WHITE,
                    errorCorrection = QrEcLevel.M
                )
            )
            assertEquals(sizePreset.px, qrResult.widthPx)
            assertEquals(sizePreset.px, qrResult.heightPx)
            assertEquals(qrPayload, decodeQrBitmap(qrResult.bitmap).text)

            val c128Result = generatorService.generate(
                BarcodeGenerationRequest(
                    payload = code128Payload,
                    format = BarcodeFormat.CODE_128,
                    sizePreset = sizePreset,
                    palette = CodePalette.CLASSIC_WHITE
                )
            )
            assertEquals(sizePreset.px, c128Result.widthPx)
            assertEquals(
                ZxingBarcodeGeneratorService.code128HeightForWidth(sizePreset.px),
                c128Result.heightPx
            )
            assertEquals(code128Payload, decodeCode128Bitmap(c128Result.bitmap).text)
        }
    }

    @Test
    fun validatesEmptyAndNonAsciiCode128Payloads() {
        assertNotNull(generatorService.validatePayload("   ", BarcodeFormat.QR_CODE))
        assertNotNull(generatorService.validatePayload("বাংলা কোড", BarcodeFormat.CODE_128))
        assertNull(generatorService.validatePayload("বাংলা কিউআর", BarcodeFormat.QR_CODE))
        assertNull(generatorService.validatePayload("SKU-00491-OK", BarcodeFormat.CODE_128))
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

    private fun decodeQrBitmap(bitmap: Bitmap): Result {
        val binary = toBinaryBitmap(bitmap)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        return QRCodeReader().decode(binary, hints)
    }

    private fun decodeCode128Bitmap(bitmap: Bitmap): Result {
        val binary = toBinaryBitmap(bitmap)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        return Code128Reader().decode(binary, hints)
    }

    private fun toBinaryBitmap(bitmap: Bitmap): BinaryBitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return BinaryBitmap(HybridBinarizer(source))
    }
}
