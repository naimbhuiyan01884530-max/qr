# Naim QR

A 100% offline Android QR & Barcode studio built with Kotlin, Jetpack Compose, CameraX, on-device ML Kit Barcode Scanning, ZXing Core, and Room SQLite.

## Features
- **Purely Offline**: Zero AI APIs, zero cloud services, zero network permissions.
- **Instant Decode & Quick Action Sheet**: Supports URL, Wi-Fi (SSID & password), vCard contacts, phone, email, location (`geo:`), product barcodes (`EAN-13`, `UPC`, `Code-128`, etc.), 2FA/OTP, and text. Includes one-tap Copy, Open in Browser, Wi-Fi Settings, Call, Save Contact, Share, Save QR Image to Gallery, offline personal notes, and technical parameters (format, data size, error correction, offline processing ms).
- **Offline Scan Vault**: Local Room database with search, category & bookmark filters, JSON backup, CSV export, and JSON restore.
- **Offline QR & Code-128 Generator**: Live preview for Links, Text, Wi-Fi, vCard, and Code-128 with Neon Mint / Cyan / Classic White palettes, `512px` / `1024px` / `2048px` sizes, `L/M/Q/H` error correction, and PNG Gallery, Vector SVG, Share, Print, and Vault exports.
- **One-Tap Dark/Light Mode & Bilingual UI (`বাং / EN`)**: Instant theme and Bangla/English switching from the top bar.

## Building an APK from GitHub on Your Phone
1. Push this repository to GitHub.
2. Open the **Actions** tab on GitHub and select **Build ScanVault Android APK**.
3. Download **`ScanVault-debug-apk`** from the workflow run artifacts directly onto your phone.
