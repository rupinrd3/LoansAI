# Implementation Plan (v1.5.0)

**Version:** 1.5.0
**Date:** April 27, 2025
**Status:** Final Draft

## Overview

This plan outlines the tasks and phases required to implement version 1.5.0 of the Personal Loan Android Application. This version introduces the Bureau Confirmation screen, integrates backend LLM services for document processing and obligation recalculation, and refines the overall workflow based on the v1.5.0 Requirements Specification.

## Implementation Phases

**Phase 1: Foundation & Data (Weeks 1-2)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **1.1** | **Finalize v1.5 Data Catalog & Enums:** Approve Data Catalog v1.5, including final schemas for Firestore (incl. new `obligationRefinement` subcollection), Appwrite, enums (`ApplicationStep` updated). | Approved Data Catalog v1.5 document.                           | Requirements v1.5 Finalized      |
| **1.2** | **Implement Firestore Schema Changes:** Update `applications`, `documents` schemas. Create `obligationRefinement` subcollection structure. Define required indexes (`userId`, `applicationStatus`, `lastUpdatedAt`). | Firestore collections/subcollections configured per Data Catalog v1.5. | Task 1.1                         |
| **1.3** | **Verify Appwrite Collections:** Ensure `borrower_summary`, `enquiries`, `tradelines` collections and necessary indexes (`panNumber`) exist in Appwrite as per Data Catalog v1.5.                       | Confirmation of Appwrite setup readiness.                       | Data Catalog v1.5                |
| **1.4** | **Update Architecture Diagrams:** Modify system interaction diagrams (e.g., Requirements Sec 4.1) to reflect v1.5 flow (Bureau Conf. screen, Backend LLM APIs).                                       | Updated v1.5 architecture diagrams.                           | Requirements v1.5                |
| **1.5** | **Set up Backend LLM API Project Structure:** Initialize project structure (e.g., new Cloud Functions directory or separate microservice) for `processDocument` and `recalculateObligation` endpoints.   | Basic project structure for Backend LLM API endpoints.        | Requirements v1.5                |

**Phase 2: Backend LLM APIs (Weeks 3-4)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **2.1** | **Implement `processDocument` Backend API:** Develop endpoint logic (e.g., Cloud Function) to receive text/PDF, interact with Gemini LLM for structured data extraction, handle errors/timeouts, and update Firestore `documents` collection. | Deployed `processDocument` v1 endpoint.                           | Task 1.1, 1.5, LLM API Creds     |
| **2.2** | **Implement `recalculateObligation` Backend API:** Develop endpoint logic (e.g., Cloud Function) to fetch data from Firestore `obligationRefinement`, interact with Gemini LLM for recalculation, handle errors/timeouts, and update `obligationRefinement`. | Deployed `recalculateObligation` v1 endpoint.                  | Task 1.1, 1.5, 2.1, LLM API Creds |
| **2.3** | **Define App<->Backend API Contracts:** Create Retrofit interfaces (e.g., `LlmProcessingApi.kt`) and DTOs in the Android app for the new backend endpoints.                                           | API interfaces and DTOs defined in Android codebase.            | Task 2.1, 2.2                    |

**Phase 3: Bureau Confirmation & Workflow (Weeks 5-7)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **3.1** | **Develop Bureau Confirmation UI:** Create `BureauConfirmationScreen.kt` Composable with `LazyColumn`, EMI inputs (`EnhancedLoanTextField`), comments `TextField`, validation display.                 | `BureauConfirmationScreen.kt` Composable implemented.          | Requirements v1.5, Data Catalog v1.5 |
| **3.2** | **Develop Bureau Confirmation ViewModel:** Create `BureauConfirmationViewModel.kt` to fetch tradelines, manage state, handle input/validation, save data to `obligationRefinement` via repository, trigger `recalculateObligation` backend API. | `BureauConfirmationViewModel.kt` implemented.                 | Task 3.1, Task 2.3              |
| **3.3** | **Update Camunda BPMN & BRE Trigger Logic:** Modify workflow. BRE call trigger now occurs after Bureau Confirmation/Obligation Recalculation. Potentially add flags/checks for recalculation results.        | Updated and deployed Camunda BPMN v1.5 model.                   | Requirements v1.5                |
| **3.4** | **Update Python BRE Worker Input:** Modify BRE worker to accept `breInputData` potentially containing `llmRecalculatedObligation`. Update rule logic to prioritize this value if present.             | Updated Python BRE worker logic.                                | Task 3.3, Data Catalog v1.5      |
| **3.5** | **Integrate Bureau Confirmation Screen Navigation:** Update `AppNavGraph.kt` and ViewModels to navigate to `BureauConfirmationScreen` conditionally (based on bureau score 1-1000) after `DocumentUploadScreen`. | Conditional navigation flow implemented in App.                 | Task 3.1, 3.2                    |

**Phase 4: Document Processing Integration (Weeks 7-8)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **4.1** | **Integrate App -> `processDocument` API Calls:** Implement logic in `DocumentViewModel.kt` and `DocumentRepositoryImpl.kt` to call the `processDocument` backend API asynchronously after document upload/OCR. | App triggers backend document processing.                       | Task 2.1, 2.3                    |
| **4.2** | **Implement Document Status UI:** Develop the summary section in `DocumentUploadScreen.kt` displaying processing status indicators (Spinner/Tick/Cross) based on ViewModel state reflecting backend API results. | Document processing status UI implemented.                      | Task 4.1                         |
| **4.3** | **Update Key Fact Sheet Display Logic:** Modify `KeyFactSheetViewModel.kt` to fetch optional extracted document data fields from Firestore and update `KeyFactSheetScreen.kt` to display them conditionally. | Key Fact Sheet displays available LLM-extracted data.           | Task 2.1, Data Catalog v1.5      |

