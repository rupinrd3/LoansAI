# Mobile App Setup

This guide explains how to prepare, configure, build, and run the Android application.

## Overview

The Android app depends on these backend services:

- Firebase
- Appwrite
- BRE service
- email verification backend
- optional AI provider keys

Recommended setup order:

1. Firebase
2. Appwrite
3. BRE service
4. email verification
5. Android build and install

## 1. Prerequisites

Install the following on your machine:

- Android Studio
- Android SDK Platform 35
- Android Build Tools 35
- Java 17
- Git

Optional but recommended:

- an Android device with USB debugging enabled
- an Android emulator with API 32 or higher

## 2. Open The Project

The Android app is located in:

```text
apps/mobile-android
```

Open this folder in Android Studio.

## 3. Create Local Android Config Files

This public-ready repo does not include private config files. You must create them locally.

### 3.1 `local.properties`

Android Studio usually creates this automatically.

If needed:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Place it in:

```text
apps/mobile-android/local.properties
```

### 3.2 `google-services.json`

After creating your Firebase Android app:

1. download `google-services.json`
2. place it here:

```text
apps/mobile-android/app/google-services.json
```

### 3.3 `keystore.properties`

This file is used locally for API keys and optionally release signing values.

Minimum useful example:

```properties
openai_api_key=YOUR_OPENAI_KEY
gemini_api_key=YOUR_GEMINI_KEY
brevo_api_key=YOUR_BREVO_KEY
```

Optional release signing values:

```properties
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Place it in:

```text
apps/mobile-android/keystore.properties
```

Do not commit it.

## 4. Review Environment-Specific Values In Source

Before building a real environment, inspect and update the backend values used by the app.

Start with:

- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/local/database/di/NetworkModule.kt`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/util/constants/ApiConstants.kt`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/di/AppwriteModule.kt`
- `apps/mobile-android/app/src/main/res/xml/network_security_config.xml`

Review and replace values such as:

- BRE base URL
- Appwrite endpoint
- Appwrite project ID
- Appwrite database ID
- any custom REST backend URLs used in your auth or PAN flows

## 5. Configure Backend Services First

Before running the full app, complete:

- `firebase-backend.md`
- `appwrite-admin-tools.md`
- `bre-python.md`
- `email-verification.md`

Without those services, the app may build but major flows will fail.

## 6. Sync Gradle

From Android Studio:

1. open the project
2. let Gradle sync run
3. accept Android SDK installation prompts
4. resolve any missing packages

From terminal:

```bash
cd apps/mobile-android
./gradlew tasks
```

## 7. Build Debug APK

```bash
cd apps/mobile-android
./gradlew assembleDebug
```

## 8. Install And Run Debug Build

To install directly:

```bash
cd apps/mobile-android
./gradlew installDebug
```

Or manually using ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 9. Validate Core Flows

After app launch, verify:

- login or verification flow opens correctly
- PAN screen works
- bureau-data retrieval works
- document upload works
- document extraction completes
- BRE-based offer flow works

## 10. Build Release APK Or Bundle

If you want a signed release build:

1. create your own Android signing keystore
2. update local signing values
3. review `app/build.gradle.kts`

Then build:

```bash
cd apps/mobile-android
./gradlew assembleRelease
```

Or build an app bundle:

```bash
cd apps/mobile-android
./gradlew bundleRelease
```

## 11. Troubleshooting

### Gradle build fails

Check:

- Java 17 is active
- Android SDK 35 is installed
- `local.properties` is present
- `google-services.json` exists

### App launches but backend flows fail

Check:

- Firebase setup
- Appwrite IDs and endpoint
- BRE URL
- API keys in `keystore.properties`

### AI features fail

Check:

- Gemini key
- OpenAI key if you use that path

### OTP/email flow fails

Check:

- Firebase Functions deployed
- Brevo config set
- sender email verified
