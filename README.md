# AI-Enabled Loan Application Mobile App

An educational Android project that demonstrates how AI can be integrated into a loan-application journey.

This project shows how a mobile loan app can combine:

- guided user onboarding
- PAN and bureau-data retrieval
- document upload and OCR
- AI-assisted document understanding
- obligation review
- rule-based loan-offer calculation
- OTP and email-based verification workflows

The goal of this repository is learning, experimentation, and hands-on understanding of how AI and backend services can support a digital lending flow. It is not financial, legal, underwriting, or production-compliance advice.

## Purpose

This project was built to explore questions like:

- how can AI help extract structured information from financial documents?
- how can a mobile app combine bureau data, user-declared data, and verified document data?
- how can a backend decision engine turn that information into a loan decision and indicative offer?
- how can services like Firebase, Appwrite, and email verification be connected inside a single product flow?

## What The App Does

The Android app walks a user through a loan-application experience that includes:

- sign-in and OTP-based verification
- PAN verification and bureau-data lookup
- personal and employment information capture
- document upload for salary slips, bank statements, and ITR
- OCR and AI-assisted extraction of structured fields from uploaded documents
- tradeline and EMI confirmation
- rule-based loan-offer generation
- final offer display and summary

## Key Features

### 1. AI-assisted document journey

- OCR using on-device text recognition
- AI-assisted extraction for salary slips, bank statements, ITR, PAN, and ID documents
- structured document outputs saved for downstream processing

### 2. Bureau-data integration

- Appwrite-backed bureau collections
- borrower summary, enquiries, and tradelines support
- bureau-based credit-score and obligation inputs in the loan flow

### 3. Loan decision engine

- direct Business Rules Engine (BRE) integration over HTTP
- rule-based decision output such as `AUTO_APPROVED`, `REJECTED`, or `REFER_TO_UNDERWRITER`
- indicative loan amount, tenure, and pricing outputs

### 4. Firebase-backed mobile backend

- Firestore for application and process state
- Storage for uploaded documents
- Functions for OTP and email workflows
- Crashlytics and analytics support

### 5. Email verification support

- OTP/email workflows through Firebase Functions
- Brevo-based transactional email sending

## Tech Stack

### Mobile app

- Kotlin
- Jetpack Compose
- Hilt
- Retrofit / OkHttp
- Firebase Android SDK
- ML Kit OCR
- WorkManager
- Appwrite Android SDK

### Backend services

- Firebase / Firestore / Storage / Functions
- Appwrite
- Python Flask BRE service
- Brevo email API

### AI integration

- Gemini API integration in the Android app
- OpenAI client dependencies present for experimentation and future extension

## Repository Structure

```text
apps/
  mobile-android/
services/
  bre-python/
  appwrite-admin-tools/
  firebase-support/
docs/
  architecture/
  setup/
  migration/
  reference/
```

## Main Components

### `apps/mobile-android`

The Android application.

### `services/bre-python`

The direct rule engine used to calculate loan decisions and indicative loan offers.

### `services/appwrite-admin-tools`

Scripts and notes for setting up the Appwrite database used for bureau data.

### `services/firebase-support`

Firebase Functions and helper scripts used for backend support tasks such as OTP and data operations.

## Setup Guides

Detailed setup instructions are here:

- [Mobile App Setup](./docs/setup/mobile-android.md)
- [Firebase Backend Setup](./docs/setup/firebase-backend.md)
- [BRE Service Setup](./docs/setup/bre-python.md)
- [Appwrite Setup](./docs/setup/appwrite-admin-tools.md)
- [Email Verification Setup](./docs/setup/email-verification.md)
- [Build And Install APK](./docs/setup/build-and-install.md)

## High-Level Setup Order

If you want to run the full project, use this order:

1. Create Firebase project and mobile app config
2. Set up Appwrite database and bureau collections
3. Deploy or run the BRE service
4. Configure Firebase Functions for OTP/email flows
5. Configure AI keys for document/assistant features
6. Build the Android APK and install it

## Educational Notes

This repository is intentionally useful for learners who want to understand:

- mobile-to-backend workflow design
- AI integration inside a user journey
- combining OCR, structured extraction, rules, and backend services
- how multiple systems can support a lending-style product flow

## Before You Publish Or Demo

Review:

- hardcoded endpoints
- project IDs
- Appwrite IDs
- Firebase configuration files
- API keys and email provider credentials

This repo excludes major local secret files, but you should still do a final secret and environment review before public release or deployment.
