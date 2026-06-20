# System Overview

## Components

### Mobile App

Location:

- `apps/mobile-android`

Responsibilities:

- user onboarding and application capture
- PAN and bureau flow
- document upload and extraction orchestration
- BRE request preparation
- final offer presentation

### Appwrite

Location in this repo:

- `services/appwrite-admin-tools`

Role:

- bureau-related collections and setup material
- borrower summary
- enquiries
- tradelines

### Firebase / Firestore

Location in this repo:

- `services/firebase-support`

Role in the working path:

- application records
- uploaded document records
- extracted document data
- BRE output persistence
- loan-offer persistence

### Direct BRE

Location:

- `services/bre-python`

Role:

- evaluate business rules
- compute loan decision status
- compute loan-offer values
- respond over direct HTTP

## Working Runtime Flow

1. User fills the application in the Android app.
2. PAN and bureau data are resolved, including Appwrite-backed bureau information.
3. Documents are uploaded and extraction results are stored in Firestore.
4. The app assembles normalized BRE input, including `documentExtractions`.
5. The app calls the BRE Cloud Run endpoint directly.
6. The app receives decision and offer values.
7. The app writes BRE output and loan-offer data back to Firestore.

## Why BRE Is Direct

The working app snapshot contains code for direct BRE HTTP calls and app-side Firestore persistence around BRE output. That is the basis for the active architecture in this repo.

## Historical But Not Active

- MCP experiments
- Camunda orchestration experiments

Those are part of the broader workspace history, but not part of this repo’s chosen working baseline.
