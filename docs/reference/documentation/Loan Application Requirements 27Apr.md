# Personal Loan Android Application: Requirements and Technical Specification

**Version:** 1.5.0
**Date:** April 27, 2025
**Status:** Final Draft

## Table of Contents

1.  Introduction
    1.1 Purpose
    1.2 Scope
    1.3 Key Features Overview (Target State v1.5)
2.  Current State Analysis (Baseline v1.4)
    2.1 Existing Application Features & Flow
    2.2 Current Technical Architecture
    2.3 Current Data Structures
3.  Proposed Enhancements & Changes (Functional Requirements - v1.5 Detailed)
    3.1 Core Loan Process Flow (Revised v1.5)
    3.2 Metadata Collection & Orchestration (Firebase Cloud Functions)
    3.3 LLM Integration & Agentic AI Enhancements (Revised - Backend APIs)
    3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen - v1.5)
    3.5 Backend & Integration Changes (Revised v1.5)
4.  System Interactions & Data Flow (Revised v1.5)
    4.1 Overview Diagram (Conceptual - Revised v1.5)
    4.2 Detailed Interaction Points & Data Exchange (Revised v1.5)
5.  Detailed Design & Implementation Plan (Revised v1.5)
    5.1 UI/UX Design Principles
    5.2 Screen-Specific Implementation Details (Detailed - Revised v1.5)
    5.3 Metadata Implementation Strategy (Subcollection & Cloud Functions)
    5.4 Metadata Orchestrator Implementation Strategy (Firebase Cloud Functions)
    5.5 LLM Integration Implementation Strategy (Revised - Backend APIs)
    5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite, Backend LLM APIs)
    5.7 Implementation Phases (Detailed - Revised v1.5)
6.  Data Model Specification (Target State - v1.5 Detailed)
    6.1 Firestore Database Structure (Revised v1.5)
    6.2 Appwrite Bureau Database Structure (v1.5)
    6.3 Metadata Structure (Detailed - Subcollection)
    6.4 Obligation Refinement Data Structure (New Subcollection)
    6.5 Data Catalog Reference (v1.5)
7.  Technical Architecture (Target State - v1.5 Detailed)
8.  Orchestrator (Metadata/Drop-off) Design & Config Details (Firebase Cloud Functions)
9.  BRE (Python Worker) Design & Config Details (Revised Input)
10. Testing & Deployment Strategy (Detailed - Revised v1.5)

---

## 1. Introduction

### 1.1 Purpose

This document serves as the exhaustive and consolidated requirements and technical specification for the **v1.5.0** release of the Android Personal Loan application. It integrates the analysis of the existing application codebase (v1.4 base), supporting documentation, the introduction of the **Bureau Confirmation & Obligation Refinement** screen, the shift to **backend-driven LLM processing for documents and obligation recalculation**, and the established Firebase Cloud Functions Metadata Orchestrator. It acts as the single source of truth for development, testing, and deployment.

### 1.2 Scope

This specification covers the Android mobile application (Kotlin, Jetpack Compose), its detailed interactions with all backend services (Firebase Suite, Camunda Platform 8, Python BRE Worker, Appwrite Bureau Service, **Backend LLM Service API (interfacing with Gemini)**, Firebase Cloud Functions Metadata Orchestrator, Email Service - Brevo, PAN Verification Service), target data structures, UI/UX principles and specific screen enhancements, metadata tracking implementation, bug fixes, and the phased implementation plan.

### 1.3 Key Features Overview (Target State v1.5)

* **Streamlined Loan Application:** A guided, multi-step personal loan application flow optimized for mobile users in India.
* **Modern UI/UX:** Built natively with Jetpack Compose adhering to Material 3 guidelines, featuring a continuous vertical form layout enhanced by Section-by-Section Progressive Reveal. Includes minimalist card-based design, clear typography, subtle animations, and standard navigation patterns.
* **Agentic AI Assistant:** An integrated AI assistant accessible via a floating bubble (`AIAssistantBubble.kt`) providing contextual help and application review suggestions. Backend LLM analysis (via Cloud Functions) used **exclusively for drop-off analysis**.
* **Multi-Stage Loan Offer Computation:** Near real-time loan decisions (Approve/Reject/Refer) and offer parameter calculation using an integrated backend workflow involving Camunda, Python BRE, and potentially **LLM-driven obligation recalculation (triggered via backend API)**.
* **Bureau Data Confirmation & Obligation Refinement:** A dedicated screen allowing users to confirm open tradelines, input current EMIs, and provide comments, triggering a potential recalculation of obligations using a **backend LLM service**.
* **LLM-Powered Document Processing (via Backend):** In-app OCR using ML Kit for images. App triggers a **backend API** to send OCR text or PDF content to an LLM (Gemini) for structured data extraction. Asynchronous processing with UI status updates. Extracted data (optional) stored in Firestore.
* **Credit Bureau Integration (Appwrite):** Automated fetching and utilization of credit bureau data (CIBIL) via an Appwrite service (orchestrated by Camunda). Data stored across three collections (`borrower_summary`, `enquiries`, `tradelines`).
* **Detailed Metadata Collection:** Comprehensive tracking of user interactions, stored in a Firestore subcollection (`applications/{appId}/metadata`). Critical events trigger the **Firebase Cloud Functions Metadata Orchestrator**.
* **Firebase Backend:** Leverages Firebase for Phone Authentication, Firestore Database (application data, user profiles, document metadata, obligation refinement & application metadata subcollections), and Cloud Storage.
* **Robust Architecture:** MVVM pattern, Repositories, Use Cases, ViewModels, Hilt DI. Data persistence primarily via direct Firestore updates from repositories, with backend API calls for specific actions (LLM processing, Camunda triggers).
* **User Settings:** Allows users to configure OCR service preference, toggle AI assistant, and manage document upload settings.

---

## 2. Current State Analysis (Baseline v1.4)

### 2.1 Existing Application Features & Flow

