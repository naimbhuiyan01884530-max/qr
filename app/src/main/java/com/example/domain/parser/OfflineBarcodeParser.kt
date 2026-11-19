package com.example.domain.parser

import com.example.domain.model.ParsedBarcode
import com.example.domain.model.ParsedField
import com.example.domain.model.ScanCategory
import com.google.mlkit.vision.barcode.common.Barcode
import java.net.URI
import java.net.URLDecoder

object OfflineBarcodeParser {

    fun formatNameFor(formatCode: Int): String = when (formatCode) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "Barcode"
    }

    fun isProductBarcodeFormat(formatCode: Int): Boolean = when (formatCode) {
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_CODE_93,
        Barcode.FORMAT_CODABAR,
        Barcode.FORMAT_ITF -> true
        else -> false
    }

    fun parse(rawValue: String, formatCode: Int): ParsedBarcode {
        val trimmed = rawValue.trim()
        val formatLabel = formatNameFor(formatCode)

        // 1. 2FA / OTP Account URI (otpauth:// or otpauth-migration://)
        if (trimmed.startsWith("otpauth://", ignoreCase = true) ||
            trimmed.startsWith("otpauth-migration://", ignoreCase = true)
        ) {
            return parseOtpAuth(trimmed, formatCode, formatLabel)
        }

        // 2. Wi-Fi Credentials (WIFI:T:WPA;S:MyNet;P:Pass;; or WIFI:S:...;)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            return parseWifi(trimmed, formatCode, formatLabel)
        }

        // 3. Contact (vCard / MeCard / Email / Phone / SMS / Geo)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            return parseVCard(trimmed, formatCode, formatLabel)
        }
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            return parseMeCard(trimmed, formatCode, formatLabel)
        }
        if (trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("MATMSG:", ignoreCase = true)
        ) {
            return parseEmail(trimmed, formatCode, formatLabel)
        }
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val number = trimmed.substringAfter(":").trim()
            return ParsedBarcode(
                rawValue = rawValue,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.CONTACT,
                title = number.ifBlank { "Phone Number" },
                subtitle = "Phone contact action",
                primaryCopyValue = number.ifBlank { trimmed },
                fields = listOf(ParsedField("Phone", number)),
                actionUri = "tel:$number"
            )
        }
        if (trimmed.startsWith("smsto:", ignoreCase = true) ||
            trimmed.startsWith("sms:", ignoreCase = true)
        ) {
            val bodyPart = trimmed.substringAfter(":")
            val recipient = bodyPart.substringBefore(":")
            val message = bodyPart.substringAfter(":", "")
            val fields = buildList {
                if (recipient.isNotBlank()) add(ParsedField("Recipient", recipient))
                if (message.isNotBlank()) add(ParsedField("Message", message))
            }
            return ParsedBarcode(
                rawValue = rawValue,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.CONTACT,
                title = recipient.ifBlank { "SMS Message" },
                subtitle = if (message.isNotBlank()) message else "SMS shortcut",
                primaryCopyValue = recipient.ifBlank { trimmed },
                fields = fields,
                actionUri = "smsto:$recipient"
            )
        }

        // 4. Web Links / URLs
        if (looksLikeUrl(trimmed)) {
            val normalizedUrl = if (trimmed.startsWith("www.", ignoreCase = true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
            val host = extractHost(normalizedUrl)
            val fields = buildList {
                add(ParsedField("URL", normalizedUrl))
                if (host.isNotBlank()) add(ParsedField("Domain", host))
                val scheme = normalizedUrl.substringBefore("://", "").uppercase()
                if (scheme.isNotBlank()) add(ParsedField("Protocol", scheme))
            }
            return ParsedBarcode(
                rawValue = rawValue,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.URL,
                title = host.ifBlank { normalizedUrl },
                subtitle = normalizedUrl,
                primaryCopyValue = normalizedUrl,
                fields = fields,
                actionUri = normalizedUrl
            )
        }

        // 5. Geo coordinates
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val coords = trimmed.substringAfter(":").substringBefore("?")
            return ParsedBarcode(
                rawValue = rawValue,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.CONTACT,
                title = "Location Pin",
                subtitle = coords,
                primaryCopyValue = coords,
                fields = listOf(ParsedField("Coordinates", coords)),
                actionUri = trimmed
            )
        }

        // 6. Product / Industrial 1D Barcodes (or pure numeric EAN/UPC/ISBN)
        if (isProductBarcodeFormat(formatCode) || isIsbnLike(trimmed)) {
            val isIsbn = (formatCode == Barcode.FORMAT_EAN_13 &&
                (trimmed.startsWith("978") || trimmed.startsWith("979"))) || isIsbnLike(trimmed)
            val label = if (isIsbn) "ISBN Book Code" else "$formatLabel Product Code"
            return ParsedBarcode(
                rawValue = rawValue,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.PRODUCT,
                title = trimmed,
                subtitle = label,
                primaryCopyValue = trimmed,
                fields = listOf(
                    ParsedField("Code", trimmed),
                    ParsedField("Symbology", formatLabel),
                    ParsedField("Digits", trimmed.length.toString())
                )
            )
        }

        // 7. Plain Text fallback
        val firstLine = trimmed.lineSequence().firstOrNull()?.take(80).orEmpty()
        return ParsedBarcode(
            rawValue = rawValue,
            formatCode = formatCode,
            formatLabel = formatLabel,
            category = ScanCategory.TEXT,
            title = firstLine.ifBlank { "Text Payload" },
            subtitle = "$formatLabel • ${trimmed.length} chars",
            primaryCopyValue = trimmed,
            fields = listOf(ParsedField("Content", trimmed))
        )
    }

    private fun parseWifi(raw: String, formatCode: Int, formatLabel: String): ParsedBarcode {
        val body = raw.removePrefix("WIFI:").removePrefix("wifi:")
        val tokens = splitUnescapedSemicolons(body)
        var ssid = ""
        var password = ""
        var authType = "Open"
        var hidden = false

        for (token in tokens) {
            if (token.isBlank()) continue
            val key = token.substringBefore(":").uppercase()
            val value = unescapeWifiValue(token.substringAfter(":", ""))
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> authType = value.ifBlank { "Open" }
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }

        val fields = buildList {
            add(ParsedField("Network (SSID)", ssid.ifBlank { "Hidden Network" }))
            add(ParsedField("Security", authType))
            if (password.isNotEmpty()) {
                add(ParsedField("Password", password, isSensitive = true))
            }
            if (hidden) {
                add(ParsedField("Visibility", "Hidden SSID"))
            }
        }

        val subtitleParts = buildList {
            add(authType)
            if (password.isNotEmpty()) add("Password protected") else add("No password")
            if (hidden) add("Hidden")
        }

        return ParsedBarcode(
            rawValue = raw,
            formatCode = formatCode,
            formatLabel = formatLabel,
            category = ScanCategory.WIFI,
            title = ssid.ifBlank { "Wi-Fi Network" },
            subtitle = subtitleParts.joinToString(" • "),
            primaryCopyValue = if (password.isNotEmpty()) password else ssid.ifBlank { raw },
            fields = fields
        )
    }

    private fun parseOtpAuth(raw: String, formatCode: Int, formatLabel: String): ParsedBarcode {
        if (raw.startsWith("otpauth-migration://", ignoreCase = true)) {
            return ParsedBarcode(
                rawValue = raw,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.TWO_FA,
                title = "Authenticator Account Export",
                subtitle = "Google Authenticator Migration Payload",
                primaryCopyValue = raw,
                fields = listOf(ParsedField("Migration URI", raw, isSensitive = true))
            )
        }

        val withoutScheme = raw.substringAfter("://", "")
        val otpType = withoutScheme.substringBefore("/", "totp").uppercase()
        val labelAndQuery = withoutScheme.substringAfter("/", "")
        val rawLabel = decodeUrlPart(labelAndQuery.substringBefore("?"))
        val queryString = labelAndQuery.substringAfter("?", "")
        val queryParams = parseQueryParams(queryString)

        val issuer = queryParams["issuer"].orEmpty()
            .ifBlank { rawLabel.substringBefore(":", "").takeIf { ":" in rawLabel }.orEmpty() }
        val accountName = if (":" in rawLabel) rawLabel.substringAfter(":").trim() else rawLabel
        val secret = queryParams["secret"].orEmpty()
        val digits = queryParams["digits"] ?: "6"
        val period = queryParams["period"] ?: "30"
        val algorithm = queryParams["algorithm"] ?: "SHA1"

        val fields = buildList {
            if (issuer.isNotBlank()) add(ParsedField("Issuer", issuer))
            if (accountName.isNotBlank()) add(ParsedField("Account", accountName))
            if (secret.isNotBlank()) add(ParsedField("Secret Key", secret, isSensitive = true))
            add(ParsedField("Type", "$otpType • $digits digits • ${period}s ($algorithm)"))
        }

        val title = when {
            issuer.isNotBlank() && accountName.isNotBlank() -> "$issuer ($accountName)"
            issuer.isNotBlank() -> issuer
            accountName.isNotBlank() -> accountName
            else -> "2FA Authenticator Key"
        }

        return ParsedBarcode(
            rawValue = raw,
            formatCode = formatCode,
            formatLabel = formatLabel,
            category = ScanCategory.TWO_FA,
            title = title,
            subtitle = "$otpType • $digits digits • ${period}s",
            primaryCopyValue = secret.ifBlank { raw },
            fields = fields
        )
    }

    private fun parseVCard(raw: String, formatCode: Int, formatLabel: String): ParsedBarcode {
        var name = ""
        var org = ""
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var url = ""
        var address = ""

        raw.lineSequence().forEach { line ->
            val trimmedLine = line.trim()
            val upper = trimmedLine.uppercase()
            when {
                upper.startsWith("FN:") || upper.startsWith("FN;") ->
                    name = trimmedLine.substringAfter(":").trim()
                upper.startsWith("N:") && name.isBlank() ->
                    name = trimmedLine.substringAfter(":").split(";")
                        .filter { it.isNotBlank() }.reversed().joinToString(" ").trim()
                upper.startsWith("ORG:") || upper.startsWith("ORG;") ->
                    org = trimmedLine.substringAfter(":").replace(";", " ").trim()
                upper.startsWith("TEL:") || upper.startsWith("TEL;") ->
                    phones += trimmedLine.substringAfter(":").trim()
                upper.startsWith("EMAIL:") || upper.startsWith("EMAIL;") ->
                    emails += trimmedLine.substringAfter(":").trim()
                upper.startsWith("URL:") || upper.startsWith("URL;") ->
                    url = trimmedLine.substringAfter(":").trim()
                upper.startsWith("ADR:") || upper.startsWith("ADR;") ->
                    address = trimmedLine.substringAfter(":")
                        .split(";").filter { it.isNotBlank() }.joinToString(", ")
            }
        }

        val fields = buildList {
            if (name.isNotBlank()) add(ParsedField("Name", name))
            if (org.isNotBlank()) add(ParsedField("Organization", org))
            phones.forEachIndexed { idx, p ->
                add(ParsedField(if (phones.size == 1) "Phone" else "Phone ${idx + 1}", p))
            }
            emails.forEachIndexed { idx, e ->
                add(ParsedField(if (emails.size == 1) "Email" else "Email ${idx + 1}", e))
            }
            if (url.isNotBlank()) add(ParsedField("Website", url))
            if (address.isNotBlank()) add(ParsedField("Address", address))
        }

        val subtitle = listOfNotNull(
            org.takeIf { it.isNotBlank() },
            phones.firstOrNull(),
            emails.firstOrNull()
        ).joinToString(" • ").ifBlank { "vCard Contact" }

        return ParsedBarcode(
            rawValue = raw,
            formatCode = formatCode,
            formatLabel = formatLabel,
            category = ScanCategory.CONTACT,
            title = name.ifBlank { org.ifBlank { "Contact Card" } },
            subtitle = subtitle,
            primaryCopyValue = phones.firstOrNull() ?: emails.firstOrNull() ?: name.ifBlank { raw },
            fields = fields,
            actionUri = phones.firstOrNull()?.let { "tel:$it" } ?: emails.firstOrNull()?.let { "mailto:$it" }
        )
    }

    private fun parseMeCard(raw: String, formatCode: Int, formatLabel: String): ParsedBarcode {
        val body = raw.substringAfter(":")
        val parts = splitUnescapedSemicolons(body)
        var name = ""
        var phone = ""
        var email = ""
        var note = ""
        var url = ""

        for (part in parts) {
            val k = part.substringBefore(":").uppercase()
            val v = part.substringAfter(":", "").trim()
            if (v.isBlank()) continue
            when (k) {
                "N" -> name = v.replace(",", " ").trim()
                "TEL" -> phone = v
                "EMAIL" -> email = v
                "NOTE" -> note = v
                "URL" -> url = v
            }
        }

        val fields = buildList {
            if (name.isNotBlank()) add(ParsedField("Name", name))
            if (phone.isNotBlank()) add(ParsedField("Phone", phone))
            if (email.isNotBlank()) add(ParsedField("Email", email))
            if (url.isNotBlank()) add(ParsedField("Website", url))
            if (note.isNotBlank()) add(ParsedField("Note", note))
        }

        return ParsedBarcode(
            rawValue = raw,
            formatCode = formatCode,
            formatLabel = formatLabel,
            category = ScanCategory.CONTACT,
            title = name.ifBlank { phone.ifBlank { email.ifBlank { "Contact" } } },
            subtitle = listOfNotNull(phone.takeIf { it.isNotBlank() }, email.takeIf { it.isNotBlank() })
                .joinToString(" • ").ifBlank { "MeCard Contact" },
            primaryCopyValue = phone.ifBlank { email.ifBlank { raw } },
            fields = fields,
            actionUri = phone.takeIf { it.isNotBlank() }?.let { "tel:$it" }
        )
    }

    private fun parseEmail(raw: String, formatCode: Int, formatLabel: String): ParsedBarcode {
        return if (raw.startsWith("MATMSG:", ignoreCase = true)) {
            val body = raw.substringAfter(":")
            val tokens = splitUnescapedSemicolons(body)
            var to = ""
            var sub = ""
            var msg = ""
            for (token in tokens) {
                when (token.substringBefore(":").uppercase()) {
                    "TO" -> to = token.substringAfter(":", "")
                    "SUB" -> sub = token.substringAfter(":", "")
                    "BODY" -> msg = token.substringAfter(":", "")
                }
            }
            val fields = buildList {
                if (to.isNotBlank()) add(ParsedField("Email", to))
                if (sub.isNotBlank()) add(ParsedField("Subject", sub))
                if (msg.isNotBlank()) add(ParsedField("Body", msg))
            }
            ParsedBarcode(
                rawValue = raw,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.CONTACT,
                title = to.ifBlank { "Email Draft" },
                subtitle = sub.ifBlank { "Email message" },
                primaryCopyValue = to.ifBlank { raw },
                fields = fields,
                actionUri = to.takeIf { it.isNotBlank() }?.let { "mailto:$it" }
            )
        } else {
            val address = raw.substringAfter(":").substringBefore("?")
            val params = parseQueryParams(raw.substringAfter("?", ""))
            val subject = params["subject"].orEmpty()
            val fields = buildList {
                if (address.isNotBlank()) add(ParsedField("Email", address))
                if (subject.isNotBlank()) add(ParsedField("Subject", subject))
            }
            ParsedBarcode(
                rawValue = raw,
                formatCode = formatCode,
                formatLabel = formatLabel,
                category = ScanCategory.CONTACT,
                title = address.ifBlank { "Email Address" },
                subtitle = subject.ifBlank { "Email contact" },
                primaryCopyValue = address.ifBlank { raw },
                fields = fields,
                actionUri = "mailto:$address"
            )
        }
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.contains('\n') || text.contains(' ')) return false
        return text.startsWith("https://", ignoreCase = true) ||
            text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("www.", ignoreCase = true)
    }

    private fun isIsbnLike(text: String): Boolean {
        val digits = text.replace("-", "")
        return (digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979")) &&
            digits.all { it.isDigit() })
    }

    private fun extractHost(url: String): String {
        return try {
            URI(url).host?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeUrlPart(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val key = pair.substringBefore("=", "")
            if (key.isBlank()) null
            else decodeUrlPart(key.lowercase()) to decodeUrlPart(pair.substringAfter("=", ""))
        }.toMap()
    }

    private fun splitUnescapedSemicolons(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            when {
                escaped -> {
                    current.append('\\').append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == ';' -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun unescapeWifiValue(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                out.append(value[i + 1])
                i += 2
            } else {
                out.append(value[i])
                i += 1
            }
        }
        return out.toString()
    }
}
