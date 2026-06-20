# Personal Loan Android Application: Requirements and Technical Specification

**Version:** 1.4.0 (Incorporating Cloud Functions Orchestrator & Revised Schemas & Code Analysis)
**Date:** April 25, 2025
**Status:** Final Draft

## Table of Contents (Full Document):

1.  Introduction
    1.1 Purpose
    1.2 Scope
    1.3 Key Features Overview (Target State v1.4)
2.  Current State Analysis
    2.1 Existing Application Features & Flow (From v1.0 Code)
    2.2 Current Technical Architecture (From v1.0 Code)
    2.3 Current Data Structures (From v1.0 Code / Initial Docs)
3.  Proposed Enhancements & Changes (Functional Requirements - v1.4 Detailed)
    3.1 Core Loan Process Flow (Single Pass BRE)
    3.2 Metadata Collection & Orchestration (Firebase Cloud Functions)
    3.3 LLM Integration & Agentic AI Enhancements (Drop-off Analysis Only)
    3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen)
    3.5 Backend & Integration Changes (Single Pass BRE, Cloud Functions Orchestrator)
4.  System Interactions & Data Flow (Revised v1.4)
    4.1 Overview Diagram (Conceptual - Revised)
    4.2 Detailed Interaction Points & Data Exchange (Revised)
5.  Detailed Design & Implementation Plan (Revised v1.4)
    5.1 UI/UX Design Principles
    5.2 Screen-Specific Implementation Details (Detailed - Revised)
    5.3 Metadata Implementation Strategy (Subcollection & Cloud Functions)
    5.4 Metadata Orchestrator Implementation Strategy (Firebase Cloud Functions)
    5.5 LLM Integration Implementation Strategy (Drop-off Analysis via Cloud Function)
    5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite - Single Pass)
    5.7 Implementation Phases (Expanded & Detailed - Revised)
6.  Data Model Specification (Target State - v1.4 Detailed)
    6.1 Firestore Database Structure (Revised - Metadata Subcollection)
    6.2 Appwrite Bureau Database Structure (Revised - 3 Collections)
    6.3 Metadata Structure (Detailed - Subcollection)
    6.4 Data Catalog Reference (Revised - Separate Document v1.4)
7.  Technical Architecture (Target State - v1.4 Detailed)
8.  Orchestrator (Metadata/Drop-off) Design & Config Details (Firebase Cloud Functions)
9.  BRE (Python Worker) Design & Config Details (Single Pass)
10. Testing & Deployment Strategy (Detailed - Revised)

## 1. Introduction

### 1.1 Purpose

This document serves as the exhaustive and consolidated requirements and technical specification for the **v1.4** release of the Android Personal Loan application. It integrates the analysis of the existing application codebase, supporting documentation, the shift to a Single-Pass Business Rules Engine (BRE) flow, the defined Appwrite Bureau schema, and the implementation of the Metadata Orchestrator using **Firebase Cloud Functions**. It acts as the single source of truth for development, testing, and deployment.

### 1.2 Scope

This specification covers the Android mobile application (Kotlin, Jetpack Compose), its detailed interactions with all backend services (Firebase Suite, Camunda Platform 8, Python BRE Worker, Appwrite Bureau Service, LLM Services - Gemini/OpenAI for drop-off analysis via Cloud Functions, **Firebase Cloud Functions Metadata Orchestrator**, Email Service - Brevo, PAN Verification Service), target data structures, UI/UX principles and specific screen enhancements, metadata tracking implementation, bug fixes, removal of previous features (LinkedIn Integration, Bureau Data Confirmation Screen/Flow), and the phased implementation plan.

### 1.3 Key Features Overview (Target State v1.4)

* **Streamlined Loan Application:** A guided, multi-step personal loan application flow optimized for mobile users in India.
* **Modern UI/UX:** Built natively with Jetpack Compose adhering to Material 3 guidelines, featuring a continuous vertical form layout enhanced by Section-by-Section Progressive Reveal. Includes minimalist card-based design, clear typography, subtle animations, and standard navigation patterns (modern back icons, optional swipe-back gesture).
* **Agentic AI Assistant:** An integrated AI assistant accessible via a floating bubble (`AIAssistantBubble.kt`) providing contextual help and application review suggestions. Backend LLM analysis (Gemini 1.5 Flash / OpenAI 4o-mini) is used **exclusively for drop-off analysis**, triggered by the Metadata Orchestrator.
* **Instant Loan Offer Computation (Single Pass BRE):** Near real-time loan decisions (Approve/Reject/Refer) and offer parameter calculation using an integrated backend workflow involving a single call to the BRE, orchestrated via Camunda Platform 8 and executed by a Python BRE worker.
* **Credit Bureau Integration (Appwrite):** Automated fetching and utilization of credit bureau data (CIBIL) via an Appwrite service. Data is stored across three collections (`borrower_summary`, `enquiries`, `tradelines`). Process orchestrated by Camunda. Includes repository-level fallback logic (Appwrite -> Firestore -> Dummy Data).
* **OCR & Document Processing:** In-app OCR using ML Kit for PAN and ID cards. Document upload to Firebase Storage, with metadata and extraction results stored in Firestore (`documents` collection). Includes user feedback mechanisms for OCR results and image quality.
* **Detailed Metadata Collection:** Comprehensive tracking of user interactions, device info, screen/section timings, document/verification events, and errors. Metadata stored in a Firestore subcollection (`applications/{appId}/metadata`). Critical events trigger the Metadata Orchestrator.
* **Firebase Cloud Functions Metadata Orchestrator:** A dedicated backend service built on Firebase Cloud Functions responsible for receiving metadata events, detecting user drop-offs, triggering LLM analysis for dropped sessions, and sending email alerts via Brevo.
* **Firebase Backend:** Leverages Firebase for Phone Authentication, Firestore Database (application data, user profiles, document metadata, application metadata subcollections), and Cloud Storage.
* **Robust Architecture:** MVVM pattern, Repositories, Use Cases, ViewModels, Hilt DI. Data persistence primarily via direct Firestore updates from repositories, with API calls for specific actions/fallbacks.
* **Accessibility & Input Options:** Includes standard input fields (`EnhancedLoanTextField.kt`), date pickers (`DatePickerField`), state search (`StateSearchField`), and an implemented (though optional to enable fully) **Voice Input** feature (`VoiceInputButton.kt`, `GoogleVoiceRecognitionService.kt`).
* **User Settings:** Allows users to configure OCR service preference, toggle AI assistant, and manage document upload settings (enable/disable, image resolution) via the Settings screen (`SettingsScreen.kt`).