* **Authentication:** Firebase Phone Authentication (OTP) (`LoginScreen.kt`, `LoginViewModel.kt`, `UserRepositoryImpl.kt`).
* **Core Flow Implementation (v1.4):** Multi-step process (PAN->Personal->Employment->Docs->Offer->Verification->Review->Complete). `ContinuousFormContainer.kt` used for layout. `AppNavGraph.kt` defines navigation. Single-pass BRE concept assumed post-Docs.
* **UI:** Jetpack Compose, Material 3 (`Theme.kt`, `Type.kt`, `Shape.kt`). Progressive reveal (`AnimatedSections.kt`). AI Bubble UI (`AIAssistantBubble.kt`).
* **Data Handling:** MVVM, Repositories (`RepositoryModule.kt`), Use Cases (`CreateApplicationUseCase.kt`, etc.), Hilt DI (`AppModule.kt`, `FirebaseModule.kt`, etc.). Firestore interaction (`LoanRepositoryImpl.kt`, `UserRepositoryImpl.kt`).
* **Integrations:** Retrofit (`NetworkModule.kt`). Interfaces for Camunda, Appwrite, PAN exist. ML Kit OCR (`MLKitOCRService.kt`). FileProvider (`file_provider.xml`). Document Upload Worker (`DocumentUploadWorker.kt`). Cloud Functions for Metadata/Drop-off trigger (`MetadataRepositoryImpl.kt`).

### 2.2 Current Technical Architecture

* **Platform:** Android (Kotlin).
* **UI:** Jetpack Compose, Material 3.
* **Architecture:** MVVM, Hilt, Clean Architecture principles, Jetpack Navigation Compose.
* **Networking:** Retrofit, OkHttp (`NetworkModule.kt`).
* **Backend:** Firebase (Auth, Firestore, Storage), Camunda, Python BRE, Appwrite (via `AppwriteServiceImpl.kt`), LLM Services (Gemini - via Backend API & Cloud Functions), **Firebase Cloud Functions (Metadata Orchestrator & Backend LLM API)**, External PAN Verification API (`PANApi.kt`), Email Service (Brevo - via Cloud Func Orch).
* **Persistence:** Primarily direct Firestore updates via Repositories, with DataStore Preferences (`PreferencesDataSource.kt`) for settings and local caching.

### 2.3 Current Data Structures

* **Firestore:** Collections `users`, `applications`, `documents`, `employment_verifications`, `otps`, `employers`. Subcollection `applications/{appId}/metadata`.
* **Appwrite:** Collections `borrower_summary`, `enquiries`, `tradelines`.
* **Data Catalog:** v1.4 defined core elements. v1.5 will refine this (see Section 6).

---

## 3. Proposed Enhancements & Changes (Functional Requirements - v1.5 Detailed)

### 3.1 Core Loan Process Flow (Revised v1.5)

The end-to-end user and system flow SHALL be updated as follows:

1.  **Initiation:** User Logs in (Firebase Auth OTP), Accepts Privacy Policy (Consent stored in `users` collection).
2.  **Start:** User navigates Home, starts new application or resumes existing. `applications` record created/updated in Firestore (`CreateApplicationUseCase.kt`).
3.  **PAN Entry:** User enters/scans PAN. App verifies format (`InputValidator.kt`). App triggers PAN verification API (`VerifyPANUseCase.kt`). **App triggers Bureau Data Fetch via the Camunda service (`CamundaService.kt`).** Camunda orchestrates the call to the Appwrite Service to fetch bureau data. Bureau data stored in Appwrite collections. Screen auto-advances on successful PAN verification. *Dev Skip Option available.*
4.  **User Data Entry:** User progresses through Personal Info, Employment Details screens. Data saved progressively to the `applications` document in Firestore via ViewModels (`PersonalInfoViewModel.kt`, `EmploymentDetailsViewModel.kt`) calling repository methods (`LoanRepositoryImpl.updatePersonalInfo`, `updateEmploymentDetails`).
5.  **Document Upload (`DocumentUploadScreen.kt`):**
    * User uploads required documents (Bank Statement, Salary Slip, ITR, Form 26AS) via Camera, Gallery (Image), or File Picker (PDF).
    * For Images: App uses `MLKitOCRService` to get raw text.
    * App triggers **asynchronous backend API calls** (e.g., Cloud Function `processDocument` endpoint) for each uploaded document.
        * For Images: Backend API receives OCR text, metadata, and instructions, calls Gemini LLM for structured data extraction.
        * For PDFs: Backend API receives PDF file, instructions, calls Gemini LLM for structured data extraction.
    * Backend service updates the corresponding Firestore `documents` record with extracted data (optional fields) upon LLM completion.
    * **UI Update:** A new summary section below the upload cards displays the status of each document's LLM processing (Spinner for Processing, Green Tick for Success, Red Cross for Failure/Timeout).
    * User can proceed to the next step even if processing is ongoing or fails.
6.  **Bureau Confirmation (`BureauConfirmationScreen.kt` - New Screen):**
    * **Navigation Trigger:** Shown *only* if bureau data was successfully fetched from Appwrite AND the bureau score is between 1 and 1000 (inclusive). Otherwise, skipped.
    * **Data Display:** Fetches and displays OPEN/LIVE tradelines from cached/stored bureau data. Shows Lender Name, Product Type, Sanction Amount, Current Balance. Uses `LazyColumn`.
    * **User Input:** Numeric input field for current EMI per tradeline (0 valid, null invalid, validation highlighting). Multi-line TextField for comments (with guiding question).
    * **Submission:** "Submit" button triggers:
        * Collection of user EMIs/comments.
        * Storage in Firestore `applications/{appId}/obligationRefinement/{recordId}`.
        * Triggering a **backend API call** (`recalculateObligation` endpoint).
7.  **Obligation Recalculation (Backend LLM Call):**
    * Backend service receives trigger.
    * Backend provides LLM (Gemini 2.0 Flash - configurable) with tradeline data and user input.
    * Backend receives JSON: `{ recalculatedObligation: number, excludedLoans: [...] }`.
    * Backend updates the `obligationRefinement` record.
