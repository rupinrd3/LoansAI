# Publish Review

## Secret-Removal Status

The curated repo excludes the obvious high-risk files from the selected source set:

- Android signing files
- `local.properties`
- `google-services.json`
- Appwrite `.env`
- Firebase service account JSON files
- `node_modules`
- release bundles and IDE state

## Copy Audit Result

The selected source folders were rechecked against the copied repo using explicit exclusion rules.

Audit result:

- `apps/mobile-android`: no missing files relative to the chosen exclusion policy
- `services/bre-python`: no missing files relative to the chosen exclusion policy
- `services/appwrite-admin-tools`: no missing files relative to the chosen exclusion policy
- `services/firebase-support/functions-ver4`: no missing files relative to the chosen exclusion policy
- Firebase support utility tools: no missing files relative to the chosen exclusion policy

This means the repo copy is complete for the scope that was intentionally selected.

## Remaining Publish Risks

These are not necessarily secrets, but they are publish-review items:

- hardcoded Cloud Run endpoints in the Android app
- hardcoded Appwrite endpoint and project/database identifiers
- dormant Camunda-related code still present inside the copied app source
- reference docs that mention console URLs, project IDs, database IDs, or environment details

## Recommended Next Cleanup Before GitHub

1. Replace hardcoded infrastructure URLs with config-driven values.
2. Decide whether Appwrite project/database IDs should remain public in docs.
3. Remove or isolate dormant Camunda code from the Android app if you want the repo to reflect only the active architecture.
4. Review `docs/reference/` and remove anything too internal, outdated, or noisy.