**Phase 5: Metadata & Orchestration (Parallel - Weeks 4-6)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **5.1** | **Implement Metadata Collection Points in App:** Integrate `MetadataRepository.sendMetadataEvent` calls for key events (app lifecycle, navigation, section timing, doc actions, verification, errors, obligation refinement submission). | App collects and sends v1.5 metadata events.                    | Task 1.2                         |
| **5.2** | **Develop & Deploy Cloud Functions Orchestrator:** Implement `processEvent` (HTTPS) and `dropOffChecker` (Scheduled) functions in TypeScript based on `cloud-functions-guide.txt`. Deploy v1.        | Cloud Functions Orchestrator v1 deployed.                       | Firebase Setup, Guide            |
| **5.3** | **Implement App -> Orchestrator API Communication:** Define `MetadataApi.kt` (Retrofit). Implement POST call in `MetadataRepositoryImpl.sendMetadataEvent` (write to Firestore first, then call function). | App reliably sends events to Orchestrator endpoint.             | Task 5.1, 5.2                    |
| **5.4** | **Implement Orchestrator Drop-off Logic:** Implement `dropOffChecker` logic querying `backgrounded_apps`, checking `applications` status/metadata, triggering analysis.                                | Orchestrator correctly identifies drop-offs.                    | Task 5.2                         |
| **5.5** | **Integrate Orchestrator -> LLM Call (Drop-off):** Implement `analyzeDropOff` logic in Cloud Function to call configured LLM (Gemini/OpenAI) with data. Handle response.                              | Orchestrator triggers LLM for drop-off analysis.                | Task 5.2, LLM API Creds          |
| **5.6** | **Integrate Orchestrator -> Email Alert (Brevo):** Implement `sendDropOffEmail` logic in Cloud Function to call Brevo API. Format email.                                                              | Orchestrator sends drop-off alerts via Brevo.                   | Task 5.5, Brevo API Creds       |

**Phase 6: App UI Polish & Bug Fixes (Weeks 9-10)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **6.1** | **Implement UI Polish:** Address minor UI/UX inconsistencies, refine animations, ensure Material 3 adherence across all screens, including the new Bureau Confirmation screen.                         | Polished and consistent application UI.                         | Previous Phases                  |
| **6.2** | **Address Bugs:** Fix critical bugs identified during development and integration, focusing on issues related to the new workflow, backend API calls, and asynchronous updates.                       | Stable application build with major bugs resolved.              | Previous Phases                  |

**Phase 7: Testing (Weeks 10-12)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **7.1** | **Unit Testing:** Add/Execute unit tests for: `BureauConfirmationViewModel`, Backend LLM API Cloud Functions, updated BRE logic, updated Android repository/use case logic.                           | Comprehensive unit test suite passing.                           | Phase 1-6 Code Complete          |
| **7.2** | **Integration Testing:** Add/Execute tests for: App<->Firebase, App<->Camunda, App<->Backend LLM API, App<->Cloud Functions Orch, Backend LLM API<->Firestore, Backend LLM API<->Gemini. Test conditional Bureau Confirmation navigation. Test async document status updates. Test BRE with/without recalc obligation. | Verified component integrations for v1.5 features.             | Phase 1-6 Code Complete          |
| **7.3** | **End-to-End (E2E) Testing:** Add/Execute E2E test scripts covering Bureau Confirmation flow (skip/complete), Document processing paths (Image/PDF, Success/Failure), Key Fact Sheet optional data display. Validate Firestore data integrity. | Verified E2E user journeys for v1.5.                           | Phase 1-6 Code Complete          |
| **7.4** | **UI Testing:** Add/Execute tests for `BureauConfirmationScreen`, document status summary UI. Verify overall UI/UX consistency and accessibility.                                                      | Validated UI/UX for v1.5.                                     | Phase 1-6 Code Complete          |
| **7.5** | **Security Audit:** Conduct security testing (SAST, DAST, SCA, API testing incl. new Backend LLM APIs) and secrets audit.                                                                         | Security vulnerabilities addressed/mitigated.                  | Phase 1-6 Code Complete          |

**Phase 8: Deployment & Monitoring (Week 13)**

| Task ID | Description                                                                                                                                                                                          | Deliverable/Output                                             | Dependencies                     |
| :------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- | :------------------------------- |
| **8.1** | **Prepare Release Builds:** Generate signed Android App build. Package final backend components (Camunda BPMN, BRE Worker, Backend LLM API Functions, Cloud Functions Orchestrator).                    | Production-ready artifacts v1.5.                                | Phase 7 Complete                 |
| **8.2** | **Deploy Backend Components:** Deploy v1.5 Camunda BPMN, BRE Worker, Backend LLM API Functions, Cloud Functions Orchestrator to Production. Apply Prod configs.                                     | v1.5 Backend services deployed to Production.                  | Task 8.1                         |
| **8.3** | **Configure Production Monitoring:** Set up monitoring dashboards and alerts for all components, including Backend LLM API Functions (invocations, latency, errors) and Gemini API usage/costs.         | Production monitoring and alerting configured.                 | Task 8.2                         |
| **8.4** | **Go-live & Post-launch Monitoring:** Gradual Play Store rollout for App v1.5. Monitor system health, performance, business metrics, error logs. Execute rollback plan if necessary.                  | Application v1.5 live; ongoing monitoring.                      | Task 8.3                         |