8.  **BRE Execution (Modified Input):** App compiles required data (personal, employment, bureau summary, document insights, **and potentially the `recalculatedObligation` from Firestore**) and triggers the Camunda workflow. Input data snapshotted in `applications.breInputData`.
9.  **Camunda Workflow:** Orchestrates the single call to the Python BRE worker.
10. **Python BRE:** Worker executes rules. **Input potentially includes `recalculatedObligation` overriding declared EMI.** Returns decision (Approve/Reject/Refer), offer parameters, `profileFlag`.
11. **Loan Offer Display:** App receives BRE result. `LoanOfferScreen.kt` displays outcome dynamically based on `decisionStatus` and `profileFlag`. Offer details stored in `applications.loanOffer`.
12. **Employment Verification:** User completes verification (Email OTP or ID Card Scan). Status updated in `employment_verifications` collection.
13. **Key Fact Sheet (`KeyFactSheetScreen.kt`):**
    * User reviews final summary.
    * **UI Update:** Displays additional optional fields if available in Firestore (from LLM document processing and bureau data): `bankName`, `accountNumber`, `averageBalance`, `employerNameOnSlip`, `employeeNameOnSlip`, `grossSalary`, `netSalary`, `assessmentYear`, `panOnItr`, `taxableIncome`, `panOn26AS`, `assessmentYear26AS`, `totalTaxPaid`, `bureauType`, `creditScore`, `totalAccounts`, `openAccounts`. Fields hidden if data absent.
14. **Submit Application:** User submits. `applicationStatus` updated. `submittedAt` recorded.
15. **Application Complete:** `ApplicationCompleteScreen.kt` displays final status.

### 3.2 Metadata Collection & Orchestration (Firebase Cloud Functions)

* App MUST automatically collect metadata: `deviceInfo`, `appSession`, `screenVisits`, `sectionTimings`, `documentEvents`, `verificationEvents`, `errorEvents` (See Data Catalog v1.5 Section 4.11).
* Metadata MUST be stored in Firestore subcollection `applications/{applicationId}/metadata/{eventId}`. Each document includes `applicationId`, `eventId`, `eventTimestamp`, `eventType`.
* App MUST send critical events via HTTP POST to **Firebase Cloud Functions Metadata Orchestrator** endpoint (`processEvent` function defined in `cloud-functions-guide.txt`).
* **Firebase Cloud Functions Orchestrator** responsibilities:
    * Receive events via HTTP trigger (`processEvent`).
    * Store events in Firestore `metadata` subcollection.
    * Track app background/foreground using `backgrounded_apps` collection.
    * Check for drop-offs via scheduled function (`dropOffChecker`).
    * Trigger LLM analysis (Gemini/OpenAI) for confirmed drop-offs.
    * Send email alerts (Brevo).
    * Optionally update `applicationStatus` to `DROPPED_OFF`.

### 3.3 LLM Integration & Agentic AI Enhancements (Revised - Backend APIs)

* **Document Processing (Backend API):**
    * App triggers backend API (`processDocument`) after document upload/OCR.
    * Backend API interacts with Gemini (configurable model) for structured data extraction.
    * Backend API updates Firestore `documents` record.
* **Obligation Recalculation (Backend API):**
    * App triggers backend API (`recalculateObligation`) after Bureau Confirmation submission.
    * Backend API interacts with Gemini (configurable model) using tradeline data/user input.
    * Backend API updates Firestore `obligationRefinement` subcollection.
* **Drop-off Analysis (Cloud Functions):** Remains unchanged from v1.4. Triggered by Orchestrator, uses configured LLM via Cloud Function environment variables.
* **App-Side LLM Calls Removed:** No direct LLM SDK integration or calls from the Android app.
* **Email Service Configuration (Brevo):** Uses Brevo via Cloud Functions Orchestrator.

### 3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen - v1.5)

* **General:**
    * Use `ModernBackButton` consistently.
    * Ensure `AIAssistantBubble.kt` positioning is correct (padding, zIndex).
    * Optional: Enable `SwipeBackHandler.kt`.
* **OTP Screen (`LoginScreen.kt`):**
    * Implement Privacy Policy link/modal.
    * Ensure `users` collection updated correctly on successful verification.
* **Home Screen (`HomeScreen.kt`):**
    * Fix "Resume Application" logic in `HomeViewModel.kt`.
    * Implement Chat/Call/Email button actions.
    * Adjust layout spacing.
* **PAN Screen (`PANEntryScreen.kt`):**
    * Implement auto-progression after successful verification.
    * Implement dev-only skip button.
    * Implement OCR result preview (`OCRResultPreview`) and edit dialog (`EditExtractedFieldsDialog`).
* **Personal Info Screen (`PersonalInfoScreen.kt`):**
    * Ensure ViewModel updates `currentStep` on progression.
    * Use `DatePickerDialog`. Use `StateSearchField`.
* **Employment Info Screen (`EmploymentDetailsScreen.kt`):**
    * Implement conditional UI based on `employmentType`. Remove/disable fields as required. Fix state preservation.
* **Document Upload Screen (`DocumentUploadScreen.kt`):**
    * Add a **new summary section** below the `EnhancedDocumentUploadCard` list.
    * This section MUST list each document type (Bank Statement, Salary Slip, ITR, Form 26AS).
    * Beside each listed document type, display a status indicator managed by `DocumentViewModel.kt`:
        * Idle/Not Uploaded: Placeholder or no indicator.
        * Processing (Backend LLM call in progress): Animated `CircularProgressIndicator`.
        * Success (LLM data received): Green `Icons.Default.CheckCircle`.
        * Failure (LLM error/timeout): Red `Icons.Default.Error`.
    * ViewModel manages state based on asynchronous backend API responses.
* **Bureau Confirmation Screen (`BureauConfirmationScreen.kt` - New):**
    * Implement UI: Title, subtitle, `LazyColumn` for tradelines (Lender, Product, Sanction, Balance), numeric `EnhancedLoanTextField` for EMI input per tradeline, validation highlighting (red border for null/invalid EMI), guiding question text, multi-line `TextField` for comments, Submit button.
* **Loan Offer Screen (`LoanOfferScreen.kt`):**
    * Logic remains based on `breOutputData`, but this data now arrives later in the flow. Implement dev-only bypass button (`bypassApproval`).
