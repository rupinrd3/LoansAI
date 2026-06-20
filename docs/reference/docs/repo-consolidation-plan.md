# Repo Consolidation Plan

## Goal

Create one clean git repository for the loan application project without carrying over accidental secrets, build artifacts, duplicate snapshots, or unclear ownership.

## Recommended Repo Shape

Use a monorepo with clear product boundaries:

```text
loan-app-platform/
  AGENTS.md
  README.md
  docs/
    architecture/
    decisions/
    setup/
    product/
    migration/
  apps/
    mobile-android/
  services/
    mcp-server/
    firebase-functions-legacy/
    bre-python/
    appwrite-admin-tools/
  infra/
    camunda/
  archive/
    mobile-snapshots/
    firebase-experiments/
    bre-experiments/
```

## What To Keep As Active Code

Use these as the initial active codebases:

- Android app: `loan_app (ver_0.33_Releaseversion-10003_Closed_testing1)`
- BRE service: `Camunda_and_BRE/bre-deployment-ver4/python-bre`
- Appwrite setup/admin utilities: `appwrite_database_updation`
- Firebase support code: selected content from `firebase-cli-codes`

Use these as reference or archive inputs, not active roots:

- `Configure_MCP_server/loan-app-mcp-server_v5`
- later `loan_app` snapshots unless you intentionally promote one
- duplicate `loan_app` copies
- `firebase_functions_code*` folders unless they contain unique source not present elsewhere
- all Camunda deployment folders and vendored upstream Camunda Helm content unless you explicitly revive workflow orchestration

## What To Exclude Before First Commit

Do not bring these into the new repo history:

- `node_modules/`
- `.gradle/`
- `.idea/`
- `.kotlin/`
- `build/`
- `app/build/`
- `functions/lib/` if it is generated from source you retain elsewhere
- release bundles such as `.aab`
- log files
- notebook checkpoints
- local machine files like `local.properties`
- signing files and credentials
- `google-services.json` unless you intentionally manage an example or encrypted secret workflow
- `service-account.json`
- `.env`

## High-Risk Cleanup Before Git Init

Complete this cleanup before creating the first public or long-lived commit:

1. Remove hardcoded API keys from any archived code before publication, especially MCP folders if they are copied into archive.
2. Remove `keystore.properties`, `local.properties`, service account files, and generated binaries from tracked content.
3. Review `google-services.json` handling. Usually keep it out of git and document setup separately.
4. Remove `.env` files and any Appwrite admin credentials from tracked content.
5. Add a root `.gitignore` for Android, Node, Python, Firebase, IDE, OS, and archive noise.
6. Decide whether `functions-ver4` is still needed as runnable code or only as migration reference.

## Migration Sequence

### Phase 1: Prepare

1. Create a new empty root folder for the monorepo.
2. Copy only the selected active code into the target structure.
3. Move old snapshots and experiments into `archive/` only if they provide historical value.
4. Strip secrets and generated content before `git init`.

### Phase 2: Normalize

1. Rename folders into stable names:
   - `loan_app (ver_0.33_Releaseversion-10003_Closed_testing1)` -> `apps/mobile-android`
   - selected Firebase support code -> `services/firebase-support`
   - `Camunda_and_BRE/bre-deployment-ver4-3May/python-bre` -> `services/bre-python`
   - `appwrite_database_updation` -> `services/appwrite-admin-tools`
2. Move MCP and Camunda folders to `archive/` if you want them preserved.
3. Remove vendored third-party repos unless they are truly required.
4. Add per-project README files describing build, deploy, dependencies, and ownership.

### Phase 3: Document

Create these docs early:

- `README.md`
  - what the platform is
  - repo layout
  - quick start
  - high-level architecture
- `docs/architecture/system-overview.md`
  - app, Firebase, MCP, BRE, Camunda interactions
- `docs/setup/mobile-android.md`
- `docs/setup/firebase-support.md`
- `docs/setup/bre-python.md`
- `docs/setup/appwrite-admin-tools.md`
- `docs/migration/source-of-truth.md`
  - which old folder became which new folder
- `docs/migration/archive-policy.md`
  - what stays archived and why

### Phase 4: Stabilize

1. Add build verification commands for each subproject.
2. Add a root task runner or script shortcuts later if useful.
3. Add CI only after local builds work reliably.
4. Promote legacy modules to active status only if they still serve production needs.

## Suggested Documentation Ownership

Keep documentation close to the code and also summarized centrally:

- root `README.md` for platform-level orientation
- local README in each app/service folder for setup and deployment
- `docs/architecture/` for diagrams and system flows
- `docs/decisions/` for architectural decision records
- `docs/migration/` for snapshot-to-monorepo provenance

## Suggested First Commit Strategy

Make the initial git history easy to understand:

1. Commit 1: skeleton monorepo structure, root docs, root `.gitignore`
2. Commit 2: Android app import
3. Commit 3: MCP server import after secret cleanup
4. Commit 4: BRE service and Camunda assets import
5. Commit 5: legacy Firebase functions import
6. Commit 6: archive historical snapshots or attach them separately outside the main repo

This creates readable history and reduces the chance of committing secrets by accident.

## Recommendation On Historical Snapshots

Do not put every `loan_app` snapshot directly into the main active tree. That will make the repo noisy and hard to maintain.

Better options:

- keep only the chosen baseline in active code and store the rest under `archive/mobile-snapshots/`
- or keep old snapshots outside the main repo entirely and reference them in migration docs

The second option is cleaner if those snapshots are only personal backups.

## Final Recommendation

Use `ver_0.33` as the initial mobile baseline because it matches the release snapshot you identified. Use direct BRE from `bre-deployment-ver4` as the active offer-calculation backend. Keep Appwrite setup/admin material because bureau data was used in the working flow. Treat MCP and Camunda as archive/reference material unless you intentionally revive them.
