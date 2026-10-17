1. Download `app-debug.apk` from `app/build/outputs/apk/debug/app-debug.apk` (or from the GitHub Actions `app-debug` artifact / GitHub Releases).
2. Install via ADB (`adb install -r app-debug.apk`) or transfer the APK to your Android phone and tap to install (allow "Install unknown apps" if prompted).
3. Hardware Requirement: Your phone must be an ARCore-supported Android device running Android 7.0 (API 24) or higher.
4. Software Requirement: Ensure "Google Play Services for AR" is installed and updated from the Google Play Store before launching.
5. Open "Measure AR", grant Camera permission, move your phone slowly over a well-lit textured surface until the center reticle turns green, and tap "+" to measure.