* **Settings Screen (`SettingsScreen.kt`):**
    * Implement toggles/selectors for OCR Service, AI Assistant Enabled, Document Upload Enabled, Document Image Resolution.
* **Employment Verification Screen (`EmploymentVerificationScreen.kt`):**
    * Implement conditional UI. Use `VerificationMethodSelector`. Implement skip confirmation dialog.
* **Key Fact Sheet Screen (`KeyFactSheetScreen.kt`):**
    * **Enhancement:** Fetch and display additional optional fields if present in Firestore: `bankName`, `accountNumber`, `averageBalance`, `employerNameOnSlip`, `employeeNameOnSlip`, `grossSalary`, `netSalary`, `assessmentYear`, `panOnItr`, `taxableIncome`, `panOn26AS`, `assessmentYear26AS`, `totalTaxPaid`, `bureauType`, `creditScore`, `totalAccounts`, `openAccounts`. Hide fields if data is absent.
* **Application Submitted Screen (`ApplicationCompleteScreen.kt`):**
    * Implement static confirmation graphic. Add `SuccessConfetti`. Display dynamic content based on final `applicationStatus`. Add "Back to Home" button.
* **Voice Input:** Integrate `VoiceInputButton.kt` optionally near relevant text fields.

### 3.5 Backend & Integration Changes (Revised v1.5)

* **Workflow Modification:** BRE call happens *after* Bureau Confirmation and potential Obligation Recalculation.
* **Backend API (New):** Define and implement backend endpoints (e.g., Cloud Functions):
    * `processDocument(applicationId, documentId, documentType, content)`: Receives OCR text/PDF, calls Gemini, updates Firestore `documents`.
    * `recalculateObligation(applicationId, obligationRefinementId)`: Fetches data from `obligationRefinement` subcollection, calls Gemini, updates subcollection.
* **App Integration:** Define `LlmProcessingApi.kt` (Retrofit). Update repositories (`DocumentRepositoryImpl.kt`, `LoanRepositoryImpl.kt`) to call these APIs. ViewModels trigger repository methods.
* **BRE Input:** `breInputData` map potentially includes `recalculatedObligation` from Firestore.
* **Firestore:** Define schema for `obligationRefinement` subcollection. Add optional extracted fields to `documents`.

---

## 4. System Interactions & Data Flow (Revised v1.5)

### 4.1 Overview Diagram (Conceptual - Revised v1.5)

    subgraph User Device
        App[Android App]
    end
    subgraph Firebase
        Auth(Firebase Auth)
        Firestore[(Firestore DB)]
        Storage(Firebase Storage)
        CloudFuncOrch[Cloud Functions Orchestrator]
        CloudFuncLLM[Cloud Functions LLM Backend API]
    end
    subgraph Core Backend
        Camunda(Camunda Engine)
        PythonBRE(Python BRE Worker)
    end
    subgraph Supporting Services
        Appwrite(Appwrite DB Service)
        LLM(LLM Service - Gemini)
        Email(Email Service - Brevo)
        PANVerify(PAN Verification API)
    end

    User(User) -- Interacts --> App
    App -- Auth Requests --> Auth
    App -- CRUD App Data, Doc Meta, Obligation Input --> Firestore
    App -- Write Metadata Events --> Firestore[metadata subcollection]
    App -- Store/Retrieve Docs --> Storage
    App -- Verify PAN --> PANVerify
    App -- Start Loan Workflow (Post-Bureau Confirm) / Trigger Bureau Fetch --> Camunda
    App -- Send Metadata Events --> CloudFuncOrch[processEvent Endpoint]
    App -- Trigger Document Processing --> CloudFuncLLM[processDocument Endpoint]
    App -- Trigger Obligation Recalculation --> CloudFuncLLM[recalculateObligation Endpoint]

    CloudFuncOrch -- Reads App Status/Metadata --> Firestore
    CloudFuncOrch -- Writes App Status (Dropped Off) --> Firestore
    CloudFuncOrch -- Request Drop-off Analysis --> LLM
    CloudFuncOrch -- Send Summary --> Email
    CloudFuncOrch -- Writes Event to Subcollection --> Firestore[metadata subcollection]

    CloudFuncLLM -- Reads App Data / Doc Meta / Obligation Data --> Firestore
    CloudFuncLLM -- Calls Gemini --> LLM
    CloudFuncLLM -- Writes Extracted Data --> Firestore[documents collection]
    CloudFuncLLM -- Writes Recalculated Obligation --> Firestore[obligationRefinement subcollection]

    Camunda -- Request Bureau Fetch (Delegate) --> Appwrite
    Camunda -- Assign Task (BRE Call) --> PythonBRE
    PythonBRE -- Complete Task --> Camunda
    PythonBRE -- Read Rules --> RulesConfig[Rule Files/DB]

    Appwrite -- Serves Bureau Data --> Camunda

    LLM -- Provides Analysis/Extraction --> CloudFuncLLM
    LLM -- Provides Drop-off Analysis --> CloudFuncOrch

    Firestore -- Serves Data/Metadata --> App
    Firestore -- Serves Data/Metadata --> CloudFuncOrch
    Firestore -- Serves Data --> CloudFuncLLM
    Auth -- Returns Auth State --> App
    Storage -- Serves Docs --> App
    PANVerify -- Returns Verification --> App
    Email -- Sends Alert --> User/Support
 