*(Removed: Bureau Data Confirmation & Obligation Refinement feature, LinkedIn Integration)*

## 2. Current State Analysis (Based on initial code review and documentation)

### 2.1 Existing Application Features & Flow: (From v1.0 Code)

* **Authentication:** Firebase Phone Authentication (OTP) (`LoginScreen.kt`, `LoginViewModel.kt`, `UserRepositoryImpl.kt`).
* **Core Flow Implementation:** As seen in `AppNavGraph.kt`, reflects the multi-step process. `ContinuousFormContainer.kt` used for layout.
* **UI:** Jetpack Compose, Material 3 (`Theme.kt`, `Type.kt`, `Shape.kt`). Progressive reveal using `AnimatedSections.kt`.
* **AI Assistant:** Floating bubble (`AIAssistantBubble.kt`) integrated.
* **Data Handling:** MVVM, Repositories (`RepositoryModule.kt`), Use Cases (`CreateApplicationUseCase.kt`, etc.), Hilt DI (`AppModule.kt`, `FirebaseModule.kt`, etc.). Firestore interaction via `LoanRepositoryImpl.kt`, `UserRepositoryImpl.kt`.
* **Integrations:** Retrofit (`NetworkModule.kt`). Interfaces for Camunda, Appwrite, PAN exist. ML Kit OCR (`MLKitOCRService.kt`). FileProvider (`file_provider.xml`). Document Upload Worker (`DocumentUploadWorker.kt`).

### 2.2 Current Technical Architecture: (Reflecting code + v1.4 plan)

* **Platform:** Android (Kotlin).
* **UI:** Jetpack Compose, Material 3.
* **Architecture:** MVVM, Hilt, Clean Architecture principles, Jetpack Navigation Compose.
* **Networking:** Retrofit, OkHttp (`NetworkModule.kt`).
* **Backend:** Firebase (Auth, Firestore, Storage), Camunda, Python BRE, Appwrite (via `AppwriteServiceImpl.kt`), LLM Services (via Orchestrator), **Firebase Cloud Functions (Metadata Orchestrator)**, External PAN Verification API (`PANApi.kt`), Email Service (Brevo).
* **Persistence:** Primarily direct Firestore updates via Repositories, with DataStore Preferences (`PreferencesDataSource.kt`) for settings and local caching.

### 2.3 Current Data Structures: (Reflecting code + v1.4 plan / samples)

* **Firestore:** Collections `users`, `applications` (with nested maps and `metadata` subcollection), `documents`, `employment_verifications`, `otps`, `employers` defined. Sample structures available in `sample_metadata_*.txt` files.
* **Appwrite:** Collections `borrower_summary`, `enquiries`, `tradelines` defined per `Appwrite Bureau Data Attributes.txt`.
* **Data Catalog:** v1.4 defines core elements.

## 3. Proposed Enhancements & Changes (Functional Requirements - v1.4 Detailed)

### 3.1 Core Loan Process Flow (Single Pass BRE)

The end-to-end user and system flow SHALL be updated as follows, incorporating a Single-Pass Business Rules Engine (BRE) approach:

1.  **Initiation:** User Logs in (Firebase Auth OTP), Accepts Privacy Policy (Consent stored in `users` collection).
2.  **Start:** User navigates Home, starts new application or resumes existing (`ResumeApplicationUseCase.kt` logic to handle loading state). `applications` record created/updated in Firestore (`CreateApplicationUseCase.kt`).
3.  **PAN Entry:** User enters/scans PAN.
    * App verifies PAN format (`InputValidator.kt`). App triggers PAN verification API (`VerifyPANUseCase.kt`).
    * **App triggers Bureau Data Fetch via the Camunda service (`CamundaService.kt`).**
    * Camunda orchestrates the call to the Appwrite Service to fetch bureau data (from `borrower_summary`, `enquiries`, `tradelines`).
    * Bureau data stored in Appwrite collections. `applicationId` and `panNumber` link records.
    * Screen auto-advances on successful PAN verification.
    * **Skip Option (Dev/Test Only):** A subtle skip button allows bypassing PAN entry. If skipped, PAN details in Firestore will be null, `panVerified` status 'N'.
