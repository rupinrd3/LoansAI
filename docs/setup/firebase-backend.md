# Firebase Backend Setup

This guide covers Firebase project creation, Firestore, Storage, Functions, Android integration, and security rules.

## What Firebase Is Used For

Firebase supports the following parts of the project:

- Android app registration
- Firestore database
- Storage for uploaded documents
- Functions for OTP/email workflows
- Authentication
- Crashlytics and analytics support

## 1. Prerequisites

Install:

- Node.js 20 or newer recommended
- npm
- Firebase CLI
- Java 17

Install Firebase CLI:

```bash
npm install -g firebase-tools
```

Log in:

```bash
firebase login
```

## 2. Create A Firebase Project

1. Open Firebase Console
2. Create a new project
3. Enable Google Analytics if you want analytics reporting

Record your Firebase project ID. You will need it for deployment and Android configuration.

## 3. Register The Android App

1. In Firebase Console, add an Android app
2. Use your package name from:

```text
com.loansai.unassisted
```

3. Download `google-services.json`
4. Copy it to:

```text
apps/mobile-android/app/google-services.json
```

## 4. Enable Authentication

Open Firebase Console and enable the sign-in methods you plan to use.

If your flow is phone or custom OTP based, configure the related auth providers appropriately for your environment.

## 5. Create Firestore Database

1. Open Firestore Database in Firebase Console
2. Create database
3. Start in your selected region
4. Choose production mode if you want to define real rules immediately

## 6. Suggested Firestore Collections

This app may create or use collections such as:

- `applications`
- `documents`
- `application-documents`
- `loan_offers`
- `bre_input`
- `bre_output`
- `otps`

Depending on your experiments and helper flows, additional collections may appear.

## 7. Firestore Rules

You should define your own security rules before any real deployment.

The repo contains one Firebase service area at:

```text
services/firebase-support/functions-ver4
```

There is not yet a polished public rules file in this curated repo, so start with a minimal development rule set and harden it later.

### Example development-only Firestore rules

Use this only for local testing or controlled learning environments:

```txt
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Stronger direction for real use

For a more realistic setup, split rules by collection and user ownership:

- users can read/write only their own application
- document records should be tied to owning user or application
- OTP records should only be written by backend functions
- loan offers should be read by the related applicant only

Before public demonstration or real usage, replace the development rule set with collection-specific rules.

## 8. Create Firebase Storage

1. Open Storage in Firebase Console
2. Create the bucket in the same region as your project if possible

The app uses Storage for uploaded document files.

## 9. Storage Rules

For early development, you can start with authenticated-only access.

### Example development-only Storage rules

```txt
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

As with Firestore, tighten these before real usage.

## 10. Firebase Functions Setup

Functions are located in:

```text
services/firebase-support/functions-ver4/functions
```

Install dependencies:

```bash
cd services/firebase-support/functions-ver4/functions
npm install
```

Build:

```bash
npm run build
```

## 11. Deploy Firebase Functions

Set the project:

```bash
cd services/firebase-support/functions-ver4
firebase use YOUR_PROJECT_ID
```

Deploy:

```bash
firebase deploy --only functions
```

## 12. Firebase Functions Runtime Config

Review:

```text
services/firebase-support/functions-ver4/functions/src/index.ts
```

This code expects runtime config, especially for Brevo.

Example:

```bash
firebase functions:config:set \
  brevo.key="YOUR_BREVO_API_KEY" \
  brevo.from="verified-sender@example.com" \
  brevo.to="alerts@example.com"
```

If you also use model-related backend settings, configure those after reviewing the code.

## 13. Local Emulator Flow

To run Functions locally:

```bash
cd services/firebase-support/functions-ver4/functions
npm install
npm run build
firebase emulators:start --only functions
```

## 14. Validate Firebase Integration

After setup:

1. run the Android app
2. confirm login/auth flow initializes
3. confirm Firestore documents are created
4. confirm Storage uploads work
5. confirm Functions can be called where needed

## 15. Deployment Recommendations

- use the same region where practical for Firestore, Functions, and Storage
- keep project IDs and service credentials out of public commits
- replace broad development rules with collection-specific rules before wider use