### 4.2 Detailed Interaction Points & Data Exchange (Revised v1.5)

    App <> Firebase Auth: Standard OTP flow.
    App -> Firestore: Writes/Updates users, applications (incl. personalInfo, employmentDetails), documents, employment_verifications, otps. Writes events to applications/{appId}/metadata. Writes user input to applications/{appId}/obligationRefinement. Reads app state, user profile, document status, recalculated obligation (if needed for display). Primary mechanism for saving user progress.
    App <> Firebase Storage: Uploads/downloads document files.
    App -> PAN Verification API: Sends PAN, receives validation status/name.
    App -> Camunda Service: Initiates Bureau Fetch (PAN screen). Initiates BRE calculation (Post Bureau Confirmation/Obligation Recalc trigger).
    App -> Cloud Functions Orchestrator: Sends JSON metadata event payloads via HTTP POST to processEvent endpoint.
    App -> Backend LLM API (Cloud Functions):
        Calls processDocument endpoint sending (AppID, DocID, DocType, OCR Text or PDF content).
        Calls recalculateObligation endpoint sending (AppID, ObligationRefinementRecordID).
    Cloud Functions Orchestrator <> Firestore: Reads applications status/timestamps, reads/writes metadata subcollection events. Writes applications.applicationStatus = DROPPED_OFF. Manages backgrounded_apps collection.
    Cloud Functions Orchestrator -> LLM Service (Drop-off): Sends application snapshot/metadata. Receives JSON summary. Uses configured provider (Gemini/OpenAI).
    Cloud Functions Orchestrator -> Email Service (Brevo): Sends formatted drop-off summary email.
    Backend LLM API (Cloud Functions) <> Firestore: Reads applications, documents, obligationRefinement. Writes extracted data to documents. Writes recalculated obligation to obligationRefinement.
    Backend LLM API (Cloud Functions) -> LLM Service (Gemini): Sends formatted prompts with text/PDF/data for document processing or obligation recalculation. Receives structured JSON/text.
    Camunda <> Appwrite: Camunda Service Task fetches bureau data from 3 collections based on PAN.
    Camunda <> Python BRE: Camunda assigns External Task. BRE worker executes rules, completes task. Input potentially includes recalculated obligation.
    Python BRE -> Rules Config: Reads rule definitions.

### 5. Detailed Design & Implementation Plan (Revised v1.5)
5.1 UI/UX Design Principles

    Material 3 Adherence: Strictly follow M3 guidelines.
    Jetpack Compose: Implement all UI declaratively.
    Progressive Reveal: Utilize ContinuousFormContainer.kt and AnimatedSections.kt.
    Clean & Minimalist: Emphasize clarity, whitespace, card-based layouts.
    Responsiveness: Ensure layouts adapt using Compose modifiers.
    Accessibility: Implement content descriptions, sufficient touch targets, font scaling, good contrast.
    Performance: Optimize Compose layouts, minimize recompositions, use lazy lists.
    Navigation: Use Jetpack Navigation Compose (AppNavGraph.kt). Provide consistent back navigation.

5.2 Screen-Specific Implementation Details (Detailed - Revised v1.5)

    General: Utilize reusable Composables (ModernBackButton, EnhancedLoanTextField, LoanOfferSlider, AIAssistantBubble, ErrorState, LoadingState). Implement optional SwipeBackHandler.
    OTP Screen (LoginScreen.kt): Implement OTP input (ModernOtpInput), Privacy Policy link/modal. Ensure ViewModel updates Firestore users.
    Home Screen (HomeScreen.kt): Fix Resume flow in HomeViewModel.kt. Implement button actions (Intents: Dialer, Email; AI Assistant toggle). Adjust layout spacing.
    PAN Screen (PANEntryScreen.kt): Use EnhancedLoanTextField. Handle OCR (MLKitOCRService, OCRScanState). Show OCRResultPreview and EditExtractedFieldsDialog. Implement auto-progress. Implement dev skip button.
    Personal Info Screen (PersonalInfoScreen.kt): Use EnhancedLoanTextField. Use DatePickerDialog. Use StateSearchField. Ensure ViewModel updates currentStep.
    Employment Info Screen (EmploymentDetailsScreen.kt): Implement conditional UI logic based on employmentType. Use EmployerSearchField. Remove/disable fields as specified. Fix state preservation.
    Document Upload Screen (DocumentUploadScreen.kt):
        Use EnhancedDocumentUploadCard for each document type.
        Trigger backend API call via DocumentViewModel.kt after upload/OCR.
        Implement new summary section below cards displaying document processing status (Spinner, Tick, Cross) based on ViewModel state reflecting asynchronous backend results.
    Bureau Confirmation Screen (BureauConfirmationScreen.kt - New):
        Create Composable and BureauConfirmationViewModel.kt.
        ViewModel fetches tradelines, handles user EMI/comment input.
        Use LazyColumn, EnhancedLoanTextField (numeric, validation), TextField.
        Submit button saves input to Firestore obligationRefinement subcollection and triggers backend recalculateObligation API call.
    Loan Offer Screen (LoanOfferScreen.kt): Display based on BRE results (which now follow Bureau Confirmation/Obligation Recalculation). Implement dev bypass button.
    Settings Screen (SettingsScreen.kt): Implement toggles/selectors for OCR Service, AI Assistant, Document Upload Enabled, Document Image Resolution.
    Employment Verification Screen (EmploymentVerificationScreen.kt): Implement conditional UI. Use VerificationMethodSelector. Implement skip confirmation dialog.
    Key Fact Sheet Screen (KeyFactSheetScreen.kt):
        Fetch and display new optional data fields (from LLM document processing/bureau) if available in Firestore. Hide fields otherwise. Use ReviewSection and LabeledValue.
    Application Submitted Screen (ApplicationCompleteScreen.kt): Implement static confirmation graphic/text. Add SuccessConfetti. Display dynamic content based on final applicationStatus. Provide "Back to Home" action.
    Voice Input: Conditionally display VoiceInputButton. Handle RECORD_AUDIO permission.

5.3 Metadata Implementation Strategy (Subcollection & Cloud Functions)

    Data Structures: Use Kotlin data classes in MetadataModels.kt.
    Collection Logic (App): Utilize Android lifecycle observers, Navigation listeners, ViewModel logic to capture events. Instantiate metadata data classes.
    Storage & Orchestration Trigger (App): Inject MetadataRepository.kt. Call metadataRepository.sendMetadataEvent(appId, eventType, eventData). MetadataRepositoryImpl.sendMetadataEvent writes to Firestore subcollection (applications/{appId}/metadata/{eventId}) AND makes async HTTP POST to Cloud Functions processEvent endpoint via MetadataApi.kt.