4.  **User Data Entry:** User progresses through Personal Info, Employment Details screens. Progressive Reveal guides the user. Data saved progressively to the `applications` document in Firestore via ViewModels (`PersonalInfoViewModel.kt`, `EmploymentDetailsViewModel.kt`) calling repository methods (`LoanRepositoryImpl.updatePersonalInfo`, `updateEmploymentDetails`).
5.  **Document Upload:** User uploads required documents based on profile.
    * Documents uploaded to Firebase Storage, metadata stored in `documents` collection in Firestore (`UploadDocumentUseCase.kt`, `DocumentRepositoryImpl.uploadDocument`).
    * OCR/Extraction attempted (`ParseDocumentUseCase.kt`, `MLKitOCRService.kt`), status and key extracted data updated in `documents` record (`extractionStatus`, `extractedData`).
    * User can manage upload settings (enable/disable, resolution) via Settings Screen.
6.  **BRE Execution (Single Pass):** App compiles required data (personal, employment, bureau summary, document insights) and triggers the Camunda workflow (`CamundaService.kt`, `CalculateLoanOfferWithBREUseCase.kt`). Input data snapshotted in `applications.breInputData`.
7.  **Camunda Workflow:** Camunda orchestrates the single call to the Python BRE worker (`calculateLoanOffer` topic).
8.  **Python BRE:** Worker executes rules based on input (See Section 9.5). Returns decision (Approve/Reject/Refer), offer parameters, and `profileFlag`.
9.  **Loan Offer Display:** App receives BRE result. Result snapshotted in `applications.breOutputData`. `LoanOfferScreen.kt` displays outcome dynamically:
    * **Approve:** Shows offer, sliders enabled up to limits. "Proceed" to Emp Verification. Offer details stored in `applications.loanOffer`. Final selected values stored via `LoanRepository.saveLoanOfferSelection`.
    * **Reject:** Shows rejection message. Exit/Go Home.
    * **Refer:** Shows referral/review message. Display indicative Rate & Fee. Enable amount/tenor sliders. "Proceed" to Emp Verification. Applied amount/tenor stored in `applications.loanOffer`.
    * `profileFlag` stored in `applications.breOutputData`.
    * **Skip Option (Dev/Test Only):** Bypasses offer stage, treats outcome as 'Refer'.
10. **Employment Verification:** User completes verification (Email OTP or ID Card Scan). Status updated in `employment_verifications` collection.
11. **Key Fact Sheet:** User reviews final summary (incl. PAN/Mobile, Office Address/Work Email). AI suggestions box shown.
12. **Submit Application:** User submits. `applicationStatus` updated to SUBMITTED/UNDER_REVIEW. `submittedAt` timestamp recorded (`SubmitApplicationUseCase.kt`).
13. **Application Complete:** `ApplicationCompleteScreen.kt` displays final status dynamically: Approved (with Disbursal info, confetti animation), Rejected, or Submitted for Review. "Back to Home" button available.

### 3.2 Metadata Collection & Orchestration (Firebase Cloud Functions)

* App MUST automatically collect metadata: `deviceInfo`, `appSession`, `screenVisits`, `sectionTimings`, `documentEvents`, `verificationEvents`, `errorEvents` (See Data Catalog v1.4 Section 4.11).
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

### 3.3 LLM Integration & Agentic AI Enhancements (Drop-off Analysis Only)

* **Drop-off Analysis (Firebase Cloud Functions):**
    * Triggered by Orchestrator on detecting drop-off (>30s idle).
    * **Configurability:** LLM provider (Google Gemini / OpenAI) and model (gemini-1.5-flash-latest / gpt-4o-mini) configured in Cloud Function environment.
    * Orchestrator provides LLM with application data/metadata.
    * LLM returns JSON: { customerProfile, lastStep, followUpPriority, issuesSummary }.
    * Orchestrator forwards summary via email using Brevo.
* **Email Service Configuration:** Uses Brevo (free tier). API Key managed securely in Cloud Functions environment.
* **AI Assistant UI:** `AIAssistantBubble.kt` positioning fixed. No direct app-to-LLM calls. Provides contextual help and Key Fact Sheet suggestions.

### 3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen)

*(Incorporates observed code state and requirements)*

* **General:**
    * Modern back navigation icons (`ModernBackButton`) used consistently.
    * AI chat bubble (`AIAssistantBubble.kt`) positioned correctly above navigation bars using `padding` and `zIndex`.
    * **Optional:** Swipe-back gesture (`SwipeBackHandler.kt`) can be enabled for navigation.
* **OTP Screen (`LoginScreen.kt`):**
    * Implement Privacy Policy link/modal.
    * Ensure successful OTP verification updates `users` collection (Firestore) with `isPrivacyPolicyAccepted=true`, `privacyPolicyVersion`, `privacyPolicyAcceptedAt`.
* **Home Screen (`HomeScreen.kt`):**
    * Fix "Resume Application" logic in `HomeViewModel.kt` to correctly load state and navigate to `currentStep`.
    * Implement button actions: Chat (AI Assistant), Call (Dialer Intent), Email (Compose Intent).
    * Adjust layout to reduce excess top whitespace.
