# Measure AR 📏

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![ARCore](https://img.shields.io/badge/Google-ARCore%201.45-FBBC04.svg?style=flat&logo=google)](https://developers.google.com/ar)
[![SceneView](https://img.shields.io/badge/SceneView-2.2.1-FF6F00.svg?style=flat)](https://github.com/SceneView/sceneview-android)
[![CI Build](https://github.com/MohamedNavith/Measure-AR/actions/workflows/build.yml/badge.svg)](https://github.com/MohamedNavith/Measure-AR/actions/workflows/build.yml)

A production-quality native Android augmented reality measurement app inspired by Apple's iOS Measure app. Built with **Kotlin 2.0**, **Jetpack Compose (Material 3)**, **ARCore 1.45**, and **SceneView (`io.github.sceneview:arsceneview:2.2.1`)** with Google Filament for 3D procedural geometry.

---

## 📥 Download APK

You can download the pre-compiled, debug-signed installable APK directly:

| Download Source | Link | Details |
|:---|:---|:---|
| **Direct APK (Repository)** | [⬇️ **Download `app-debug.apk`**](https://github.com/MohamedNavith/Measure-AR/raw/main/app-debug.apk) | Direct 1-click download (~49 MB) |
| **GitHub Releases** | [🏷️ **Latest Release Assets**](https://github.com/MohamedNavith/Measure-AR/releases) | Pre-release / GitHub Release page |
| **GitHub Actions** | [⚡ **Build Artifacts**](https://github.com/MohamedNavith/Measure-AR/actions) | Latest CI/CD workflow run artifact |

---

## ⚡ Prerequisites & Requirements

1. **Hardware**: An **ARCore-supported Android device** running **Android 7.0 (API level 24)** or higher.
   - [Official List of Supported ARCore Devices](https://developers.google.com/ar/devices)
2. **Software**: **Google Play Services for AR** must be installed and up-to-date on your device.
   - [Google Play Services for AR on Google Play](https://play.google.com/store/apps/details?id=com.google.ar.core)
3. **Permissions**: Camera access permission is required for real-world surface scanning and tracking.

---

## 📱 How to Install

### Option 1: Direct Phone Install
1. Click [Download `app-debug.apk`](https://github.com/MohamedNavith/Measure-AR/raw/main/app-debug.apk) from your phone's browser.
2. Open the downloaded file in your file manager or notification shade.
3. Allow **"Install unknown apps"** if prompted by Android.
4. Launch **Measure AR**, grant Camera permission, and begin measuring.

### Option 2: Install via ADB
```bash
adb install -r app-debug.apk
```

---

## ✨ Features

- **Instant AR Camera View**: Launches straight into full-screen AR after a camera-permission and ARCore-availability check.
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
├── app-debug.apk                   # Pre-compiled debug-signed APK
├── settings.gradle.kts             # Dependency repositories & plugin management
├── gradle.properties               # Memory & AndroidX configuration
├── build.gradle.kts                # Root build script
├── app/
│   ├── build.gradle.kts            # App module build script (Compose, Hilt, Room, SceneView)
│   ├── proguard-rules.pro          # ProGuard rules for ARCore, Filament, SceneView & Room
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions, ARCore metadata, FileProvider
│       │   ├── res/xml/
│       │   │   └── file_paths.xml  # FileProvider paths for sharing screenshots
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

### Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).