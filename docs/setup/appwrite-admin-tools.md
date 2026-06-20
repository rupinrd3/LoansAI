# Appwrite Setup

This guide explains how to create and configure the Appwrite database used for bureau-data retrieval.

## What Appwrite Is Used For

The app expects bureau-data collections for:

- borrower summary
- enquiries
- tradelines

These collections support bureau score, tradeline, and related credit-information flows in the app.

## 1. Prerequisites

You need:

- an Appwrite account
- an Appwrite project
- a database inside that project
- optionally, an Appwrite API key for automated setup

## 2. Create Appwrite Project And Database

1. create an Appwrite project
2. create a database dedicated to this app
3. note:
   - endpoint
   - project ID
   - database ID

You will need those values in both setup scripts and Android config.

## 3. Required Collections

Create these collections:

- `borrower_summary`
- `enquiries`
- `tradelines`

## 4. Setup Options

### Option A: Manual setup in Appwrite Console

Read:

```text
services/appwrite-admin-tools/appwrite_setup_readme.md
```

and create the collections, attributes, and indexes manually.

### Option B: Automated setup with script

Install dependencies:

```bash
cd services/appwrite-admin-tools
npm install
```

Create a local `.env` file:

```env
APPWRITE_ENDPOINT=https://cloud.appwrite.io/v1
APPWRITE_PROJECT_ID=your-project-id
APPWRITE_API_KEY=your-secret-api-key
APPWRITE_DATABASE_ID=your-database-id
```

Run:

```bash
node setup-appwrite-collections.js
```

## 5. Upload Sample Bureau Data

If you want test data, review:

- `services/appwrite-admin-tools/upload-report.js`
- `services/appwrite-admin-tools/geeta_cibil_report.json`

This can help you populate a sample borrower report for app testing.

## 6. Update Android App Config

Review:

- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/util/constants/ApiConstants.kt`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/di/AppwriteModule.kt`

Update:

- Appwrite endpoint
- project ID
- database ID
- collection IDs if you changed the defaults

## 7. Validate Appwrite Integration

After setup:

1. run the app
2. complete PAN/bureau steps
3. confirm borrower summary retrieval
4. confirm tradeline retrieval
5. confirm the app behaves correctly when data exists and when it does not

## 8. Security Notes

- do not commit `.env`
- do not expose admin API keys to the Android client
- only use the admin key in server-side setup scripts
