# Included And Excluded

## Included

### Mobile App

Included from `loan_app (ver_0.33_Releaseversion-10003_Closed_testing1)`:

- Gradle wrapper and project files
- full `app/src/main` source tree
- resource files and launcher assets
- app build configuration
- Proguard rules

Excluded from the same source:

- `.gradle/`
- `.idea/`
- `.kotlin/`
- `build/`
- `app/build/`
- `app/release/`
- `local.properties`
- `keystore.properties`
- `app/google-services.json`
- log and error dump files

### BRE

Included from `Camunda_and_BRE/bre-deployment-ver4/python-bre`:

- `worker.py`
- `Dockerfile`
- `requirements.txt`
- deployment/service YAML files

Excluded:

- `.ipynb_checkpoints/`
- any service account file

### Appwrite Tools

Included from `appwrite_database_updation`:

- setup readme
- setup scripts
- package files
- example bureau report JSON

Excluded:

- `.env`
- `node_modules/`

### Firebase Support

Included:

- `functions-ver4` source/build files except heavy local junk
- selected utility scripts for deleting and downloading Firestore records

Excluded:

- service account files
- `node_modules/`
- notebooks
- zip archives
- logs

## Not Imported As Active Repo Content

These workspace areas were intentionally not imported into the active baseline:

- `Configure_MCP_server/*`
- Camunda deployment folders
- duplicate and backup `loan_app` copies
- later MCP-focused `loan_app` snapshots
- release archives and scratch artifacts

## Important Clarification

The copied Android app source still contains some Camunda-related classes and references because they exist inside the chosen app snapshot.

That is different from importing Camunda deployment folders as active repo content.