* **PAN Screen (`PANEntryScreen.kt`):**
    * Adjust button/card widths.
    * Implement auto-progression after successful verification (1.5s delay).
    * Implement dev-only skip button (bottom-left).
    * Implement OCR result preview (`OCRResultPreview`) and edit dialog (`EditExtractedFieldsDialog`).
* **Personal Info Screen (`PersonalInfoScreen.kt`):**
    * Ensure ViewModel updates `currentStep` on progression.
    * Use `DatePickerDialog` for date selection.
    * Use `StateSearchField` for state selection.
* **Employment Info Screen (`EmploymentDetailsScreen.kt`):**
    * Conditional UI: Work Email optional/readonly for Government/Private; Designation required for Govt, removed for Private.
    * Employee ID field removed.
    * State preservation bug fixed.
    * LinkedIn code removed.
* **Document Upload Screen (`DocumentUploadScreen.kt`):**
    * Use `EnhancedDocumentUploadCard` for each document type.
    * Handle Camera permission (`rememberPermissionState`).
    * Manage processing state (`DocumentProcessingState`).
    * Track metadata via `DocumentViewModel.kt`.
* **Loan Offer Screen (`LoanOfferScreen.kt`):**
    * Implement dynamic content based on `offerStatus` (APPROVED/REJECTED/REFERRAL/ERROR/LOADING).
    * Enable sliders (`LoanOfferSlider.kt`) for APPROVED/REFERRAL states within calculated limits.
    * Implement dev-only bypass button (`bypassApproval`) sets state to REFERRAL.
* **Settings Screen (`SettingsScreen.kt`):**
    * Implement toggles/selectors for OCR Service, AI Assistant Enabled, Document Upload Enabled, Document Image Resolution (`ImageResolution` enum).
* **Employment Verification Screen (`EmploymentVerificationScreen.kt`):**
    * Implement conditional UI based on `employmentType`.
    * Implement skip verification confirmation dialog.
    * Use `VerificationMethod.kt` enum correctly.
* **Key Fact Sheet Screen (`KeyFactSheetScreen.kt`):**
    * Display required fields: PAN, Mobile, Office Address, Work Email.
    * Style AI Suggestions box for readability.
* **Application Submitted Screen (`ApplicationCompleteScreen.kt`):**
    * Use static confirmation (remove jumping animation). Implement `SuccessConfetti` effect.
    * Implement dynamic content display based on final `applicationStatus`.
    * Include "Back to Home" button.
* **Voice Input:**
    * Integrate `VoiceInputButton.kt` optionally near relevant text fields (`EnhancedLoanTextField.kt`) based on user setting (if a setting is added) or build flag. Requires `RECORD_AUDIO` permission.

### 3.5 Backend & Integration Changes (Single Pass BRE, Cloud Functions Orchestrator)

* **Code Removal:** LinkedIn code, App-side LLM code (`LlmApi.kt`, `LlmService.kt`), Bureau Confirmation screen code removed.
* **Camunda Integration (`CamundaService.kt`):** Focuses on triggering the single-pass workflow.
* **Appwrite Integration (`AppwriteServiceImpl.kt`):** Fetches from 3 collections. Includes fallback logic (Appwrite -> Firestore -> Dummy).
* **BRE Input Preparation:** Logic prepares `breInputData` for single pass.
* **BRE Output Handling:** ViewModels handle `breOutputData` (incl. `profileFlag`).
* **Metadata Orchestrator API:** App implements `MetadataApi.kt` (Retrofit) to call Cloud Function (`processEvent`). `MetadataRepositoryImpl.kt` uses this.
* **FileProvider:** Configuration verified.
* **Persistence Strategy:** Repositories (`LoanRepositoryImpl.kt`, `UserRepositoryImpl.kt`) prioritize direct Firestore updates for core application data and state, with API calls potentially used for specific actions or as fallbacks.

## 4. System Interactions & Data Flow (Revised v1.4)

### 4.1 Overview Diagram (Conceptual - Revised)