5.4 Metadata Orchestrator Implementation Strategy (Firebase Cloud Functions)

    Implementation: Firebase Cloud Functions using TypeScript (as per cloud-functions-guide.txt).
    Core Functions:
        processEvent (HTTPS Trigger): Receives event, validates, stores in Firestore metadata subcollection, manages backgrounded_apps state.
        dropOffChecker (Scheduled Trigger): Periodically queries backgrounded_apps, checks applications status/metadata activity, triggers analyzeDropOff.
    Logic: Follows detailed steps in cloud-functions-guide.txt.
    Configuration: Securely manage API keys (LLM, Brevo), endpoints, timeouts via Firebase environment configuration.

5.5 LLM Integration Implementation Strategy (Revised - Backend APIs)

    Backend API (Cloud Functions or Separate Service):
        Define HTTP endpoints (processDocument, recalculateObligation).
        Implement logic to interact with Gemini API.
        Handle requests, prompt engineering, API calls, response parsing, error handling, timeouts.
        Implement Firestore updates (documents, obligationRefinement).
    App Integration:
        Define LlmProcessingApi.kt (Retrofit).
        Update repositories (DocumentRepositoryImpl.kt, LoanRepositoryImpl.kt) to call these APIs.
        ViewModels (DocumentViewModel.kt, BureauConfirmationViewModel.kt) trigger repository methods.
        App UI observes status updates.

5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite, Backend LLM APIs)

    Camunda Workflow: Updated to trigger BRE after potential Bureau Confirmation and Obligation Recalculation.
    Python BRE Worker: Input may now include recalculatedObligation. Rule logic prioritizes this if present.
    Appwrite Service: No change.
    Backend LLM APIs: Integrated via calls from App/Repositories.

5.7 Implementation Phases (Detailed - Revised v1.5)

    Phase 1: Foundation & Data (Weeks 1-2)
        Task 1.1: Finalize v1.5 Data Catalog & Enums.
        Task 1.2: Implement Firestore schema changes (obligationRefinement subcollection, optional fields in documents).
        Task 1.3: Verify Appwrite collections exist.
        Task 1.4: Update Architecture diagrams (v1.5).
        Task 1.5: Set up Backend LLM API project structure (e.g., Cloud Functions).
    Phase 2: Backend LLM APIs (Weeks 3-4)
        Task 2.1: Implement processDocument backend API.
        Task 2.2: Implement recalculateObligation backend API.
        Task 2.3: Define App<->Backend API contracts (Retrofit). Deploy v1 APIs.
    Phase 3: Bureau Confirmation & Workflow (Weeks 5-7)
        Task 3.1: Develop BureauConfirmationScreen.kt UI.
        Task 3.2: Develop BureauConfirmationViewModel.kt.
        Task 3.3: Update Camunda BPMN.
        Task 3.4: Update Python BRE worker input handling.
        Task 3.5: Update App Navigation (AppNavGraph.kt).
    Phase 4: Document Processing Integration (Weeks 7-8)
        Task 4.1: Integrate App calls to processDocument backend API.
        Task 4.2: Implement document status summary section UI.
        Task 4.3: Update KeyFactSheetViewModel.kt to fetch/display LLM-extracted data.
    Phase 5: Metadata & Orchestration (Parallel - Weeks 4-6)
        Task 5.1: Implement metadata collection & sending in App.
        Task 5.2: Develop & Deploy Cloud Functions Orchestrator (processEvent, dropOffChecker).
        Task 5.3: Implement App -> Orchestrator API communication.
        Task 5.4: Implement Orchestrator drop-off detection logic.
        Task 5.5: Integrate Orchestrator -> LLM call (Drop-off).
        Task 5.6: Integrate Orchestrator -> Email alert (Brevo).
    Phase 6: App UI Polish & Bug Fixes (Weeks 9-10)
        Task 6.1: Implement specific UI fixes/enhancements.
        Task 6.2: Address bugs from new workflow.
    Phase 7: Testing (Weeks 10-12)
        Task 7.1: Unit Testing (Android, BRE, Backend LLM APIs, Cloud Functions).
        Task 7.2: Integration Testing (App<->Firebase, App<->Camunda, App<->Backend LLM API, App<->Cloud Functions Orch, Backend<->LLM, etc.).
        Task 7.3: E2E Testing (Include new flows, statuses).
        Task 7.4: UI Testing (New screen, status UI).
        Task 7.5: Security Audit.
    Phase 8: Deployment & Monitoring (Week 13)
        Task 8.1: Prepare release builds.
        Task 8.2: Deploy backend components.
        Task 8.3: Configure production monitoring.
        Task 8.4: Go-live & monitoring.

### 6. Data Model Specification (Target State - v1.5 Detailed)

(Refers to the updated Loan Application Data Catalog v1.5 document for full definitions)
6.1 Firestore Database Structure (Revised v1.5)

    users: Stores user authentication and profile data (phone, email, privacy consent).
    applications: Core collection for loan applications. Contains nested maps for personalInfo, employmentDetails, breInputData, breOutputData, loanOffer. Includes status tracking (applicationStatus, currentStep, completedSteps). Contains documentIds array. Links to subcollections.
    documents: Stores metadata for each uploaded document. Includes new optional fields populated by the backend LLM service based on document type (e.g., bankName, accountNumber, netSalary, taxableIncome).
    employment_verifications: Tracks employment verification attempts and status.
    otps: Stores temporary OTPs for phone/email verification.
    employers: Cache/List of known employers (populated externally or via admin).
    applications/{applicationId}/metadata: Subcollection. Stores detailed user interaction events (screen visits, section timings, document actions, errors, etc.). Each event is a document.
    applications/{applicationId}/obligationRefinement: New Subcollection. Stores user input from Bureau Confirmation screen and LLM recalculation results.

6.2 Appwrite Bureau Database Structure (v1.5)

    borrower_summary: Stores aggregated bureau info.
    enquiries: Stores details of individual credit enquiries.
    tradelines: Stores details of individual loans/credit lines.
    (Schema details unchanged from v1.4, refer to Data Catalog v1.5 Section 4.6).

