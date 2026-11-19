# ScanVault

A 100% offline Android QR code and barcode scanner built with Kotlin, Jetpack Compose, CameraX, on-device ML Kit Barcode Scanning, and Room local storage.

## Features
- **Purely Offline**: Zero AI APIs, zero cloud services, zero network permissions required.
- **All Standard Formats**: QR Code, Data Matrix, Aztec, PDF417, EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Code 93, Codabar, and ITF.
- **Smart Local Parsing**: Automatically extracts URLs, Wi-Fi credentials (`WIFI:`), 2FA/OTP accounts (`otpauth://`), contacts (vCard / MeCard), email/phone/SMS/geo intents, product barcodes, and plain text.
- **Camera & Photo Picker**: Real-time CameraX viewfinder with pinch/slider zoom, flashlight toggle, front/rear camera switch, and zero-permission Android Photo Picker image scanning.
- **One-Tap Copy & Actions**: Copy full payloads or extracted fields (Wi-Fi password, 2FA secret, URL, product code) in one tap, or open links/connect/dial locally.
- **Local Scan History**: Persisted strictly on-device in Room SQLite with instant search, category filter chips, swipe/single delete, and clear-all confirmation.

## Building an APK from GitHub on Your Phone
1. Push this repository to GitHub.
2. Open the **Actions** tab on GitHub (mobile browser or GitHub app) and select **Build ScanVault Android APK**.
3. Open the latest workflow run (or tap **Run workflow**) and download the **`ScanVault-debug-apk`** artifact directly to your phone.
