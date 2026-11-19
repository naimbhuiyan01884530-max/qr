package com.example.domain.model

enum class ScanCategory {
    URL,
    PRODUCT,
    WIFI,
    TWO_FA,
    CONTACT,
    TEXT
}

enum class VaultFilter {
    ALL,
    BOOKMARKS,
    URL,
    WIFI,
    CONTACT,
    PRODUCT,
    TWO_FA,
    TEXT
}

data class ParsedField(
    val label: String,
    val value: String,
    val isSensitive: Boolean = false
)

data class WifiInfo(
    val ssid: String,
    val password: String,
    val security: String,
    val isHidden: Boolean
)

data class ContactInfo(
    val name: String = "",
    val organization: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val website: String = ""
)

data class ParsedBarcode(
    val rawValue: String,
    val formatCode: Int,
    val formatLabel: String,
    val category: ScanCategory,
    val title: String,
    val subtitle: String,
    val primaryCopyValue: String,
    val fields: List<ParsedField>,
    val actionUri: String? = null,
    val wifiInfo: WifiInfo? = null,
    val contactInfo: ContactInfo? = null,
    val phoneNumber: String? = null,
    val emailAddress: String? = null,
    val dataSizeBytes: Int = rawValue.toByteArray(Charsets.UTF_8).size,
    val errorCorrection: String = "",
    val processingTimeMs: Long = 0L
)
