package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.export.LocalExportHelper
import com.example.generator.CodePalette
import com.example.generator.CodeSize
import com.example.generator.GeneratorType
import com.example.generator.OfflineCodeGenerator
import com.example.generator.QrEcLevel
import com.example.ui.components.FormatTagPill
import com.example.ui.components.ThemeAndLanguageActions
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSavePngToGallery: (Bitmap, String) -> Unit,
    onSaveToHistory: (payload: String, isCode128: Boolean, ecLevel: QrEcLevel, genTimeMs: Long) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(GeneratorType.URL) }
    var urlInput by remember { mutableStateOf("https://") }
    var textInput by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    var wifiHidden by remember { mutableStateOf(false) }
    var vcardName by remember { mutableStateOf("") }
    var vcardPhone by remember { mutableStateOf("") }
    var vcardEmail by remember { mutableStateOf("") }
    var vcardOrg by remember { mutableStateOf("") }
    var code128Input by remember { mutableStateOf("") }

    var selectedPalette by remember { mutableStateOf(CodePalette.CYAN) }
    var selectedSize by remember { mutableStateOf(CodeSize.PX_1024) }
    var selectedEcLevel by remember { mutableStateOf(QrEcLevel.M) }

    val payload = remember(
        selectedType, urlInput, textInput,
        wifiSsid, wifiPassword, wifiSecurity, wifiHidden,
        vcardName, vcardPhone, vcardEmail, vcardOrg, code128Input
    ) {
        when (selectedType) {
            GeneratorType.URL -> {
                val trimmed = urlInput.trim()
                if (trimmed == "https://" || trimmed == "http://" || trimmed.isBlank()) ""
                else OfflineCodeGenerator.normalizeUrlPayload(trimmed)
            }
            GeneratorType.TEXT -> textInput.trim()
            GeneratorType.WIFI -> OfflineCodeGenerator.buildWifiPayload(wifiSsid, wifiPassword, wifiSecurity, wifiHidden)
            GeneratorType.VCARD -> OfflineCodeGenerator.buildVCardPayload(vcardName, vcardPhone, vcardEmail, vcardOrg)
            GeneratorType.CODE_128 -> code128Input.trim()
        }
    }

    val isCode128 = selectedType == GeneratorType.CODE_128
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(payload, isCode128, selectedPalette, selectedEcLevel) {
        if (payload.isBlank()) {
            previewBitmap = null
            generationError = null
            generationMs = 0L
        } else {
            val startNs = System.nanoTime()
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    OfflineCodeGenerator.generateBitmap(
                        payload = payload,
                        isCode128 = isCode128,
                        sizePx = 512,
                        palette = selectedPalette,
                        ecLevel = selectedEcLevel
                    )
                }
            }
            val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            result.onSuccess { bmp ->
                previewBitmap = bmp
                generationError = null
                generationMs = elapsed
            }.onFailure { err ->
                previewBitmap = null
                generationError = err.localizedMessage ?: "Encoding failed"
            }
        }
    }

    val svgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        if (uri != null && payload.isNotBlank()) {
            scope.launch {
                runCatching {
                    val svg = withContext(Dispatchers.Default) {
                        OfflineCodeGenerator.generateSvg(
                            payload = payload,
                            isCode128 = isCode128,
                            exportSizePx = selectedSize.px,
                            palette = selectedPalette,
                            ecLevel = selectedEcLevel
                        )
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(svg.toByteArray(Charsets.UTF_8))
                        } ?: error("Cannot write SVG file")
                    }
                }.onSuccess {
                    onShowMessage(strings.svgSavedMsg)
                }.onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "SVG export failed")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Dark/Light + Language Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.generatorTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ThemeAndLanguageActions(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onToggleLanguage = onToggleLanguage
            )
        }

        // Type Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                GeneratorType.URL to strings.genTypeUrl,
                GeneratorType.TEXT to strings.genTypeText,
                GeneratorType.WIFI to strings.genTypeWifi,
                GeneratorType.VCARD to strings.genTypeVcard,
                GeneratorType.CODE_128 to strings.genTypeCode128
            ).forEach { (type, label) ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(label) },
                    modifier = Modifier.testTag("gen_type_${type.name.lowercase()}")
                )
            }
        }

        // Input Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedType) {
                    GeneratorType.URL -> {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text(strings.labelUrlInput) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gen_url_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (!urlInput.startsWith("https://")) {
                                    urlInput = "https://${urlInput.removePrefix("http://")}"
                                }
                            }) { Text("https://") }
                            TextButton(onClick = { urlInput = "https://" }) { Text(strings.clearLabel) }
                        }
                    }

                    GeneratorType.TEXT -> {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text(strings.labelTextInput) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gen_text_input")
                        )
                    }

                    GeneratorType.WIFI -> {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text(strings.labelWifiSsid) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (wifiSecurity != "None") {
                            OutlinedTextField(
                                value = wifiPassword,
                                onValueChange = { wifiPassword = it },
                                label = { Text(strings.labelWifiPass) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("WPA", "WEP", "None").forEach { sec ->
                                FilterChip(
                                    selected = wifiSecurity == sec,
                                    onClick = { wifiSecurity = sec },
                                    label = { Text(sec) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.labelWifiHidden, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = wifiHidden, onCheckedChange = { wifiHidden = it })
                        }
                    }

                    GeneratorType.VCARD -> {
                        OutlinedTextField(
                            value = vcardName,
                            onValueChange = { vcardName = it },
                            label = { Text(strings.labelContactName) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vcardPhone,
                            onValueChange = { vcardPhone = it },
                            label = { Text(strings.labelContactPhone) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vcardEmail,
                            onValueChange = { vcardEmail = it },
                            label = { Text(strings.labelContactEmail) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vcardOrg,
                            onValueChange = { vcardOrg = it },
                            label = { Text(strings.labelContactOrg) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    GeneratorType.CODE_128 -> {
                        OutlinedTextField(
                            value = code128Input,
                            onValueChange = { code128Input = it },
                            label = { Text(strings.labelCode128Input) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gen_code128_input")
                        )
                    }
                }
            }
        }

        // Live Preview Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (previewBitmap != null) {
                    val bmp = previewBitmap!!
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth()
                            .then(
                                if (isCode128) Modifier.height(130.dp) else Modifier.aspectRatio(1f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(selectedPalette.bgColor))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated code preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormatTagPill(label = if (isCode128) "CODE-128" else "QR • EC ${selectedEcLevel.shortCode}")
                        FormatTagPill(label = "${selectedSize.px}px", accentColor = MaterialTheme.colorScheme.secondary)
                        FormatTagPill(label = "${generationMs} ms", accentColor = MaterialTheme.colorScheme.tertiary)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = generationError ?: strings.enterContentPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (generationError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Palette, Size & Error Correction Controls
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.paletteTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CodePalette.NEON_MINT to strings.paletteNeonMint,
                        CodePalette.CYAN to strings.paletteCyan,
                        CodePalette.CLASSIC_WHITE to strings.paletteClassicWhite
                    ).forEach { (palette, label) ->
                        val selected = selectedPalette == palette
                        FilterChip(
                            selected = selected,
                            onClick = { selectedPalette = palette },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(palette.fgColor))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                Text(strings.sizeTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CodeSize.entries.forEach { size ->
                        GeneratorOptionPill(
                            text = "${size.px}px",
                            selected = selectedSize == size,
                            onClick = { selectedSize = size },
                            useMono = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(strings.ecTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (isCode128) {
                    Text(
                        text = strings.code128ChecksumNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QrEcLevel.entries.forEach { ec ->
                            val label = when (ec) {
                                QrEcLevel.L -> "L 7%"
                                QrEcLevel.M -> "M 15%"
                                QrEcLevel.Q -> "Q 25%"
                                QrEcLevel.H -> "H 30%"
                            }
                            GeneratorOptionPill(
                                text = label,
                                selected = selectedEcLevel == ec,
                                onClick = { selectedEcLevel = ec },
                                useMono = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Export Actions (PNG Gallery, Vector SVG, Share, Print, Save to History)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = payload.isNotBlank() && generationError == null,
                onClick = {
                    scope.launch {
                        val fullBmp = withContext(Dispatchers.Default) {
                            OfflineCodeGenerator.generateBitmap(
                                payload = payload,
                                isCode128 = isCode128,
                                sizePx = selectedSize.px,
                                palette = selectedPalette,
                                ecLevel = selectedEcLevel
                            )
                        }
                        onSavePngToGallery(fullBmp, "naim_qr_${selectedType.name.lowercase()}")
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gen_export_png_button")
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportPngGallery)
            }

            FilledTonalButton(
                enabled = payload.isNotBlank() && generationError == null,
                onClick = {
                    svgLauncher.launch("naim_qr_${System.currentTimeMillis()}.svg")
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gen_export_svg_button")
            ) {
                Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportSvg)
            }

            FilledTonalButton(
                enabled = payload.isNotBlank() && generationError == null,
                onClick = {
                    scope.launch {
                        val fullBmp = withContext(Dispatchers.Default) {
                            OfflineCodeGenerator.generateBitmap(
                                payload = payload,
                                isCode128 = isCode128,
                                sizePx = selectedSize.px,
                                palette = selectedPalette,
                                ecLevel = selectedEcLevel
                            )
                        }
                        val uri = withContext(Dispatchers.IO) {
                            LocalExportHelper.createShareablePngUri(context, fullBmp, "naim_qr")
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, strings.exportShare))
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gen_share_button")
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportShare)
            }

            OutlinedButton(
                enabled = payload.isNotBlank() && generationError == null,
                onClick = {
                    scope.launch {
                        val fullBmp = withContext(Dispatchers.Default) {
                            OfflineCodeGenerator.generateBitmap(
                                payload = payload,
                                isCode128 = isCode128,
                                sizePx = selectedSize.px,
                                palette = CodePalette.CLASSIC_WHITE,
                                ecLevel = selectedEcLevel
                            )
                        }
                        LocalExportHelper.printBitmap(context, fullBmp, "Naim QR Print")
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gen_print_button")
            ) {
                Icon(Icons.Rounded.Print, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportPrint)
            }

            OutlinedButton(
                enabled = payload.isNotBlank() && generationError == null,
                onClick = {
                    onSaveToHistory(payload, isCode128, selectedEcLevel, generationMs)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("gen_save_history_button")
            ) {
                Icon(Icons.Rounded.BookmarkAdd, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.exportSaveHistory)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GeneratorOptionPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    useMono: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = if (useMono) JetBrainsMonoFontFamily else MaterialTheme.typography.labelMedium.fontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
