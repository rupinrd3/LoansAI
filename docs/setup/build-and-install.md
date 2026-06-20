# Build And Install APK

This guide explains how to generate an APK and install it on a device.

## 1. Prerequisites

Make sure you already completed:

- Firebase setup
- Appwrite setup
- BRE deployment
- Android local config setup

Required local files:

- `apps/mobile-android/local.properties`
- `apps/mobile-android/app/google-services.json`

Optional but useful:

- `apps/mobile-android/keystore.properties`

## 2. Build Debug APK

```bash
cd apps/mobile-android
./gradlew assembleDebug
```

Typical output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 3. Install Debug APK

With a device connected:

```bash
cd apps/mobile-android
./gradlew installDebug
```

Or manually:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Run On Emulator

1. start an emulator from Android Studio
2. run the app from Android Studio

Or use:

```bash
cd apps/mobile-android
./gradlew installDebug
```

## 5. Create A Signed Release Build

This repo does not ship a release keystore.

To make a release build:

1. create your own keystore
2. configure signing values locally
3. review signing config in `app/build.gradle.kts`

Example commands:

```bash
cd apps/mobile-android
./gradlew assembleRelease
```

Or app bundle:

```bash
./gradlew bundleRelease
```

## 6. Install Release APK

If you produced a signed release APK:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 7. Verify A Working Install

Check:

- app launches without immediate crash
- login/verification screen opens
- PAN and bureau flow works
- document upload works
- offer generation works

## 8. If Build Or Install Fails

### Build failure

Check:

- Java 17
- Android SDK 35
- `google-services.json`
- `local.properties`
- Gradle sync

### Install failure

Check:

- device storage
- USB debugging enabled
- same app package already installed with mismatched signature

### Runtime backend failure

Check:

- Firebase project setup
- Appwrite setup
- BRE endpoint value
- API keys and backend configuration
