package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ParsedBarcode
import com.example.domain.model.ParsedField
import com.example.domain.model.ScanCategory
import com.example.domain.parser.OfflineBarcodeParser
import com.example.generator.CodePalette
import com.example.generator.OfflineCodeGenerator
import com.example.generator.QrEcLevel
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanResultInspector(
    parsed: ParsedBarcode,
    scannedAtMillis: Long,
    isPinned: Boolean,
    initialNote: String = "",
    recordId: Long?,
    onCopyText: (label: String, value: String) -> Unit,
    onSaveImageToGallery: (Bitmap, String) -> Unit,
    onSaveNote: ((Long, String) -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val accent = parsed.category.accentColor()
    val formattedTime = remember(scannedAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scannedAtMillis))
    }
    var justCopiedPrimary by remember(parsed.rawValue) { mutableStateOf(false) }
    var noteText by remember(recordId, initialNote) { mutableStateOf(initialNote) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header row: Category icon + format badge + bookmark/delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryIconBadge(category = parsed.category)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormatTagPill(label = parsed.category.localizedLabel(strings), accentColor = accent)
                    FormatTagPill(label = parsed.formatLabel, accentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recordId != null && onTogglePin != null) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_pin_button")
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = strings.catBookmarks,
                        tint = if (isPinned) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (recordId != null && onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Title & subtitle
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = parsed.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (parsed.subtitle.isNotBlank() && parsed.subtitle != parsed.title) {
                Text(
                    text = parsed.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // One-Tap Quick Actions
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Copy
            Button(
                onClick = {
                    onCopyText(parsed.title, parsed.primaryCopyValue)
                    justCopiedPrimary = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("detail_copy_primary_button")
            ) {
                Icon(
                    imageVector = if (justCopiedPrimary) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (justCopiedPrimary) strings.actionCopied else strings.actionCopy)
            }

            // 2. Open Browser (URLs)
            if (parsed.category == ScanCategory.URL && parsed.actionUri != null) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.actionUri)))
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_open_browser_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionOpenBrowser)
                }
            }

            // 3. Wi-Fi Settings / Connect
            if (parsed.wifiInfo != null) {
                FilledTonalButton(
                    onClick = {
                        if (parsed.wifiInfo.password.isNotEmpty()) {
                            onCopyText("Wi-Fi", parsed.wifiInfo.password)
                        }
                        runCatching {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Intent(Settings.Panel.ACTION_WIFI)
                            } else {
                                Intent(Settings.ACTION_WIFI_SETTINGS)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_wifi_settings_button")
                ) {
                    Icon(Icons.Rounded.Wifi, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionWifiSettings)
                }
            }

            // 4. Call Phone
            if (!parsed.phoneNumber.isNullOrBlank()) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parsed.phoneNumber}"))
                            )
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_call_button")
                ) {
                    Icon(Icons.Rounded.Call, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionCall)
                }
            }

            // 5. Save Contact
            if (parsed.contactInfo != null) {
                FilledTonalButton(
                    onClick = {
                        val c = parsed.contactInfo
                        runCatching {
                            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                if (c.name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, c.name)
                                if (c.phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, c.phone)
                                if (c.email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, c.email)
                                if (c.organization.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, c.organization)
                                if (c.address.isNotBlank()) putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_save_contact_button")
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionSaveContact)
                }
            }

            // 6. Email / Geo Map
            if (!parsed.emailAddress.isNullOrBlank() && parsed.contactInfo == null) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${parsed.emailAddress}")))
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Email, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionEmail)
                }
            }
            if (parsed.actionUri?.startsWith("geo:", ignoreCase = true) == true) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.actionUri)))
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Map, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.actionOpenMap)
                }
            }

            // 7. Share
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, parsed.rawValue)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, strings.actionShare))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("detail_share_button")
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.actionShare)
            }

            // 8. Save QR / Barcode Image to Gallery
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val isCode128 = OfflineBarcodeParser.isProductBarcodeFormat(parsed.formatCode) &&
                            parsed.formatCode != Barcode.FORMAT_QR_CODE
                        val bmp = withContext(Dispatchers.Default) {
                            runCatching {
                                OfflineCodeGenerator.generateBitmap(
                                    payload = parsed.rawValue,
                                    isCode128 = isCode128,
                                    sizePx = 1024,
                                    palette = CodePalette.CLASSIC_WHITE,
                                    ecLevel = QrEcLevel.M
                                )
                            }.getOrElse {
                                OfflineCodeGenerator.generateBitmap(
                                    payload = parsed.rawValue,
                                    isCode128 = false,
                                    sizePx = 1024,
                                    palette = CodePalette.CLASSIC_WHITE,
                                    ecLevel = QrEcLevel.M
                                )
                            }
                        }
                        onSaveImageToGallery(bmp, parsed.title)
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("detail_save_qr_gallery_button")
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.actionSaveQrImage)
            }
        }

        // Technical Parameters Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.techParamsTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TechParamTile(
                        label = strings.techFormat,
                        value = parsed.formatLabel,
                        modifier = Modifier.weight(1f)
                    )
                    TechParamTile(
                        label = strings.techDataSize,
                        value = "${parsed.dataSizeBytes} B (${parsed.rawValue.length} ch)",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TechParamTile(
                        label = strings.techErrorCorrection,
                        value = parsed.errorCorrection.ifBlank { "Auto" },
                        modifier = Modifier.weight(1f)
                    )
                    TechParamTile(
                        label = strings.techProcessingTime,
                        value = "${parsed.processingTimeMs.coerceAtLeast(1L)} ms",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Offline Personal Note Editor
        if (recordId != null && onSaveNote != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.personalNoteTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text(strings.personalNoteHint) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_personal_note_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { onSaveNote(recordId, noteText) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_save_note_button")
                        ) {
                            Icon(Icons.Rounded.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.saveNote)
                        }
                    }
                }
            }
        }

        // Parsed Structured Details
        if (parsed.fields.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.extractedDetailsTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    parsed.fields.forEach { field ->
                        ParsedFieldRow(
                            field = field,
                            onCopy = { onCopyText(field.label, field.value) }
                        )
                    }
                }
            }
        }

        // Raw Payload Inspector
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.rawPayloadTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { onCopyText(strings.rawPayloadTitle, parsed.rawValue) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_copy_raw_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.actionCopyRaw)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = parsed.rawValue,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (onClose != null) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("detail_close_button")
            ) {
                Text(strings.actionResumeScan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TechParamTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ParsedFieldRow(
    field: ParsedField,
    onCopy: () -> Unit
) {
    var revealSensitive by remember(field.value) { mutableStateOf(!field.isSensitive) }
    val displayValue = if (revealSensitive) field.value else "•".repeat(field.value.length.coerceIn(6, 16))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayValue,
                fontFamily = if (field.isSensitive || field.label.contains("Code") || field.label.contains("URL")) {
                    JetBrainsMonoFontFamily
                } else {
                    MaterialTheme.typography.bodyMedium.fontFamily
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (field.isSensitive) {
            IconButton(
                onClick = { revealSensitive = !revealSensitive },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (revealSensitive) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("copy_field_${field.label.lowercase().replace(" ", "_")}")
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy ${field.label}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