6.3 Metadata Structure (Detailed - Subcollection)

    Stored under applications/{applicationId}/metadata/{eventId}.
    Each event document contains eventId, applicationId, eventTimestamp, eventType, and event-specific data (e.g., screenVisit, sectionTiming, documentEvent).
    (Schema details unchanged from v1.4, refer to Data Catalog v1.5 Section 4.11).

6.4 Obligation Refinement Data Structure (New Subcollection)

    Location: applications/{applicationId}/obligationRefinement/{recordId}
    Purpose: Stores data related to the Bureau Confirmation screen and subsequent LLM recalculation.
    Fields:
        recordId (String): Unique ID for the refinement attempt (e.g., UUID).
        createdAt (Timestamp): When the user submitted the confirmation screen.
        userProvidedEmis (Map<String, Number>): Map where key is tradeline ID/identifier and value is the EMI entered by the user.
        userComments (String): Free-text comments provided by the user.
        llmRecalculatedObligation (Number): The total monthly obligation recalculated by the LLM (Nullable).
        llmExcludedLoans (List<Map<String, String>>): List of tradelines excluded by the LLM, each map containing tradelineId and reason (Nullable).
        llmProcessingStatus (String): Status of the backend LLM recalculation (e.g., PENDING, SUCCESS, FAILED).
        llmProcessedAt (Timestamp): When the LLM recalculation finished (Nullable).

6.5 Data Catalog Reference (v1.5)

    The Loan Application Data Catalog v1.5 document provides the definitive source of truth for all data fields, types, sources, descriptions, and enums used across Firestore, Appwrite, API requests/responses, and application models.

### 7. Technical Architecture (Target State - v1.5 Detailed)
7.1 Android Mobile Client

    Platform: Native Android, Kotlin.
    UI: Jetpack Compose, Material 3. Reusable Composables (EnhancedLoanTextField, LoanOfferSlider, AIAssistantBubble, etc.). New BureauConfirmationScreen. Updated DocumentUploadScreen with status summary.
    Architecture: MVVM, Hilt DI, Use Cases, Repositories.
    Data Handling: Compose State, ViewModels, DataStore Preferences, Retrofit APIs (incl. new Backend LLM API), Firestore SDK (primary persistence), Firebase Storage SDK, ML Kit OCR, WorkManager.
    Connectivity: HTTPS, AuthInterceptor.

7.2 Firebase Suite

    Authentication: Phone OTP Auth.
    Firestore Database: Primary data store (users, applications, documents, etc.). New obligationRefinement subcollection. Firestore Security Rules.
    Cloud Storage: Document file storage. Storage Security Rules.
    Cloud Functions (Metadata Orchestrator): Event-driven backend logic for metadata processing, drop-off detection, LLM calls (drop-off), email alerts.
    Cloud Functions (Backend LLM API - New): Handles requests from App for document processing and obligation recalculation, interacts with Gemini LLM, updates Firestore.

7.3 Core Loan Process Backend

    Camunda Platform 8: Process orchestration (revised flow). REST API interaction with App.
    Python BRE Worker: External Task worker executing business rules (accepts revised input).

7.4 Supporting Services

    Appwrite DB Service: Stores/serves bureau data (3 collections). Accessed via API (called by Camunda).
    LLM Service (Google Gemini): Called by Backend LLM API (documents, obligations) and Cloud Functions Orchestrator (drop-off). Configurable model.
    Email Service (Brevo): Called by Cloud Functions Orchestrator for alerts.
    PAN Verification API: External API called by the App.

7.5 Communication & Security

    HTTPS/TLS for all communication.
    Authentication: Firebase Auth (App), JWT/API Keys (App->Backend APIs), Secure keys/Service Accounts (Backend<->Backend).
    Authorization: Firestore/Storage Security Rules, API endpoint checks (e.g., checking auth context in Cloud Functions).
    Secrets Management: Use secure secrets management (e.g., Google Secret Manager, Firebase Env Vars) for API keys (LLM, Brevo, potentially Backend APIs), credentials. NO hardcoding.

### 8. Orchestrator (Metadata/Drop-off) Design & Config Details (Firebase Cloud Functions)

(No significant changes from v1.4 description. Refers to cloud-functions-guide.txt)
8.1 Purpose & Distinction

Monitors user journey via metadata, detects drop-offs, triggers LLM analysis (drop-off only) & alerts. Distinct from Camunda loan process engine. Implemented using Firebase Cloud Functions.
8.2 Implementation

Firebase Cloud Functions (TypeScript recommended, as per cloud-functions-guide.txt).
8.3 High-Level Process Flow (Cloud Functions)

    Event Reception (HTTP Trigger - processEvent): Receives metadata from App, stores in Firestore metadata subcollection, manages backgrounded_apps state.
    Drop-off Check (Scheduled Trigger - dropOffChecker): Queries backgrounded_apps, verifies app status/metadata activity, triggers analyzeDropOff if confirmed dropped.
    Drop-off Analysis (Internal Call - analyzeDropOff): Fetches data, calls configured LLM (Gemini/OpenAI), calls sendDropOffEmail.
    Email Alert (Internal Call - sendDropOffEmail): Calls Brevo API.

8.4 Configuration Parameters (Cloud Functions Environment)

    DROP_OFF_TIMEOUT_SECONDS (e.g., 30)
    llm.provider ("GOOGLE" or "OPENAI")
    llm.model_gemini (e.g., "gemini-1.5-flash-latest")
    llm.model_openai (e.g., "gpt-4o-mini")
    llm.api_key (Secret)
    brevo.key (Brevo API Key - Secret)
    brevo.from (Sender email)
    brevo.to (Alert recipient email)
    Firebase Project configuration (implicit).

### 9. BRE (Python Worker) Design & Config Details (Revised Input)
9.1 Purpose

Execute loan eligibility/offer rules. Operates via Camunda External Task pattern.
9.2 Technology Stack

Python 3.x, pyzeebe, optional rules engine, Docker.
9.3 Interaction Model

