# Measure AR 📏

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![ARCore](https://img.shields.io/badge/Google-ARCore%201.45-FBBC04.svg?style=flat&logo=google)](https://developers.google.com/ar)
[![SceneView](https://img.shields.io/badge/SceneView-2.2.1-FF6F00.svg?style=flat)](https://github.com/SceneView/sceneview-android)
[![CI Build](https://github.com/MohamedNavith/Measure-AR/actions/workflows/build.yml/badge.svg)](https://github.com/MohamedNavith/Measure-AR/actions/workflows/build.yml)
[![Signature Scheme](https://img.shields.io/badge/Signature-Scheme%20v2%20%2B%20v3-success.svg?style=flat&logo=google-play)](https://source.android.com/docs/security/features/apksigning)

A production-quality native Android augmented reality measurement app inspired by Apple's iOS Measure app. Built with **Kotlin 2.0**, **Jetpack Compose (Material 3)**, **ARCore 1.45**, and **SceneView (`io.github.sceneview:arsceneview:2.2.1`)** with Google Filament for 3D procedural geometry.

---

## 📥 Download APK (Direct 1-Click Links)

Download the signed, installable APK directly to your phone:

| Package | Download Link | Signature | Size | Recommended For |
|:---|:---|:---|:---|:---|
| **Release APK (Recommended)** | [⬇️ **Download `app-release.apk`**](https://github.com/MohamedNavith/Measure-AR/raw/main/app-release.apk) | Verified v2 + v3 Schemes | ~43 MB | **Daily Use & Performance** (Cleanest installation) |
| **Debug APK** | [⬇️ **Download `app-debug.apk`**](https://github.com/MohamedNavith/Measure-AR/raw/main/app-debug.apk) | Signed Development Build | ~49 MB | **Developers & Testing** |
| **GitHub Releases** | [🏷️ **Latest GitHub Releases**](https://github.com/MohamedNavith/Measure-AR/releases) | Standalone Release Assets | — | All Release Versions |

---

## 🛡️ Security, Privacy & Device Safety Guarantees

We engineered Measure AR with a strict zero-trust security baseline so it installs cleanly and safely on your device:

1. **Zero Internet & Zero Tracking**:
   - The app has **NO internet permission** (`android.permission.INTERNET` is completely absent from the manifest).
   - All spatial calculations, plane estimations, and images remain **100% on your device**. No telemetry, no background upload, no ads, and no analytics.
2. **Official APK Signature Scheme (v2 + v3)**:
   - Both builds are signed with a dedicated 2048-bit RSA certificate valid for 10,000+ days using modern **APK Signature Scheme v2 and v3**, avoiding "corrupted package" or "unrecognized certificate" errors.
3. **Data Extraction & Backup Protection**:
   - Configured with `android:allowBackup="false"`, `data_extraction_rules.xml`, and `backup_rules.xml` preventing unauthorized local database extraction via ADB backup.
4. **App Launcher & Drawer Visibility**:
   - Contains a complete Android adaptive vector icon (`@mipmap/ic_launcher` with background + foreground vectors) and explicit `<category android:name="android.intent.category.DEFAULT" />`.
   - `android.hardware.camera.ar` is configured safely to prevent phone OEM launchers from hiding the app icon on devices that obtain ARCore via Google Play Services for AR.
5. **Scoped Storage & File Sharing**:
   - Screenshots shared via the Android Share sheet use an unexposed, strictly internal `FileProvider` scoped exclusively to private cache images.

---

## ⚡ Prerequisites & Requirements

1. **Hardware**: An **ARCore-supported Android device** running **Android 7.0 (API level 24)** or higher.
   - [Official List of Supported ARCore Devices](https://developers.google.com/ar/devices)
2. **Software**: **Google Play Services for AR** must be installed and up-to-date on your device.
   - [Google Play Services for AR on Google Play Store](https://play.google.com/store/apps/details?id=com.google.ar.core)
3. **Permissions**: Camera access permission is requested on first launch to scan real-world surfaces.

---

## 📱 How to Install

### Option 1: Direct Install on Android Phone
1. Tap [Download `app-release.apk`](https://github.com/MohamedNavith/Measure-AR/raw/main/app-release.apk) from your phone's browser.
2. Open the downloaded file from your Downloads folder or notification shade.
3. If prompted by Android:
   - Select **"Settings"** -> Enable **"Allow from this source"** for your browser/file manager.
   - If Play Protect displays *"Unrecognized developer"*, tap **"More details"** -> **"Install anyway"** (normal for open-source sideloaded APKs).
4. Launch **Measure AR** directly from your app drawer.

### Option 2: Install via ADB
```bash
adb install -r app-release.apk
```

---

## ✨ Features

- **Instant AR Camera View**: Launches straight into full-screen AR after smooth camera-permission and ARCore-availability checks.
- **Subtle Surface Dot Grid**: Detects horizontal and vertical surfaces and projects an iOS-style discrete dot mesh over tracked planes.
- **Animated Center Reticle**: Real-time crosshair that dynamically springs from white to green when locked onto a tracked plane.
- **Procedural 3D Geometry**:
  - Anchors 3D sphere markers at exact real-world coordinates.
  - Renders 3D cylinders connecting points in real time using SceneView and Google Filament.
- **Floating Distance Badges**: Projects 3D segment midpoints to 2D screen coordinates with zero lag, displaying pill-shaped distance badges with tabular-nums typography.
- **Three Measuring Modes**:
  1. **Standard Distance**: Measures 3D Euclidean distance between Point A and Point B.
  2. **Chain / Area Mode**: Multi-point polyline chaining for perimeter; automatically computes enclosed 3D polygon area for 4+ points using Newell's method.
  3. **Height Mode**: Anchors a base point on the floor and projects a vertical ray locked in the X/Z plane to measure true vertical heights.
- **Persistent Unit Toggle**: Quick-cycle and toggle between `cm`, `m`, `in`, and `ft & in` on the fly, persisted in Jetpack DataStore.
- **Tracking Quality Hints**: Contextual instruction pills for environmental conditions: *"Move phone slowly"*, *"Find a textured surface"*, *"Too dark"*, etc.
- **Undo & Clear**: Undo points step-by-step or clear all placed anchors and segments with one tap.
- **PixelCopy AR Frame Sharing**: Captures the live OpenGL surface, burns in measurement lines, markers, and distance badges, and launches the native Android share sheet.
- **Measurement History**: Room database persistence with timestamps, thumbnail previews, swipe-to-delete, and multi-unit breakdown dialog.
- **Dark Glassmorphic UI**: Translucent blurred dark surfaces, 24dp+ rounded corners, spring physics animations, and TalkBack content descriptions.

---

## 📐 Mathematical Model & State Machine

### 3D Euclidean Distance
$$d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2 + (z_2 - z_1)^2}$$

### 3D Polygon Area (Newell's Method / Triangle Fan)
$$\text{Area} = \frac{1}{2} \sum_{i=1}^{n-2} \| (\vec{P}_i - \vec{P}_0) \times (\vec{P}_{i+1} - \vec{P}_0) \|$$

### Pure State Machine
The `MeasureViewModel` is completely decoupled from Android or ARCore framework types:
```
[Searching] 
     │  (Plane detected)
     ▼
[ReadyToPlaceA] 
     │  (Tap shutter +)
     ▼
[PlacedA] 
     │  (Move reticle > 5mm)
     ▼
[Live] ───────────┐
     │            │ (Tap shutter + to commit Point B)
     │            ▼
     └───────> [Done] (Lock measurement -> Save / Share / Undo / New)
```

---

## 🏗️ Architecture & Project Structure

The project follows Clean Architecture and MVVM patterns with Dagger Hilt dependency injection:

```
MeasureAR/
├── .github/workflows/build.yml     # Automated CI/CD (Tests, APK Build & GitHub Releases)
├── app-release.apk                 # Production-signed release APK (v2 + v3 scheme)
├── app-debug.apk                   # Signed debug APK
├── settings.gradle.kts             # Dependency repositories & plugin management
├── gradle.properties               # Memory & AndroidX configuration
├── build.gradle.kts                # Root build script
├── app/
│   ├── build.gradle.kts            # App module build script (Compose, Hilt, Room, SceneView)
│   ├── proguard-rules.pro          # ProGuard rules for ARCore, Filament, SceneView & Room
│   ├── measure_ar_release.keystore # Dedicated 2048-bit RSA signing keystore
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions, ARCore metadata, FileProvider, Security
│       │   ├── res/
│       │   │   ├── drawable/       # Adaptive vector icons (background, foreground, layer-list)
│       │   │   ├── mipmap-anydpi-v26/ # Modern adaptive launcher icons
│       │   │   ├── values/         # strings.xml, themes.xml
│       │   │   └── xml/
│       │   │       ├── file_paths.xml             # FileProvider paths for sharing screenshots
│       │   │       ├── data_extraction_rules.xml   # Android 12+ backup protection
│       │   │       ├── backup_rules.xml           # Legacy backup protection
│       │   │       └── network_security_config.xml # Cleartext traffic blocking
│       │   └── java/com/example/measurear/
│       │       ├── MeasureArApplication.kt
│       │       ├── MainActivity.kt # Edge-to-edge Compose navigation
│       │       ├── ar/
│       │       │   ├── DistanceCalculator.kt # Pure math (Euclidean, Height, Area, Polyline)
│       │       │   ├── ArMeasureSession.kt   # ARCore frame processing & raycasting
│       │       │   └── ArLineRenderer.kt     # SceneView 3D nodes & 2D frustum projection
│       │       ├── data/
│       │       │   ├── MeasurementEntity.kt   # Room database entity
│       │       │   ├── MeasurementDao.kt      # Room DAO
│       │       │   ├── AppDatabase.kt         # Room database definition
│       │       │   ├── MeasurementRepository.kt
│       │       │   └── SettingsRepository.kt  # Jetpack DataStore preferences
│       │       ├── di/
│       │       │   └── AppModule.kt           # Hilt dependency injection module
│       │       ├── util/
│       │       │   ├── UnitConverter.kt       # Unit conversions & formatters
│       │       │   └── BitmapShareHelper.kt   # PixelCopy capture & overlay burning
│       │       └── ui/
│       │           ├── theme/Theme.kt         # Compose Material 3 dark/light theme
│       │           ├── components/GlassmorphicComponents.kt # Reticle, Shutter, Cards
│       │           ├── camera/
│       │           │   ├── MeasureViewModel.kt      # Pure StateFlow state machine
│       │           │   └── MeasureCameraScreen.kt   # Full-screen ARSceneView interface
│       │           ├── history/HistoryScreen.kt     # Room history with swipe-to-delete
│       │           └── settings/SettingsSheet.kt    # Preferences modal bottom sheet
│       └── test/java/com/example/measurear/
│           ├── DistanceCalculatorTest.kt            # Unit tests for 3D math & geometry
│           └── UnitConverterTest.kt                 # Unit tests for unit conversion logic
└── README.md
```

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 17** or **JDK 21**
- **Android SDK** with Platform 35 and Build-Tools 35.0.0
- **Android Studio Ladybug (2024.2.1+)** or command line

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Release & Debug APKs
```bash
./gradlew assembleRelease assembleDebug
```
The resulting APKs will be generated at:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).