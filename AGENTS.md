# AGENTS.md

## Repo Purpose

This repository contains the cleaned working baseline for the loan application project.

## Active Architecture

- Android app in `apps/mobile-android`
- Direct BRE service in `services/bre-python`
- Appwrite bureau-data setup in `services/appwrite-admin-tools`
- Firebase support and legacy utility code in `services/firebase-support`

## Source Of Truth

- Mobile app baseline: `apps/mobile-android`
- Working BRE baseline: `services/bre-python`
- Working data dependencies: Firebase/Firestore plus Appwrite

## Important Working Assumptions

- The final working app path does not depend on MCP.
- The final working app path does not depend on Camunda orchestration.
- Appwrite was used for bureau collections and tradeline access.
- Firebase/Firestore was used for application, document, BRE output, and loan-offer state.

## Where To Look First

- `apps/mobile-android/app/build.gradle.kts`
- `apps/mobile-android/app/src/main/AndroidManifest.xml`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/local/database/di/NetworkModule.kt`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/repository/BRERepositoryImpl.kt`
- `apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/repository/PANRepositoryImpl.kt`
- `services/bre-python/worker.py`
- `services/appwrite-admin-tools/appwrite_setup_readme.md`

## Publishing Rules

- Do not commit credentials, local env files, signing material, or service account files.
- Treat any hardcoded keys or project IDs as publish-review items.
- Keep MCP and Camunda material out of the active baseline unless intentionally reintroduced later.

## Git Tracking & Security Audit (June 2026)

- **Git Status**: Initialized and linked to remote `https://github.com/rupinrd3/LoansAI.git`. Default branch is configured as `main`.
- **Exclusion Policy**: Highly sensitive files (e.g., `google-services.json`, Firebase service account keys, `.env` files, keystores, and `local.properties`) are ignored via `.gitignore` and are not tracked.
- **Dormant/Misplaced Code**: A TypeScript reference file `backend-llm-functions.ts` remains in the Android project package tree (`apps/mobile-android/app/src/main/java/com/loansai/unassisted/backend-llm-functions.ts`). This code represents the Firebase Functions implementation but is ignored by the Android compilation flow.
- **Published Identifiers**: The following development endpoints and identifiers are hardcoded in the codebase:
  - Appwrite Database ID `67fb572700006c9fc191` and regional endpoint in [ApiConstants.kt](file:///media/Adata_Data/Loan_App_Project/git_repo/apps/mobile-android/app/src/main/java/com/loansai/unassisted/util/constants/ApiConstants.kt) and [PANRepositoryImpl.kt](file:///media/Adata_Data/Loan_App_Project/git_repo/apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/repository/PANRepositoryImpl.kt).
  - Cloud Run URLs for BRE worker and Camunda endpoint in [NetworkModule.kt](file:///media/Adata_Data/Loan_App_Project/git_repo/apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/local/database/di/NetworkModule.kt) and [ApiConstants.kt](file:///media/Adata_Data/Loan_App_Project/git_repo/apps/mobile-android/app/src/main/java/com/loansai/unassisted/util/constants/ApiConstants.kt).
