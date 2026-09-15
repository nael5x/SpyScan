# SpyScan

**Android security awareness application · Kotlin**

SpyScan is a final-year software engineering project that helps users inspect app usage, permission-based risk indicators, and Android device settings through charts and readable reports. The current interface is primarily Turkish.

## Implemented source features

- Daily app usage charts, recent sessions, and usage lookup by app name.
- Weighted risk scores based on requested sensitive permissions.
- Accessibility-service and overlay-permission reports.
- Installer-source checks for apps not identified as installed by Google Play.
- Heuristic root checks and USB debugging status.
- Light and dark theme resources.

These are awareness indicators, not proof that an app is malicious. Requested permissions are not necessarily granted permissions; non-Play installations are not necessarily unsafe; root heuristics can misclassify devices. SpyScan is not an antivirus or malware-removal tool.

## Technology

| Area | Implementation |
| --- | --- |
| UI | Kotlin, XML layouts, AppCompat, Material Components |
| System APIs | UsageStatsManager, UsageEvents, PackageManager, AppOpsManager, Settings |
| Charts | MPAndroidChart |
| Build | Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin plugin 2.0.10 |
| Targets | minSdk 21, compileSdk/targetSdk 34, Java/Kotlin JVM target 17 |

## Open and build

1. Clone the repository and open its root in Android Studio.
2. Configure JDK 17 and Android SDK Platform 34.
3. Set the local SDK path through Android Studio or `ANDROID_HOME`.
4. Sync Gradle dependencies.

Build on macOS/Linux with: `./gradlew assembleDebug`

Build on Windows with: `gradlew.bat assembleDebug`

Expected APK location: `app/build/outputs/apk/debug/app-debug.apk`

The app opens Android's Usage Access settings when access is missing. Android version, vendor restrictions, package visibility, and special permissions affect the data available.

## Source layout

- `app/src/main/java/com/example/spyscan/MainActivity.kt`: usage aggregation, charts, and awareness checks.
- `app/src/main/res/`: XML layouts, strings, and themes.
- `app/src/main/AndroidManifest.xml`: activity, usage-access permission, and package-visibility configuration.
- `app/src/test/` and `app/src/androidTest/`: example test scaffolding.

## Status and verification

Developed as a final-year software engineering project.

The previously identified `MainActivity` class-name mismatch and missing `PACKAGE_USAGE_STATS` manifest declaration have been corrected.

Before treating the repository as a verified release, run:

- macOS/Linux: `./gradlew assembleDebug`
- macOS/Linux tests: `./gradlew testDebugUnitTest`
- Windows: `gradlew.bat assembleDebug`
- Windows tests: `gradlew.bat testDebugUnitTest`

The example tests currently provide limited coverage and do not fully validate the security-awareness logic. Device-level behavior should also be verified on real Android devices because Android version, vendor restrictions, package visibility, and special-permission behavior can affect results.

No malware-detection accuracy or production release-readiness claim is made.

## Author

Muhammet Nail Farfur — [GitHub](https://github.com/nael5x)
