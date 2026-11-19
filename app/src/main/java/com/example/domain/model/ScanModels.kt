package com.example.domain.model

enum class ScanCategory(val displayName: String) {
    URL("Links"),
    PRODUCT("Products"),
    WIFI("Wi-Fi"),
    TWO_FA("2FA & Accounts"),
    CONTACT("Contacts"),
    TEXT("Text")
}

data class ParsedField(
    val label: String,
    val value: String,
    val isSensitive: Boolean = false
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
    val actionUri: String? = null
)
