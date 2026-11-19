package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.ScannerPreferences
import com.example.scanner.OfflineBarcodeDecoder
import com.example.ui.components.ScannerReticleOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    isScanningPaused: Boolean,
    isDecodingImage: Boolean,
    preferences: ScannerPreferences,
    onBarcodeDetected: (rawValue: String, formatCode: Int) -> Unit,
    onImageSelected: (Uri) -> Unit,
    onToggleAutoCopy: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission && cameraError == null) {
            CameraPreviewWithMlKit(
                useFrontCamera = useFrontCamera,
                isTorchEnabled = isTorchEnabled,
                zoomRatio = zoomRatio,
                isScanningPaused = isScanningPaused,
                onBarcodeDetected = onBarcodeDetected,
                onCameraError = { msg -> cameraError = msg },
                modifier = Modifier.fillMaxSize()
            )
            ScannerReticleOverlay(
                isActive = !isScanningPaused && !isDecodingImage,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraUnavailableFallback(
                permissionDenied = !hasCameraPermission,
                errorMessage = cameraError,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImportImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            )
        }

        // Top Floating Studio HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0C121B).copy(alpha = 0.78f),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = CircleShape
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (preferences.continuousMode) "OFFLINE • CONTINUOUS" else "OFFLINE • ALL FORMATS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            FilledTonalIconButton(
                onClick = { showSettingsSheet = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF0C121B).copy(alpha = 0.78f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("scanner_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Scanner settings"
                )
            }
        }

        // Bottom Studio Controls Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasCameraPermission && cameraError == null) {
                Text(
                    text = stringResource(R.string.scan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )

                // Quick zoom selector pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1f to "1×", 1.8f to "1.8×", 3f to "3×").forEach { (ratio, label) ->
                        val selected = kotlin.math.abs(zoomRatio - ratio) < 0.1f
                        Surface(
                            onClick = { zoomRatio = ratio },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFF0C121B).copy(alpha = 0.72f)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Primary Action Dock: Torch | Import Image | Flip Camera
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0E141E).copy(alpha = 0.88f),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { isTorchEnabled = !isTorchEnabled },
                        enabled = hasCameraPermission && !useFrontCamera && cameraError == null,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isTorchEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            },
                            contentColor = if (isTorchEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                Color.White
                            }
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("torch_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = stringResource(R.string.torch_toggle)
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_from_image))
                    }

                    FilledTonalIconButton(
                        onClick = {
                            useFrontCamera = !useFrontCamera
                            if (useFrontCamera) isTorchEnabled = false
                        },
                        enabled = hasCameraPermission && cameraError == null,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = stringResource(R.string.camera_flip)
                        )
                    }
                }
            }
        }

        // Loading overlay while decoding an imported photo
        AnimatedVisibility(
            visible = isDecodingImage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "Decoding image offline…",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        BackHandler { showSettingsSheet = false }
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            ScannerSettingsSheetContent(
                preferences = preferences,
                onToggleAutoCopy = onToggleAutoCopy,
                onToggleHaptic = onToggleHaptic,
                onToggleContinuous = onToggleContinuous
            )
        }
    }
}

@Composable
private fun CameraPreviewWithMlKit(
    useFrontCamera: Boolean,
    isTorchEnabled: Boolean,
    zoomRatio: Float,
    isScanningPaused: Boolean,
    onBarcodeDetected: (String, Int) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val barcodeScanner = remember { OfflineBarcodeDecoder.createAllFormatScanner() }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    LaunchedEffect(useFrontCamera) {
        runCatching {
            cameraController.cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        }.onFailure {
            onCameraError("Camera lens unavailable on this device.")
        }
    }

    LaunchedEffect(isTorchEnabled) {
        runCatching { cameraController.enableTorch(isTorchEnabled) }
    }

    LaunchedEffect(zoomRatio) {
        runCatching { cameraController.setZoomRatio(zoomRatio) }
    }

    DisposableEffect(lifecycleOwner, barcodeScanner) {
        val analyzer = MlKitAnalyzer(
            listOf(barcodeScanner),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor
        ) { result ->
            if (isScanningPaused) return@MlKitAnalyzer
            val barcodes = result?.getValue(barcodeScanner).orEmpty()
            val firstMatch = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() || !it.displayValue.isNullOrBlank() }
            if (firstMatch != null) {
                val payload = firstMatch.rawValue ?: firstMatch.displayValue.orEmpty()
                if (payload.isNotBlank()) {
                    onBarcodeDetected(payload, firstMatch.format)
                }
            }
        }
        cameraController.setImageAnalysisAnalyzer(mainExecutor, analyzer)
        runCatching {
            cameraController.bindToLifecycle(lifecycleOwner)
        }.onFailure { err ->
            Log.e("ScanVaultCamera", "Camera bind failed", err)
            onCameraError("Camera preview could not be started on this device.")
        }

        onDispose {
            runCatching {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                barcodeScanner.close()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
        modifier = modifier
    )
}

@Composable
private fun CameraUnavailableFallback(
    permissionDenied: Boolean,
    errorMessage: String?,
    onRequestPermission: () -> Unit,
    onImportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = if (permissionDenied) {
                        stringResource(R.string.camera_permission_title)
                    } else {
                        "Camera Preview Unavailable"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = errorMessage ?: stringResource(R.string.camera_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (permissionDenied) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("grant_camera_button")
                    ) {
                        Text(stringResource(R.string.grant_camera_permission))
                    }
                }

                OutlinedButton(
                    onClick = onImportImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("fallback_import_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan from Photo Library")
                }
            }
        }
    }
}

@Composable
private fun ScannerSettingsSheetContent(
    preferences: ScannerPreferences,
    onToggleAutoCopy: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleContinuous: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Offline Scanner Preferences",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "All decoding and history storage run locally on this device with zero network access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PreferenceToggleRow(
            title = "Auto-copy on scan",
            subtitle = "Immediately copy the primary result (link, password, or code) to clipboard",
            checked = preferences.autoCopyOnScan,
            onCheckedChange = onToggleAutoCopy,
            tag = "setting_auto_copy"
        )
        PreferenceToggleRow(
            title = "Haptic confirmation",
            subtitle = "Vibrate briefly when a QR code or barcode is recognized",
            checked = preferences.hapticFeedback,
            onCheckedChange = onToggleHaptic,
            tag = "setting_haptic"
        )
        PreferenceToggleRow(
            title = "Continuous batch scan",
            subtitle = "Keep scanning without pausing; saves every new code straight to history",
            checked = preferences.continuousMode,
            onCheckedChange = onToggleContinuous,
            tag = "setting_continuous"
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