```mermaid
graph LR
    subgraph User Device
        App[Android App]
    end
    subgraph Firebase
        Auth(Firebase Auth)
        Firestore[(Firestore DB)]
        Storage(Firebase Storage)
        CloudFunc[Cloud Functions Orchestrator]
    end
    subgraph Core Backend
        Camunda(Camunda Engine)
        PythonBRE(Python BRE Worker)
    end
    subgraph Supporting Services
        Appwrite(Appwrite DB Service)
        LLM(LLM Service - Gemini/OpenAI)
        Email(Email Service - Brevo)
        PANVerify(PAN Verification API)
    end

    User(User) -- Interacts --> App
    App -- Auth Requests --> Auth
    App -- CRUD App Data, Doc Meta --> Firestore
    App -- Write Metadata Events --> Firestore[metadata subcollection]
    App -- Store/Retrieve Docs --> Storage
    App -- Verify PAN --> PANVerify
    App -- Start Loan Workflow / Trigger Bureau Fetch --> Camunda
    App -- Send Events/Metadata --> CloudFunc[processEvent Endpoint]

    CloudFunc -- Reads App Status/Metadata --> Firestore
    CloudFunc -- Writes App Status (Dropped Off) --> Firestore
    CloudFunc -- Request Drop-off Analysis --> LLM
    CloudFunc -- Send Summary --> Email
    CloudFunc -- Writes Event to Subcollection --> Firestore[metadata subcollection] %% Added explicit write back %%

    Camunda -- Request Bureau Fetch (Delegate) --> Appwrite
    Camunda -- Assign Task (Single Pass) --> PythonBRE
    PythonBRE -- Complete Task --> Camunda
    PythonBRE -- Read Rules --> RulesConfig[Rule Files/DB]

    Appwrite -- Serves Bureau Data --> Camunda

    LLM -- Provides Drop-off Analysis --> CloudFunc

    Firestore -- Serves Data/Metadata --> App
    Firestore -- Serves Data/Metadata --> CloudFunc
    Auth -- Returns Auth State --> App
    Storage -- Serves Docs --> App
    PANVerify -- Returns Verification --> App
    Email -- Sends Alert --> User/Support
'''


4.2 Detailed Interaction Points & Data Exchange (Revised)

    App <> Firebase Auth: Standard OTP flow.
    App -> Firestore: Writes/Updates users, applications, documents, employment_verifications, otps. Writes events to applications/{appId}/metadata. Reads app state, user profile. Primary mechanism for saving user progress.
    App <> Firebase Storage: Uploads/downloads document files.
    App -> PAN Verification API: Sends PAN, receives validation status/name.
    App -> Camunda Service: Initiates workflow (startLoanProcessAndFetchBureau).
    App -> Cloud Functions Orchestrator: Sends JSON metadata event payloads via HTTP POST to the processEvent endpoint.
    Cloud Functions Orchestrator <> Firestore: Reads applications status/timestamps, reads/writes metadata subcollection events. Writes applications.applicationStatus = DROPPED_OFF. Manages backgrounded_apps collection.
    Cloud Functions Orchestrator -> LLM Service (Drop-off): Sends application snapshot/metadata. Receives JSON summary. Uses configured provider (Gemini/OpenAI).
    Cloud Functions Orchestrator -> Email Service (Brevo): Sends formatted drop-off summary email.
    Camunda <> Appwrite: Camunda Service Task fetches bureau data from 3 collections based on PAN.
    Camunda <> Python BRE: Camunda assigns External Task (calculateLoanOffer). BRE worker executes rules, completes task.
    Python BRE -> Rules Config: Reads rule definitions.

5. Detailed Design & Implementation Plan (Revised v1.4)
5.1 UI/UX Design Principles

    Material 3 Adherence: Strictly follow M3 guidelines for components, colors, typography (Theme.kt, Color.kt, Type.kt).
    Jetpack Compose: Implement all UI declaratively.
    Progressive Reveal: Utilize ContinuousFormContainer.kt and AnimatedSections.kt for smooth, section-by-section form presentation.
    Clean & Minimalist: Emphasize clarity, adequate white space, use card-based layouts (CardShape) for grouping information.
    Responsiveness: Ensure layouts adapt to various screen sizes using Compose modifiers.
    Accessibility: Implement content descriptions, ensure sufficient touch target sizes, support font scaling, maintain good color contrast.
    Performance: Optimize Compose layouts, minimize unnecessary recompositions, use lazy lists where appropriate (DocumentUploadScreen.kt).
    Navigation: Use Jetpack Navigation Compose (AppNavGraph.kt) with clear transitions. Provide consistent back navigation (modern icons, optional swipe gesture).

5.2 Screen-Specific Implementation Details (Detailed - Revised)

(Reflects code state and v1.4 requirements)

    General: Utilize reusable Composables (ModernBackButton, EnhancedLoanTextField, LoanOfferSlider, AIAssistantBubble, ErrorState, LoadingState). Implement optional SwipeBackHandler.
    OTP Screen (LoginScreen.kt): Implement OTP input (ModernOtpInput), Privacy Policy link/modal. Ensure ViewModel updates Firestore users.
    Home Screen (HomeScreen.kt): Fix Resume flow in HomeViewModel.kt. Implement button actions (Intents: Dialer, Email; AI Assistant toggle). Adjust layout spacing.
    PAN Screen (PANEntryScreen.kt): Use EnhancedLoanTextField for PAN input. Implement camera/gallery launch via ActivityResultLaunchers. Handle OCRScanState. Show OCRResultPreview and EditExtractedFieldsDialog. Implement auto-progress on verification success. Implement dev skip button.
    Personal Info Screen (PersonalInfoScreen.kt): Use EnhancedLoanTextField for inputs. Use DatePickerDialog via DatePickerField. Use StateSearchField for state. Ensure ViewModel updates currentStep on save.
    Employment Info Screen (EmploymentDetailsScreen.kt): Implement conditional UI logic in Composable based on employmentType. Use EmployerSearchField. Remove Employee ID/Designation fields as specified. Fix state preservation.
    Document Upload Screen (DocumentUploadScreen.kt): Use EnhancedDocumentUploadCard. Handle camera permission. Manage DocumentProcessingState. Implement file selection/camera launch via ActivityResultLaunchers. Track metadata via ViewModel. Add user settings for upload enable/disable and resolution.
    Loan Offer Screen (LoanOfferScreen.kt): Handle different LoanOfferUI.Status in Composable. Use LoanOfferSlider for amount/tenure adjustment. Implement dev bypass button. Display EMI/Fee details (EMIInfoCard).
    Settings Screen (SettingsScreen.kt): Implement toggles/radio buttons for OCR Service (OCRServiceSelector), AI Assistant, Document Upload Enabled, Document Image Resolution (ImageResolution enum). Implement "Clear Data" functionality for debug builds.
    Employment Verification Screen (EmploymentVerificationScreen.kt): Implement conditional UI. Use VerificationMethodSelector. Implement OTP input and ID card scan placeholders/flow. Add skip confirmation dialog.
    Key Fact Sheet Screen (KeyFactSheetScreen.kt): Display summary using ReviewSection and LabeledValue. Style AI suggestions list. Handle "Edit" actions by navigating back to the relevant screen.
    Application Submitted Screen (ApplicationCompleteScreen.kt): Implement static confirmation graphic/text. Add SuccessConfetti. Implement dynamic content display based on final applicationStatus. Provide "Back to Home" action.
    Voice Input: Conditionally display VoiceInputButton near text fields if feature is enabled. Ensure RECORD_AUDIO permission is handled. Pass results to onValueChange of the associated text field.

5.3 Metadata Implementation Strategy (Subcollection & Cloud Functions)

    Data Structures: Use Kotlin data classes in MetadataModels.kt (ScreenVisit, SectionTiming, DocumentEvent, etc.).
    Collection Logic (App):
        Utilize Android lifecycle observers, Navigation listeners, and ViewModel logic (DocumentViewModel.kt, PANViewModel.kt, LoanOfferViewModel.kt, etc.) to capture events.
        Instantiate metadata data classes with relevant details (timestamps, screen names, event status, etc.).
    Storage & Orchestration Trigger (App):
        Inject MetadataRepository.kt into ViewModels.
        Call metadataRepository.sendMetadataEvent(appId, eventType, eventData).
        MetadataRepositoryImpl.sendMetadataEvent first writes the structured event data to applications/{appId}/metadata/{eventId} in Firestore.
        Then, it makes an asynchronous HTTP POST call to the configured Cloud Functions processEvent endpoint using MetadataApi.kt (Retrofit).
        Log errors but prioritize local Firestore write completion.

5.4 Metadata Orchestrator Implementation Strategy (Firebase Cloud Functions)

    Implementation: Firebase Cloud Functions using TypeScript (as per cloud-functions-guide.txt).
    Core Functions:
        processEvent (HTTPS Trigger): Receives event payload from app, validates, stores in Firestore metadata subcollection, updates backgrounded_apps collection based on APP_BACKGROUNDED/APP_FOREGROUNDED events.
        dropOffChecker (Scheduled Trigger): Periodically queries backgrounded_apps, checks applications status and recent metadata activity to confirm drop-off, triggers analyzeDropOff.
    Logic: Follows the detailed implementation steps provided in cloud-functions-guide.txt, including Firestore interactions, scheduling, and state management for drop-off detection.
    Configuration: Securely manage API keys (LLM, Brevo), endpoints, timeouts via Firebase environment configuration.

5.5 LLM Integration Implementation Strategy (Drop-off Analysis via Cloud Function)

    API Definition: Internal to the Cloud Functions orchestrator (dropoff/analyzeDropOff.ts).
    DI (Cloud Functions): Handled within the function's environment.
    Drop-off Analysis Call (Cloud Functions): The analyzeDropOff function calls the configured LLM API (Gemini or OpenAI) with application data and metadata. Handles response parsing (JSON preferred, text fallback).
    App Integration: No direct LLM calls. App-side LLM code removed.

5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite - Single Pass)

    Camunda Workflow (BPMN): Single-pass model: Start -> Get Bureau (Appwrite via Connector) -> Calculate Offer (External Task for BRE) -> Decision Gateway -> End.
    Python BRE Worker: Subscribes to calculateLoanOffer topic via pyzeebe. Executes single-pass rules. Returns decision/offer/flag.
    Appwrite Service: AppwriteServiceImpl.kt queries borrower_summary, enquiries, tradelines. Includes fallback to Firestore/dummy data.
    App <> Camunda Interface: CamundaService.kt triggers workflow (startLoanProcessAndFetchBureau).

5.7 Implementation Phases (Expanded & Detailed - Revised)

(Reflects Cloud Functions implementation)

    Phase 1: Foundation & Data (Weeks 1-2)
        Task 1.1: Finalize v1.4 Data Catalog & Enums.
        Task 1.2: Implement Firestore schema changes (metadata subcollection).
        Task 1.3: Set up Appwrite collections & schema.
        Task 1.4: Refine Architecture diagrams (v1.4).
        Task 1.5: Set up Firebase Cloud Functions project structure.
    Phase 2: Core Backend - Single Pass BRE (Weeks 3-5)
        Task 2.1: Develop/Deploy Camunda BPMN (single-pass).
        Task 2.2: Implement Camunda <> Appwrite connector logic.
        Task 2.3: Develop Python BRE worker (single-pass logic).
        Task 2.4: Integrate App -> Camunda trigger.
        Task 2.5: Integrate App to receive BRE results (from Firestore).
    Phase 3: Metadata & Orchestration (Weeks 4-6)
        Task 3.1: Implement metadata collection & sending in Android App (MetadataRepositoryImpl.sendMetadataEvent).
        Task 3.2: Develop & Deploy Firebase Cloud Functions Orchestrator (processEvent, dropOffChecker).
        Task 3.3: Implement App -> Cloud Functions API interface (MetadataApi.kt).
        Task 3.4: Implement Orchestrator drop-off detection logic (Cloud Function).
        Task 3.5: Integrate Cloud Function -> LLM call (Drop-off Analysis).
        Task 3.6: Integrate Cloud Function -> Email alert (Brevo).
    Phase 4: App UI & Bug Fixes (Weeks 6-8)
        Task 4.1: Implement specific UI fixes/enhancements (Sec 3.4).
        Task 4.2: Refine Loan Offer screen logic (single-pass display).
        Task 4.3: Refine Application Complete screen logic (final states).
        Task 4.4: Address critical bugs (incl. FileProvider).
        Task 4.5: Remove Bureau Confirmation screen code.
        Task 4.6: Remove App-side LLM integration code.
        Task 4.7: Remove LinkedIn integration code.
    Phase 5: Testing (Weeks 8-10)
        Task 5.1: Unit Testing (Android, BRE, Cloud Functions).
        Task 5.2: Integration Testing (App<->Firebase, App<->Camunda, App<->Cloud Functions, Camunda<->BRE, Camunda<->Appwrite, Cloud Functions<->Firestore/LLM/Email).
        Task 5.3: E2E Testing (Approve, Reject, Refer, Drop-off, Skips).
        Task 5.4: UI Testing (Manual & Automated).
        Task 5.5: Security Audit.
    Phase 6: Deployment & Monitoring (Week 11)
        Task 6.1: Prepare release builds.
        Task 6.2: Deploy backend (Camunda, Python BRE, Cloud Functions).
        Task 6.3: Configure production monitoring (incl. Cloud Functions).
        Task 6.4: Go-live & monitoring.

6. Data Model Specification (Target State - v1.4 Detailed)

(Refers to the updated Loan Application Data Catalog v1.4 document for definitions)
6.1 Firestore Database Structure (Revised - Metadata Subcollection)

    Defined in Data Catalog v1.4 Sections 4.1 - 4.5, 4.7 - 4.10.
    Metadata stored in applications/{appId}/metadata subcollection.

6.2 Appwrite Bureau Database Structure (Revised - 3 Collections)

    Defined in Data Catalog v1.4 Section 4.6.
    Collections: borrower_summary, enquiries, tradelines.

6.3 Metadata Structure (Detailed - Subcollection)

    Defined in Data Catalog v1.4 Section 4.11.

6.4 Data Catalog Reference (Revised - Separate Document v1.4)

    The Loan Application Data Catalog v1.4 is the source of truth.

7. Technical Architecture (Target State - v1.4 Detailed)
7.1 Android Mobile Client

    Platform: Native Android, Kotlin.
    UI: Jetpack Compose, Material 3. Components: EnhancedLoanTextField.kt, LoanOfferSlider.kt, AIAssistantBubble.kt, ContinuousFormContainer.kt, AnimatedSections.kt, ModernBackButton.kt, VoiceInputButton.kt, etc.
    Architecture: MVVM, Hilt DI, Use Cases, Repositories.
    Data Handling: Compose State, ViewModels, DataStore Preferences, Retrofit APIs, Firestore SDK (primary persistence), Firebase Storage SDK, ML Kit OCR, WorkManager.
    Connectivity: HTTPS, AuthInterceptor.

7.2 Firebase Suite

    Authentication: Phone OTP Auth.
    Firestore Database: Primary data store (users, applications with nested maps and metadata subcollection, documents, etc.). Firestore Security Rules.
    Cloud Storage: Document file storage. Storage Security Rules.
    Cloud Functions (Metadata Orchestrator): Event-driven backend logic for metadata processing, drop-off detection, LLM calls, email alerts.

7.3 Core Loan Process Backend

    Camunda Platform 8: Process orchestration (single-pass BRE flow). REST API interaction with App.
    Python BRE Worker: External Task worker executing business rules for loan decisions/offers.

7.4 Supporting Services

    Appwrite DB Service: Stores/serves bureau data (3 collections). Accessed via API (called by Camunda).
    LLM Service (Google Gemini / OpenAI): Called by Cloud Functions for drop-off analysis. Configurable provider/model.
    Metadata Orchestrator: Implemented as Firebase Cloud Functions.
    Email Service (Brevo): Called by Cloud Functions for alerts.
    PAN Verification API: External API called by the App.

7.5 Communication & Security

    HTTPS/TLS for all communication.
    Authentication: Firebase Auth (App), JWT (App->API), Secure keys/Service Accounts (Backend<->Backend).
    Authorization: Firestore/Storage Security Rules, API endpoint checks.
    Secrets Management: Use secure secrets management (e.g., Google Secret Manager) for API keys, credentials. NO hardcoding.

8. Orchestrator (Metadata/Drop-off) Design & Config Details (Firebase Cloud Functions)
8.1 Purpose & Distinction

Monitors user journey via metadata, detects drop-offs, triggers LLM analysis & alerts. Distinct from Camunda loan process engine. Implemented using Firebase Cloud Functions.
8.2 Implementation

Firebase Cloud Functions (TypeScript recommended, as per cloud-functions-guide.txt).
8.3 High-Level Process Flow (Cloud Functions)

    Event Reception (HTTP Trigger - processEvent): Receives metadata from App, stores in Firestore metadata subcollection, manages backgrounded_apps state.
    Drop-off Check (Scheduled Trigger - dropOffChecker): Queries backgrounded_apps, verifies app status/metadata activity, triggers analyzeDropOff if confirmed dropped.

8.4 Configuration Parameters (Cloud Functions Environment)

    DROP_OFF_TIMEOUT_SECONDS (e.g., 30)
    LLM_PROVIDER ("GOOGLE" or "OPENAI")
    LLM_MODEL_NAME ("gemini-1.5-flash-latest" or "gpt-4o-mini")
    LLM_API_KEY (Secret)
    EMAIL_API_KEY (Brevo API Key - Secret)
    EMAIL_FROM
    EMAIL_TO
    Firebase Project configuration (implicit).

9. BRE (Python Worker) Design & Config Details (Single Pass)
9.1 Purpose

Execute loan eligibility/offer rules in single pass. Operates via Camunda External Task pattern.
9.2 Technology Stack

Python 3.x, pyzeebe, optional rules engine, Docker.
9.3 Interaction Model

Polls Zeebe for calculateLoanOffer topic, executes handler, completes task with breOutputData.
9.4 Input/Output Handling

Input: breInputData map. Output: breOutputData map (incl. profileFlag).
9.5 Rule Categories & Logic (Single Pass - Illustrative)

Handler for calculateLoanOffer topic:

    Input Prep: Get breInputData, determine effectiveMonthlyEmi. Derive intermediates (Age, Income Stability, etc.).
    PAN Check: Set internal profileOK flag based on verification status.
    Hard Rules: Check Min/Max Age, Bureau Flags, Employment, Min Income, Data Presence. If fail -> REJECT, set profileFlag, STOP.
    Risk/Calc: Calculate Risk Score, Max Affordable EMI (maxPayableEmi). If maxPayableEmi <= 0 -> REJECT, set profileFlag, STOP. Calculate Max Loan Amount. Determine Rate/Tenor/Fee tiers.
    Final Decision:
        If profileOK is false -> REJECT ("Profile Verification Failed", profileFlag=false).
        If profileOK is true & maxPayableEmi > 0:
            If Auto-Approve -> AUTO_APPROVED (profileFlag=true), populate offer.
            If Review Needed -> REFER (profileFlag=true), populate indicative offer/reasons.
        Default -> REFER (profileFlag=true).

9.6 Configuration Parameters

ZEEBE_GATEWAY_ADDRESS, Credentials, TASK_TOPIC (calculateLoanOffer), WORKER_NAME, RULE_CONFIG_PATH, RULE_VERSION, INCOME_FACTOR_FOR_LOAN, DEFAULT_LOAN_IRR, DEFAULT_LOAN_TENOR, LOG_LEVEL.
10. Testing & Deployment Strategy (Detailed - Revised)
10.1 Testing Strategy

    10.1.1 Unit Testing: Android App (ViewModels, Use Cases, Repositories, Utils), Python BRE (Rules, Calculations), Cloud Functions (Event Parsing, Drop-off Logic).
    10.1.2 Integration Testing: App<->Firebase, App<->Camunda, App<->Cloud Functions, Camunda<->BRE, Camunda<->Appwrite, Cloud Functions<->Firestore, Cloud Functions<->LLM, Cloud Functions<->Email.
    10.1.3 End-to-End (E2E) Testing: Key Flows: Approve, Reject, Refer, Drop-off + LLM + Email, Skips, Error Handling. Validate data consistency.
    10.1.4 UI/UX Testing: Manual & Automated (Espresso/Compose). Verify layouts, navigation, animations, accessibility, responsiveness. Test voice input if enabled. Test Settings screen options.
    10.1.5 Security Testing: SAST, DAST, Dependency Scanning, API Testing, Infra Review, Secrets Audit.

10.2 Deployment Strategy

    Environment Promotion: Dev -> Staging/UAT -> Production.
    Versioning: Semantic versioning for App, BPMN, BRE Worker, Cloud Functions.
    Infrastructure: IaC preferred.
    Deployment Process: Deploy DB changes -> Deploy Backend (Camunda, BRE, Cloud Functions) -> Deploy App RC -> UAT -> Schedule Prod Window -> Deploy Backend (Prod) -> Gradual App Rollout (Play Store) -> Monitor.
    Rollback Plan: Documented procedures for each component.

10.3 Monitoring Strategy

    APM: Firebase Performance Monitoring / Sentry.
    Backend Monitoring: Camunda Operate; Cloud provider monitoring for BRE, Cloud Functions, Appwrite (CPU, Memory, Latency, Errors, Function Executions/Invocations); LLM provider dashboards.
    Database Monitoring: Firestore (Cloud Monitoring), Appwrite (Console/Monitoring).
    Business Metrics: Dashboards (Looker Studio) tracking Completion/Drop-off rates, BRE decisions, Turnaround Time, etc.
    Alerting: Configure alerts for critical errors (API 5xx, Crashes, Camunda Incidents, Cloud Function Errors/Timeouts) via PagerDuty/Opsgenie/Email/Slack.