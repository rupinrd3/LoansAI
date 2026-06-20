# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Firebase admin utility script for bulk deletion of Firestore data and Firebase Storage files for a loan application system. The script targets the "loansai" Firebase project and is designed to clean up application data across multiple collections while preserving the collection structure.

## Running the Script

```bash
node delete-application.js
```

The script requires:
- Firebase Admin SDK credentials in `serviceAccountKey.json`
- Internet connection to reach Firebase services
- Proper IAM permissions for the service account

## Architecture

### Core Components

- **Main deletion orchestrator**: `clearAllCollections()` function coordinates the entire cleanup process
- **Collection cleaner**: `clearCollection()` handles batch deletion of Firestore documents with pagination
- **Storage cleaner**: `deleteAllApplicationFiles()` and `deleteDirectory()` handle Firebase Storage file deletion
- **Batch processing**: Uses Firestore batch operations with 450 operation limit for efficient deletion

### Target Collections

The script clears these Firestore collections:
- `applications` (includes nested `metadata` subcollections)
- `documents` (also deletes associated storage files)
- `otps`
- `employment_verifications`
- `application-documents`

### Storage Cleanup

- Deletes all files under the `applications/` storage prefix
- Extracts and deletes individual files referenced in `documents` collection via `storageUrl` field
- Progress tracking for large file operations

### Error Handling

- Continues processing if individual file deletions fail
- Logs errors but doesn't halt the entire operation
- Provides comprehensive summary of deletion results

## Configuration

Key configuration constants:
- `COLLECTIONS_TO_CLEAR`: Array of collection names to process
- `MAX_BATCH_SIZE`: 450 operations per Firestore batch (safety margin from 500 limit)
- Storage bucket: `loansai.appspot.com`

## Security Notes

The `serviceAccountKey.json` contains sensitive Firebase service account credentials and should never be committed to version control.