Polls Zeebe for calculateLoanOffer topic, executes handler, completes task with breOutputData.
9.4 Input/Output Handling

    Input: breInputData map (from applications document). Crucially, the logic preparing this input must now check the obligationRefinement subcollection. If a recent, successful llmRecalculatedObligation exists, it should be included in breInputData.
    Output: breOutputData map (incl. decisionStatus, offer params, profileFlag). Stored back into applications document.

9.5 Rule Categories & Logic (Illustrative - Incorporating Recalculated Obligation)

Handler for calculateLoanOffer topic:

    Input Prep:
        Get breInputData (including personalInfo, employmentDetails, bureauSummary, documentInsights, and potentially llmRecalculatedObligation).
        Determine Effective EMI:
            If llmRecalculatedObligation is present and valid (positive number): effectiveMonthlyEmi = llmRecalculatedObligation. Log this usage.
            Else if employmentDetails.declaredEmi2 is present: effectiveMonthlyEmi = employmentDetails.declaredEmi2. Log this usage.
            Else: effectiveMonthlyEmi = employmentDetails.monthlyEmi. Log this usage.
        Derive intermediate variables (Age, Income Stability, etc.).
    PAN Check: Set internal profileOK flag based on verification status.
    Hard Rules: Check Min/Max Age, Bureau Flags (e.g., suitFiled, wilfulDefault, DPD), Employment Type, Min Income, effectiveMonthlyEmi vs Income Ratio, Data Presence. If fail -> Decision = REJECT, set profileFlag based on reason (e.g., False if bureau flag related), set rejection reason, STOP.
    Risk/Calculation: Calculate Internal Risk Score, Max Affordable EMI (maxPayableEmi) based on Income and effectiveMonthlyEmi. If maxPayableEmi <= 0 -> Decision = REJECT, set profileFlag, set reason, STOP. Calculate Max Loan Amount based on maxPayableEmi and rules. Determine Rate/Tenor/Fee tiers based on Risk Score and other factors.
    Final Decision & Offer Population:
        If profileOK is false (from step 2) -> Decision = REJECT, Reason = "Profile Verification Failed", profileFlag=false.
        If profileOK is true & maxPayableEmi > 0:
            If rules dictate Auto-Approval -> Decision = AUTO_APPROVED, profileFlag=true, populate approvedLoanAmount, interestRate, maxTenor, processingFeePercentage, etc.
            If rules dictate Review Needed -> Decision = REFER, profileFlag=true, populate indicative offer params (or use defaults), add referralReasons.
        Default/Catch-all -> Decision = REFER, profileFlag=true.
    Return Output: Complete Camunda task with breOutputData map containing decisionStatus, offer params (if applicable), rejectionReasons/referralReasons, profileFlag, ruleVersion, riskScore etc.

9.6 Configuration Parameters

    ZEEBE_GATEWAY_ADDRESS, Credentials, TASK_TOPIC (calculateLoanOffer), WORKER_NAME, RULE_CONFIG_PATH, RULE_VERSION, INCOME_FACTOR_FOR_LOAN, DEFAULT_LOAN_IRR, DEFAULT_LOAN_TENOR, LOG_LEVEL.

### 10. Testing & Deployment Strategy (Detailed - Revised v1.5)
10.1 Testing Strategy

    10.1.1 Unit Testing: Android App (ViewModels incl. BureauConfirmationViewModel, Use Cases, Repositories, Utils), Python BRE (Rules, Obligation input handling), Backend LLM API Functions (request parsing, LLM calls, Firestore updates), Cloud Functions Orchestrator (Event Parsing, Drop-off Logic).
    10.1.2 Integration Testing: App<->Firebase, App<->Camunda, App<->Backend LLM API, App<->Cloud Functions Orch, Camunda<->BRE, Camunda<->Appwrite, Backend LLM API<->Firestore, Backend LLM API<->Gemini, Cloud Functions Orch<->Firestore/LLM/Email. Test conditional navigation logic involving Bureau Confirmation screen. Test asynchronous document status UI updates.
    10.1.3 End-to-End (E2E) Testing: Key Flows: Approve, Reject, Refer (with and without Bureau Confirmation/Obligation Recalculation), Document Upload (Image/PDF, Success/Failure scenarios), Drop-off Detection & Alerting, Dev Skips. Validate data consistency across Firestore (applications, documents, obligationRefinement, metadata).
    10.1.4 UI/UX Testing: Manual & Automated. Verify layouts, navigation (incl. new screen), animations, input handling, accessibility. Test Bureau Confirmation screen inputs/validation. Test Document Upload status summary section. Test Key Fact Sheet optional field display.
    10.1.5 Security Testing: SAST, DAST, Dependency Scanning, API Testing (App, Camunda, Backend LLM API, Cloud Functions Orch, Appwrite), Infra Review, Secrets Audit.

10.2 Deployment Strategy

    Environment Promotion: Dev -> Staging/UAT -> Production.
    Versioning: Semantic versioning for App, BPMN, BRE Worker, Backend LLM API Functions, Cloud Functions Orchestrator.
    Infrastructure: IaC preferred.
    Deployment Process: Deploy DB changes -> Deploy Backend (Camunda, BRE, Backend LLM API, Cloud Functions Orch) -> Deploy App RC -> UAT -> Schedule Prod Window -> Deploy Backend (Prod) -> Gradual App Rollout (Play Store) -> Monitor.
    Rollback Plan: Documented procedures for each component.

10.3 Monitoring Strategy

    APM: Firebase Performance Monitoring / Sentry.
    Backend Monitoring: Camunda Operate; Cloud provider monitoring for BRE, Cloud Functions Orch, Backend LLM API (CPU, Memory, Latency, Errors, Function Executions/Invocations); LLM provider dashboards (Gemini usage/costs).
    Database Monitoring: Firestore (Cloud Monitoring), Appwrite (Console/Monitoring).
    Business Metrics: Dashboards (Looker Studio) tracking Completion/Drop-off rates, BRE decisions, Bureau Confirmation completion, Document Processing success rates, Turnaround Time.
    Alerting: Configure alerts for critical errors (API 5xx, Crashes, Camunda Incidents, Cloud Function Errors/Timeouts - incl. Backend LLM API) via PagerDuty/Opsgenie/Email/Slack.

