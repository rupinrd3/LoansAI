# Source Of Truth

## Selected Working Baseline

This repo is based on the following interpretation of the original workspace:

- the final working mobile baseline is `loan_app (ver_0.33_Releaseversion-10003_Closed_testing1)`
- the BRE version that best matches that app is `Camunda_and_BRE/bre-deployment-ver4/python-bre`
- Appwrite was used for bureau-data collections and tradeline retrieval
- Firebase/Firestore was used for application, document, BRE output, and loan-offer data

## Folder Mapping

- `loan_app (ver_0.33_Releaseversion-10003_Closed_testing1)` -> `apps/mobile-android`
- `Camunda_and_BRE/bre-deployment-ver4/python-bre` -> `services/bre-python`
- `appwrite_database_updation` -> `services/appwrite-admin-tools`
- `firebase-cli-codes/functions-ver4` -> `services/firebase-support/functions-ver4`
- `firebase-cli-codes/delete_firestore_data_records` -> `services/firebase-support/tools/delete-firestore-data-records`
- `firebase-cli-codes/download-one-record-ver2` -> `services/firebase-support/tools/download-one-record-ver2`
- `docs` and `documentation` -> `docs/reference/*`

## Why BRE Ver4 Was Chosen

The app version `0.33` prepares a normalized `documentExtractions` payload and sends direct HTTP requests to a BRE endpoint.

`bre-deployment-ver4` matches that contract best because:

- it exposes `GET /health`
- it exposes `POST /calculate-offer`
- it consumes `documentExtractions` directly
- it returns the BRE decision and offer fields expected by the app

Later BRE variants appear to shift more logic into the server by re-querying Firestore, which is a weaker match for the app-side contract in `0.33`.

## Important Distinction

This repo uses a working-baseline interpretation, not a full historical archive.

That means:

- some copied folders still contain dormant or historical code paths
- not every historical folder from the original workspace was imported
- “not imported” does not mean “unimportant historically”; it means “not part of the chosen baseline”
