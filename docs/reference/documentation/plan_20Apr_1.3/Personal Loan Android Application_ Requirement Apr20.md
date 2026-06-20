***

## Corrected: Personal Loan Android Application: Requirement Apr20.md

```markdown
# Personal Loan Android Application: Requirements and Technical Specification

**Version:** 1.3.0 (Detailed)
**Date:** April 20, 2025
**Status:** Final Draft (Incorporating User Feedback)

## Table of Contents (Full Document):

1.  Introduction
    1.1 Purpose
    1.2 Scope
    1.3 Key Features Overview (Target State v1.3)
2.  Current State Analysis
    2.1 Existing Application Features & Flow
    2.2 Current Technical Architecture
    2.3 Current Data Structures
3.  Proposed Enhancements & Changes (Functional Requirements - v1.3 Detailed)
    3.1 Core Loan Process Flow (Revised - Single Pass BRE)
    3.2 Metadata Collection & Orchestration
    3.3 LLM Integration & Agentic AI Enhancements (Drop-off Analysis Only)
    3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen)
    3.5 Backend & Integration Changes (Revised - Single Pass BRE)
4.  System Interactions & Data Flow (Revised v1.3)
    4.1 Overview Diagram (Conceptual - Revised)
    4.2 Detailed Interaction Points & Data Exchange (Revised)
5.  Detailed Design & Implementation Plan (Revised v1.3)
    5.1 UI/UX Design Principles
    5.2 Screen-Specific Implementation Details (Detailed - Revised)
    5.3 Metadata Implementation Strategy (Subcollection)
    5.4 Metadata Orchestrator Implementation Strategy
    5.5 LLM Integration Implementation Strategy (Drop-off Analysis)
    5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite - Revised Single Pass)
    5.7 Implementation Phases (Expanded & Detailed - Revised)
6.  Data Model Specification (Target State - v1.3 Detailed)
    6.1 Firestore Database Structure (Revised - Single Pass BRE)
    6.2 Appwrite Bureau Database Structure (Revised - 3 Collections)
    6.3 Metadata Structure (Detailed - Subcollection)
    6.4 Data Catalog Reference (Revised - Separate Document v1.3)
7.  Technical Architecture (Target State - v1.3 Detailed)
8.  Orchestrator (Metadata/Drop-off) Design & Config Details
9.  BRE (Python Worker) Design & Config Details (Revised - Single Pass)
10. Testing & Deployment Strategy (Detailed - Revised)

## 1. Introduction

### 1.1 Purpose

This document serves as the exhaustive and consolidated requirements and technical specification for the next major version (v1.3) of the Android Personal Loan application for a major Indian bank. It integrates the analysis of the existing application codebase, supporting documentation, and all planned enhancements discussed (including the shift to a Single-Pass Business Rules Engine (BRE) flow) into a single, detailed source of truth for development, testing, and deployment.

### 1.2 Scope

This specification covers the Android mobile application (Kotlin, Jetpack Compose), its detailed interactions with all backend services (Firebase Suite, Camunda Platform 8, Python BRE Worker, Appwrite Bureau Service, LLM Services - Gemini/OpenAI for drop-off analysis, Metadata Orchestrator, PAN Verification Service, Email Service), target data structures, UI/UX principles and specific screen enhancements, new feature implementations (including metadata tracking, LLM-driven drop-off interventions), bug fixes, removal of previous features (LinkedIn Integration, Bureau Data Confirmation Screen/Flow), and the phased implementation plan.

### 1.3 Key Features Overview (Target State v1.3)

* **Streamlined Loan Application:** A guided, multi-step personal loan application flow optimized for mobile users in India.
* **Modern UI/UX:** Built natively with Jetpack Compose adhering to Material 3 guidelines, featuring a continuous vertical form layout enhanced by Section-by-Section Progressive Reveal for reduced cognitive load and improved flow momentum. Includes minimalist card-based design, clear typography, and subtle animations.
* **Agentic AI Assistant:** An integrated AI assistant (using Gemini 2.0 Flash or OpenAI 4O-mini APIs via `AIApi.kt`, `AIAssistantService.kt`) accessible via a floating bubble (`AIAssistantBubble.kt`) on most screens, providing contextual help, document feedback (planned), application review suggestions (Key Fact Sheet), and enabling backend LLM analysis for drop-offs.
* **Instant Loan Offer Computation (Single Pass BRE):** Near real-time loan decisions (Approve/Reject/Refer) and offer parameter calculation (Amount, Rate, Tenor, Fee) using an integrated backend workflow involving a single call to the BRE. This involves:
    * Orchestration via Camunda Platform 8.
    * Rule execution via a dedicated Python Business Rules Engine (BRE) worker.
* **Credit Bureau Integration:** Automated fetching and utilization of credit bureau data (e.g., CIBIL) via an Appwrite service (interface `AppwriteService.kt`, implementation `AppwriteServiceImpl.kt`) based on user's PAN, orchestrated by Camunda. Data used for pre-fill, validation, and BRE input.
* **OCR & Document Processing:** In-app Optical Character Recognition (OCR) using ML Kit (`MLKitOCRService.kt`) for extracting data from PAN cards, and ID cards. Includes document upload to Firebase Storage (`DocumentRepositoryImpl.kt`), validation checks, and mechanisms for providing user feedback on document quality/extraction success. Planned extension points for alternative OCR services (`OCRServiceSelector.kt`).
* **Detailed Metadata Collection:** Comprehensive tracking of user interactions, device information, screen timings, section completion times, document processing events, and drop-off indicators for analytics, monitoring, and proactive intervention via a dedicated Metadata Orchestrator. Metadata stored in a dedicated Firestore subcollection.
* **Firebase Backend:** Leverages Firebase for core backend functions: Phone Authentication (OTP), Firestore Database (application data, user profiles, document metadata, application metadata subcollections, configurations), and Cloud Storage (document uploads).
* **Robust Architecture:** Follows MVVM pattern with clear separation of concerns using Repositories (`RepositoryModule.kt`), Use Cases (e.g., `CalculateLoanOfferWithBREUseCase.kt`, `UploadDocumentUseCase.kt`), ViewModels (e.g., `LoanOfferViewModel.kt`, `PANViewModel.kt`), and Dependency Injection via Hilt (`AppModule.kt`).

*(Removed: Bureau Data Confirmation & Obligation Refinement feature)*

## 2. Current State Analysis (Based on initial code review and documentation)

*(This section remains largely the same as v1.2 analysis, describing the starting point before v1.3 changes. Key references below.)*

### 2.1 Existing Application Features & Flow:

* **Authentication:** Firebase Phone Authentication (OTP).
* **Core Flow Implementation (as described in original doc):** Splash -> Login -> Privacy -> Home -> PAN -> Personal Info -> Employment -> Document Upload -> Loan Offer -> Employment Verification -> Key Fact Sheet -> Application Complete.
* **UI:** Jetpack Compose, Material 3. Progressive reveal partially implemented. Navigation via Jetpack Navigation Compose.
* **AI Assistant:** Floating bubble present, interacts with ViewModel and backend services.
* **Data Handling:** MVVM, Repositories, Use Cases, Hilt DI. Data models defined. Firestore used.
* **Integrations:** Retrofit for APIs. Stubs/interfaces for Camunda, Appwrite, PAN exist. ML Kit for OCR integrated. FileProvider configured but potentially requires fix. LinkedIn integration exists but marked for removal.

### 2.2 Current Technical Architecture: (As described in v1.1/v1.2)

* **Platform:** Android (Kotlin).
* **UI:** Jetpack Compose, Material 3.
* **Architecture:** MVVM, Hilt, Clean Architecture, Jetpack Navigation Compose.
* **Networking:** Retrofit, OkHttp.
* **Backend:** Firebase (Auth, Firestore, Storage), Planned: Camunda, Python BRE, Appwrite (simulated via Firestore `bureau_reports`), LLM Services (Gemini/OpenAI), External PAN Verification API.

### 2.3 Current Data Structures: (Based on documentation and sample records)

* **Firestore:** Collections `users`, `applications`, `documents`, `bureau_reports` (simulation), `bre_input`, `bre_output`, `loan_offers`, `employers`, `otps`, `employment_verifications` defined with schemas.
* **Appwrite (Planned/Simulated):** Single collection concept (`bureau_reports` in Firestore sim) holding flat bureau data structure. (Note: v1.3 requires revised Appwrite structure with 3 collections).
* **Data Catalog v1.0/v1.2:** Defines core data elements, relationships, enums. (Note: v1.3 Data Catalog supersedes previous versions).

## 3. Proposed Enhancements & Changes (Functional Requirements - v1.3 Detailed)

### 3.1 Core Loan Process Flow (Revised - Single Pass BRE)

The end-to-end user and system flow SHALL be updated as follows, incorporating a Single-Pass Business Rules Engine (BRE) approach:

1.  **Initiation:** User Logs in (Firebase Auth OTP), Accepts Privacy Policy (Consent stored in `users` collection).
2.  **Start:** User navigates Home, starts new application or resumes existing (`ResumeApplicationUseCase.kt` logic to be fixed). `applications` record created/updated in Firestore (`CreateApplicationUseCase.kt`).
3.  **PAN Entry:** User enters/scans PAN.
    * App verifies PAN format (`InputValidator.kt`). App triggers PAN verification API (`VerifyPANUseCase.kt`).
    * **Crucially, App triggers Bureau Data Fetch via the Camunda service (`CamundaService.kt`, `WorkspaceBureauReportUseCase.kt`).**
    * Camunda orchestrates the call to the Appwrite Service to fetch bureau data.
    * Bureau data (Borrower Summary, Enquiries & Tradelines) is stored in three separate Appwrite collections (`borrower_summary`, `enquiries`, `tradelines`). Refer to Data Catalog v1.3 Section 4.6 for details (schema pending sample report).
    * `applicationId` and `panNumber` MUST be stored in the bureau collection records for linkage.
    * Screen auto-advances on successful PAN verification.
    * **Skip Option (Dev/Test Only):** A subtle skip button allows bypassing PAN entry during development/testing. If skipped, PAN details in Firestore will be null, and `panVerified` status will be 'N' or equivalent.
4.  **User Data Entry:** User progresses through Personal Info, Employment Details screens. Progressive Reveal pattern guides the user. Data saved progressively to the `applications` document in Firestore (`PersonalInfoViewModel.kt`, `EmploymentDetailsViewModel.kt`).
5.  **Document Upload:** User uploads required documents based on selections/profile.
    * Documents uploaded to Firebase Storage, metadata stored in `documents` collection in Firestore (`UploadDocumentUseCase.kt`).
    * OCR/Extraction attempted (`ParseDocumentUseCase.kt`, `MLKitOCRService.kt`), status and key extracted data updated in `documents` record (using `extractedData` field as per Data Catalog v1.3 Section 4.5).
    * `extractionStatus` (SUCCESS/PARTIAL_SUCCESS/FAILURE) stored for each document. Success criteria: all required fields were extracted.
6.  **BRE Execution (Single Pass):** App compiles required data (personal, employment, bureau summary, document insights) and triggers the Camunda workflow (`CamundaService.kt`, `CalculateLoanOfferWithBREUseCase.kt`).
    * Input data snapshotted in `applications.breInputData`.
7.  **Camunda Workflow:** Camunda orchestrates the single call to the Python BRE worker (e.g., `calculateLoanOffer` topic).
8.  **Python BRE:** Worker executes rules based on input (See Section 9.5 for detailed rules). Returns decision (Approve/Reject/Refer), offer parameters, and `profileFlag`.
9.  **Loan Offer Display:** App receives BRE result. Result snapshotted in `applications.breOutputData`. `LoanOfferScreen.kt` displays outcome dynamically based on `breOutputData.decisionStatus` and `breOutputData.profileFlag`:
    * **Approve (`decisionStatus` = AUTO_APPROVED):** Shows approved amount, rate, tenor, fee. User proceeds to Employment Verification. Offer details received from BRE stored in `applications.loanOffer`. User can select loan amount and tenor using sliders, up to the approved limits. Final selected values stored in `applications.loanOffer`.
    * **Reject (`decisionStatus` = REJECTED or (`decisionStatus` = REFER and `profileFlag` = false)):** Shows rejection message. User proceeds to Application Submitted (Rejected/Under Review).
    * **Refer (`decisionStatus` = REFER and `profileFlag` = true):** Shows "Referral" status / "Under Review" message. The app enables loan amount and tenor sliders for the user to select. Applied loan amount and tenor stored in `applications.loanOffer`. User proceeds to Employment Verification.
    * `profileFlag` will be stored in `applications.breOutputData`.
    * **Skip Option (Dev/Test Only):** A subtle skip button allows bypassing the offer stage during development/testing. If skipped, loan offer details will be null/0, `offerStatus` might be REJECTED/SKIPPED, and the outcome effectively treated as 'Refer' (neither approved nor rejected), proceeding to Employment Verification.
10. **Employment Verification:** User completes verification (Email OTP or ID Card Scan - requires fix for `VerificationMethod.kt` alignment with Data Catalog enum). Status updated in `employment_verifications` collection (Firestore).
11. **Key Fact Sheet:** User reviews final summary. Data pulled from `applications` document. Includes PAN/Mobile, Office Address/Work Email. AI suggestions box reformatted.
12. **Submit Application:** User submits. `applicationStatus` updated to SUBMITTED or UNDER_REVIEW. `submittedAt` timestamp recorded (`SubmitApplicationUseCase.kt`).
13. **Application Complete:** `ApplicationCompleteScreen.kt` displays final status dynamically: Approved (with Disbursal option info), Rejected, or Submitted for Review (with next steps info).

*(Removed: Steps related to Bureau Data Confirmation Screen, LLM Obligation Recalculation, Second BRE Pass, Final Offer Display post-2nd pass)*

### 3.2 Metadata Collection & Orchestration

* The app MUST automatically collect the following metadata points:
    * `deviceInfo`: Phone Make (`Build.MANUFACTURER`), Model (`Build.MODEL`), OS Version (`Build.VERSION.RELEASE`), App Version.
    * `appSession`: App start time, end time (on background/close), total duration, drop-off flag (set by Orchestrator or app logic), drop-off timestamp.
    * `screenVisits`: Array containing `{ screenName (from Screen.kt), startTime, endTime, duration }` for each screen viewed.
    * `sectionTimings`: Array containing `{ sectionName (logical group), startTime, endTime, duration }` tracked via Progressive Reveal logic.
    * `documentEvents`: Array containing details for each document attempt including `{ documentType, documentSourceType, timestamp, status (success/failure), failureReason, cameraMetadata, extractionStatus, fileSize, fileName }`.
    * `verificationEvents`: Array containing details for each verification attempt `{ verificationType, timestamp, status (success/failure), failureReason }`.
    * `errorEvents`: Array containing details for critical errors `{ timestamp, screenName, errorCode, errorMessage, stackTrace }`.
* This metadata MUST be stored durably, associated with the specific `applicationId`, within a dedicated subcollection named `metadata` under the `applications/{applicationId}` document in Firestore. Each metadata event/snapshot should include the `applicationId` field within its data structure.
* The app MUST send critical events and associated metadata in near real-time to a designated "Metadata Orchestrator" HTTP endpoint. Events include: APPLICATION_STARTED, SCREEN_VIEWED, SECTION_COMPLETED, DOC_EVENT, VERIFICATION_EVENT, APPLICATION_SUBMITTED, APP_BACKGROUNDED (> 30s), ERROR_OCCURRED, etc..
* The Metadata Orchestrator (distinct from Camunda, likely a Cloud Function/Lambda) is responsible for processing these events, detecting drop-offs (>30s inactivity while `applicationStatus` is IN_PROGRESS), triggering LLM drop-off analysis, and potentially updating the `applicationStatus` in Firestore to DROPPED_OFF.

### 3.3 LLM Integration & Agentic AI Enhancements (Drop-off Analysis Only)

* **Drop-off Analysis (Triggered by Metadata Orchestrator):**
    * When the orchestrator detects a drop-off (>30s idle), it SHALL call the designated LLM Service.
    * **Configurability:** The choice of LLM provider and model MUST be configurable. The initial supported options are Google Gemini 2.0 Flash and OpenAI 4O-mini. The implementation should allow switching between these providers ideally via a single configuration change in the Orchestrator's deployment settings or code.
    * The orchestrator MUST provide the LLM with: `applicationId`, relevant data from the `applications` document (e.g., `personalInfo`, `employmentDetails`, `currentStep`, `completedSteps`), and recent metadata events (e.g., last few screen visits, recent errors) fetched from the `metadata` subcollection.
    * The LLM's task is to analyze this data and return: a brief customer profile summary, the last known screen/step, a ranked score indicating follow-up priority, and a summary of potential issues faced (based on metadata errors or inferred journey friction).
    * The orchestrator SHALL forward this LLM summary via email to a configured address (proxy for Sales Leads) using the configured Email Service.
    * **Email Service Configuration:** The system will use Brevo (formerly Sendinblue) on the free tier plan for sending email alerts. Configuration details (API Key, sender address) must be managed securely. Email subject line will contain the `applicationId`.
* **AI Assistant UI:** The floating AI chat bubble (`AIAssistantBubble.kt`) position MUST be fixed on the following screens where it currently sits behind system navigation buttons: Document Upload, Loan Offer, Employment Verification, Application Submitted. Ensure it remains accessible but unobtrusive.

*(Removed: Obligation Recalculation LLM call previously triggered by the app)*

### 3.4 UI/UX Modifications & Bug Fixes (Screen-by-Screen)

*(This section retains most fixes from v1.2, adjusted for single-pass flow where needed)*

* **General:**
    * Replace standard back arrow icon (`<-`) with a modern, theme-consistent back navigation icon (e.g., Chevron Left) on Document Upload, Loan Offer, Employment Verification screens. Use `BackNavigation.kt` component or similar.
    * Fix AI chat bubble (`AIAssistantBubble.kt`) positioning on Document Upload, Loan Offer, Employment Verification, Application Submitted screens to avoid overlap with system navigation bars.
* **OTP Screen (`LoginScreen.kt` / associated logic):**
    * Implement the Privacy Policy display: A clickable URL/text link for "Privacy Policy". Clicking it MUST display the full policy text within a modal dialog or popup box within the app.
    * Ensure that upon successful OTP verification (`VerifyOTPUseCase.kt`), the `users` collection in Firestore is updated to record `isPrivacyPolicyAccepted = true`, `privacyPolicyVersion` (fetch from config), and `privacyPolicyAcceptedAt = serverTimestamp()`.
* **Home Screen (`HomeScreen.kt`):**
    * Repair "Resume Application" functionality to correctly load the user's last state using `ResumeApplicationUseCase.kt` and navigate to the appropriate `currentStep` stored in Firestore.
    * Implement button actions correctly: "Chat Support" should open/focus the AI Assistant bubble/interface. "Call" button should launch the device's Dialer intent with a pre-filled support phone number. "Email" button should launch a Gmail compose intent with a pre-filled support email address.
    * Adjust layout constraints or spacing to reduce excessive white space above the "Welcome User" text.
* **PAN Screen (`PANEntryScreen.kt`):**
    * Increase the width of the selection boxes for "Take Photo" (Camera) and "Upload from Gallery" to prevent text wrapping.
    * Increase the width of the card/box displaying the processed PAN details.
    * Implement auto-progression: After PAN is successfully verified, display a success indicator/message for ~1.5 seconds, then automatically navigate to the next screen (Personal Info). Remove the explicit "Continue" button.
    * Add a small, circular button with a down-arrow icon (subtly styled, bottom-left) to allow users to skip PAN verification for development/testing purposes only. This should be clearly marked or enabled via a developer flag.
* **Personal Info Screen (`PersonalInfoScreen.kt`):**
    * Fix bug: Ensure `currentStep` in Firestore `applications` document is correctly updated upon successful completion/progression. Update `PersonalInfoViewModel.kt` logic.
* **Employment Info Screen (`EmploymentDetailsScreen.kt`):**
    * Conditional Logic: Make the "Work Email Id" TextField optional (not mandatory) if the user selects "Government" as `employmentType`.
    * Field Removal: Remove the "Employee ID" TextField completely.
    * Field Removal: Remove the "Designation" TextField completely for users who select "Private Sector" as `employmentType`. Keep it for "Government" if required.
    * State Preservation Bug: Fix data loss issue when navigating back and forth. Ensure `EmploymentDetailsViewModel.kt` retains and re-populates state.
    * Code Removal: Remove all code related to LinkedIn integration, including `LinkedInService.kt` and any calls/dependencies.
* **Document Upload Screen (`DocumentUploadScreen.kt`):**
    * (Apply General fixes: Modern back arrow, AI bubble position).
* **Loan Offer Screen (`LoanOfferScreen.kt`):**
    * (Apply General fixes: Modern back arrow, AI bubble position).
    * Content Removal: Remove the entire section related to "Upload Additional Documents".
    * Dynamic Content (BRE Decision): Implement logic in `LoanOfferViewModel.kt` and Composable to dynamically change display based on `breOutputData.decisionStatus` and `profileFlag`:
        * **Approve (`decisionStatus` = AUTO_APPROVED):** Show celebratory message, display final offer (Amount, Rate, Tenor, EMI, Fee), provide clear "Proceed" button to Employment Verification. Allow slider adjustment up to approved limits.
        * **Reject (`decisionStatus` = REJECTED or (`decisionStatus` = REFER and `profileFlag` = false)):** Show sympathetic rejection message, potentially offer reasons if provided by BRE. Provide button to exit/go home.
        * **Refer (`decisionStatus` = REFER and `profileFlag` = true):** Show message indicating referral/under review. Display calculated Rate & Fee. Enable amount and tenor sliders allowing user adjustment. Provide "Proceed" button navigating to Employment Verification.
        * **BRE API Failure:** Treat as "Refer" with `profileFlag` = false for UI purposes (show rejection/under review message).
    * **Skip Button (Dev/Test Only):** Add a subtle skip arrow button (bottom-left) visible only during development/testing builds. Clicking it sets loan offer details to null/0, treats the outcome as 'Refer', and navigates to Employment Verification.
    * Value Display: Ensure calculated Interest Rate (annual %), EMI Amount, and Processing Fee (% and calculated Amount) are clearly displayed for "Approve" and "Refer" scenarios.
* **Employment Verification Screen (`EmploymentVerificationScreen.kt`):**
    * (Apply General fixes: Modern back arrow, AI bubble position).
    * Conditional UI: Adapt screen content based on `employmentType`:
        * If Private Sector: Display "Verify via Work Email" prominently. Pre-populate work email (from Employment Details) and make it non-editable. "Send OTP" functions normally. "Verify via ID Card" may be secondary/hidden.
        * If Government Sector: "Verify via Work Email" might be hidden/disabled. "Verify via ID Card" should be primary.
    * Enum Fix: Ensure `VerificationMethod.kt` code is aligned with the Data Catalog v1.3 enum (EMAIL_OTP, ID_CARD_SCAN, MANUAL) and is correctly used in ViewModel/Screen logic.
* **Key Fact Sheet Screen (`KeyFactSheetScreen.kt`):**
    * Data Display: Modify `KeyFactSheetViewModel.kt` to include: Office Address and Work Email ID (if provided) in Employment Details card; PAN Card Number and registered Mobile Number in Personal Info card.
    * AI Suggestions Box: Redesign UI component for better contrast and formatting (e.g., bullet points).
* **Application Submitted Screen (`ApplicationCompleteScreen.kt`):**
    * (Apply General fixes: AI bubble position).
    * Animation: Remove the "Screen jumping animation". Use static or subtle confirmation.
    * Dynamic Content (Final Status): Implement logic in `ApplicationCompleteViewModel.kt` and Composable to display different content based on final `applicationStatus`: Approved (show success, disbursal info), Rejected (show rejection message), Refer/Under Review (show under review message, timeline, next steps). Include a "Back to Home" button.

### 3.5 Backend & Integration Changes (Revised - Single Pass BRE)

* **Code Removal:** Completely remove `LinkedInService.kt`, its implementation, DTOs, Use Cases, ViewModel logic, and DI bindings.
* **Camunda Integration (`CamundaService.kt`):** Ensure implementation can reliably:
    * Trigger the Camunda workflow instance with `breInputData` for the single BRE pass.
    * Handle responses/callbacks from Camunda regarding workflow status or BRE results.
    * *(Remove logic related to correlating messages for a second BRE pass)*
* **Appwrite Integration (`AppwriteService.kt`):** Modify implementation (`AppwriteServiceImpl.kt`) to fetch data from the 3 revised collections (`borrower_summary`, `enquiries`, `tradelines`) using the PAN number and return data structured for BRE input preparation. Handle potential errors.
* **BRE Input Preparation:** Update logic (likely within `CalculateLoanOfferWithBREUseCase.kt` or ViewModel) to correctly gather all required fields for `breInputData` according to Data Catalog v1.3 Section 4.8.
* **BRE Output Handling:** Update logic to parse `breOutputData` (including `profileFlag`) to drive the UI states described in section 3.4.
* **Metadata Orchestrator API:** Define and implement Retrofit calls (`OrchestratorApi.kt` - NEW) to send metadata events to the orchestrator endpoint. Ensure secure and reliable transmission.
* **LLM API Integration (Orchestrator):** The Metadata Orchestrator backend will implement calls to the LLM API for drop-off analysis. The Android app will not directly call LLM services anymore. Remove `LlmApi.kt`, `LlmService.kt` from the Android app codebase.
* **FileProvider Fix:** Resolve the `SecurityException` related to `com.loansai.unassisted.provider` authority. Ensure `AndroidManifest.xml` authority matches `file_provider.xml` (`res/xml/`) and provider configuration is correct.

## 4. System Interactions & Data Flow (Revised v1.3)

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
    end
    subgraph Core Backend
        Camunda(Camunda Engine)
        PythonBRE(Python BRE Worker)
    end
    subgraph Supporting Services
        Appwrite(Appwrite DB Service)
        LLM(LLM Service - Gemini/OpenAI)
        MetaOrch(Metadata Orchestrator)
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
    App -- Send Events/Metadata --> MetaOrch

    MetaOrch -- Request Drop-off Analysis --> LLM
    MetaOrch -- Send Summary --> Email
    MetaOrch -- Read App Status/Metadata --> Firestore
    MetaOrch -- Write App Status (Dropped Off) --> Firestore

    Camunda -- Request Bureau Fetch (Delegate) --> Appwrite
    Camunda -- Assign Task (Single Pass) --> PythonBRE
    PythonBRE -- Complete Task --> Camunda
    PythonBRE -- Read Rules --> RulesConfig[Rule Files/DB]

    Appwrite -- Serves Bureau Data --> Camunda

    LLM -- Provides Drop-off Analysis --> MetaOrch

    Firestore -- Serves Data/Metadata --> App
    Firestore -- Serves Data/Metadata --> MetaOrch
    Auth -- Returns Auth State --> App
    Storage -- Serves Docs --> App
    PANVerify -- Returns Verification --> App
    Email -- Sends Alert --> User/Support

    %% Removed: App -> LLM (Obligation Recalc) call
    %% Removed: App -> Appwrite direct call (now via Camunda)
    ```


Diagram Notes: The key change is the removal of the direct App-to-LLM call for obligation recalculation and the App-to-Appwrite call (now orchestrated via Camunda). The flow emphasizes a single BRE pass orchestrated by Camunda. Metadata flows to Firestore subcollections and the Metadata Orchestrator.


### 4.2 Detailed Interaction Points & Data Exchange (Revised)

    App <> Firebase Auth: App sends phone number, receives request for OTP, sends OTP, receives JWT/session token on success.
    App -> Firestore:
        Writes/Updates users document (profile, privacy consent).
        Creates/Updates applications document (personalInfo, employmentDetails, status, steps, BRE I/O, loanOffer, timestamps).
        Creates/Updates documents metadata documents in the documents collection.
        Creates employment_verifications records in the employment_verifications collection.
        Writes event documents to the applications/{appId}/metadata subcollection.
        Reads application state, user profile, configurations.
    App <> Firebase Storage: App uploads selected document files, receives Storage URL. App downloads documents for display/review if needed.
    App -> PAN Verification API: App sends PAN number, receives validation status (Valid/Invalid/Error) and potentially holder's name.
    App -> Camunda Service:
        Initiate Workflow & Bureau Fetch: Sends initial application data (subset needed for BRE) and triggers the Camunda workflow instance. This trigger implicitly signals Camunda to orchestrate the bureau data fetch from Appwrite. Receives instance ID.
        (Removed: Update message for 2nd BRE pass)
        Query Status (Optional): App might query Camunda for workflow status updates.
    App -> Metadata Orchestrator: App sends JSON payloads containing eventType, applicationId, timestamp, and specific metadata relevant to the event (e.g., screen name, duration, error details, device info) via HTTP POST requests to the orchestrator's endpoint.
    (Removed: App -> LLM Service call for Obligation Recalc)
    Metadata Orchestrator -> Firestore: Reads applications status/timestamps and metadata subcollection events to check for drop-offs. Updates applications.applicationStatus to DROPPED_OFF if detected.
    Metadata Orchestrator -> LLM Service (Drop-off): Sends JSON payload with application snapshot and metadata. Receives JSON summary (profile, last step, rank, issues). Uses configured provider (Gemini 2.0 Flash / OpenAI 4O-mini).
    Metadata Orchestrator -> Email Service (Brevo): Sends formatted email containing LLM drop-off summary using the Brevo API.
    Camunda <> Appwrite: Camunda Service Task (likely using HTTP connector) sends PAN to Appwrite endpoint, receives bureau data JSON (from borrower_summary, enquiries, tradelines).
    Camunda <> Python BRE: Camunda assigns External Task with process variables (including input data for the single BRE pass). BRE worker fetches task, executes rules, completes task with output variables (decision, offer, flag).
    Python BRE -> Rules Config: Reads rule definitions from local files (JSON, YAML, Python modules) or potentially a database/rules repository.

## 5. Detailed Design & Implementation Plan (Revised v1.3)
### 5.1 UI/UX Design Principles

(This section remains largely the same as v1.2, emphasizing Material 3, Compose, Progressive Reveal, Cleanliness, Responsiveness, Accessibility, and Performance)

    Material 3 Adherence: Strictly follow M3 guidelines.
    Jetpack Compose: Implement all UI using declarative Compose.
    Progressive Reveal: Implement section-by-section reveal robustly using state management and animations.
    Clean & Minimalist: Emphasize clarity, white space, card-based layouts.
    Responsiveness: Ensure layouts adapt using Compose modifiers.
    Accessibility: Implement content descriptions, touch targets, font scaling, contrast.
    Performance: Optimize Compose layouts, minimize recompositions.

### 5.2 Screen-Specific Implementation Details (Detailed - Revised)

(Expands on Section 3.4, incorporating single-pass flow adjustments)

    General: Create reusable Composables (back navigation, text fields, buttons, AI bubble wrapper).
    OTP Screen: Implement as described (OTP field, Privacy Policy link/modal, Firestore update on success).
    Home Screen: Implement as described (Resume flow fix, Button actions using Intents, Layout adjustments).
    PAN Screen: Implement as described (Layout fixes, Auto-progress, Dev-only skip button).
    Personal Info Screen: Implement as described (Ensure ViewModel updates currentStep correctly).
    Employment Info Screen: Implement as described (Conditional Work Email, Remove Employee ID, Remove Designation for Private Sector, Fix state preservation bug, Remove LinkedIn code).
    Document Upload Screen: Implement as described (Modern back arrow, AI bubble position fix, LazyColumn for DocumentUploadCard).
    Loan Offer Screen:
        Relies heavily on state from LoanOfferViewModel.kt reflecting the single breOutputData.
        Use when(state.decisionStatus) (and profileFlag for REFER) to show Approve/Reject/Refer content dynamically as per Section 3.1 logic.
        Conditionally enable sliders (LoanOfferSlider.kt) only if decisionStatus is REFER and profileFlag is true.
        Display formatted currency/percentages.
        Implement dev-only skip button logic (sets values to null/0, treats as Refer, proceeds to Employment Verification).
        (Remove logic related to displaying 2nd pass results)
    (Removed: Bureau Confirmation Screen implementation details)
    Employment Verification Screen: Implement as described (Conditional UI based on employmentType, Pre-populate/disable work email for Private, Align VerificationMethod.kt with Data Catalog enum, Modern back arrow, AI bubble position fix).
    Key Fact Sheet Screen: Implement as described (Include required fields: PAN, Mobile, Office Address, Work Email; Redesign AI suggestions box).
    Application Submitted Screen: Implement as described (Remove jumping animation, Dynamic content based on final applicationStatus, Buttons for Disbursal Info/Back to Home).

### 5.3 Metadata Implementation Strategy (Subcollection)

    Data Structures: Define Kotlin data classes mirroring the JSON structure in Data Catalog Section 4.11 (e.g., MetadataEvent, ScreenVisitData, DocumentEventData). Each class should include eventId, applicationId, eventTimestamp, eventType, and event-specific payload fields.
    Collection Logic:
        Use LifecycleEventObserver for app session start/end.
        Use NavController.OnDestinationChangedListener for screen visits.
        Integrate timing capture into Progressive Reveal logic for section timings.
        Capture document events in DocumentViewModel.kt / UploadDocumentUseCase.kt / OCR Service callbacks.
        Capture verification events in EmploymentVerificationViewModel.kt / VerifyEmploymentUseCase.kt.
        Capture error events in a global error handler or relevant ViewModels/Use Cases.
        Get deviceInfo using android.os.Build.
    Storage: Inject LoanRepository.kt (or a dedicated MetadataRepository.kt). Create method like logMetadataEvent(event: MetadataEvent). This method will:
        Generate a unique eventId (e.g., UUID).
        Set the applicationId and eventTimestamp.
        Use Firestore's collection("applications").document(appId).collection("metadata").document(eventId).set(event) to write the event to the subcollection.
        Consider batching writes for high-frequency events (like screen visits) if performance becomes an issue.
    Orchestrator Trigger: Identify key points (e.g., screen change, error caught, background event > 30s, document processed, verification attempt) where metadata needs to be sent in addition to being stored locally. Call the MetadataOrchestratorService.sendEvent(eventPayload) method. Ensure the payload sent matches the orchestrator's expected input for specific event types (e.g., APP_BACKGROUNDED).

### 5.4 Metadata Orchestrator Implementation Strategy

(This section remains largely the same as v1.2, focusing on the separate event-driven service for drop-off detection)

    Implementation: Recommended as a serverless function (Google Cloud Functions, AWS Lambda) or lightweight microservice. Triggered via HTTP POST endpoint.
    High-Level Process Flow:
        Trigger: HTTP POST Request received at /event.
        Input: JSON Payload: { "eventType": "string", "applicationId": "string", "timestamp": "timestamp", "metadata": { ... } }
        Parse Event.
        Gateway (Check Event Type):
            If APP_BACKGROUNDED: Schedule Drop-off Check task/timer.
            If APP_FOREGROUNDED / APPLICATION_SUBMITTED / APPLICATION_CANCELLED: Cancel pending Drop-off Check task for that applicationId.
            Other Events: Log/Ignore for drop-off purposes.
        (Separate Flow Triggered by Timer/Scheduled Task - Drop-off Check):
            Fetch Application Status & Metadata from Firestore (applications doc and metadata subcollection).
            Gateway (Check if Dropped Off based on status and timestamps).
            If Dropped Off:
                Prepare LLM Input (fetch relevant data/metadata).
                Call LLM Analysis API (using configured provider: Gemini 2.0 Flash / OpenAI 4O-mini).
                Process LLM Response.
                Send Email Alert (using Brevo API).
                Update App Status in Firestore to DROPPED_OFF (Optional).
            If Not Dropped Off: End.
    Configuration Parameters: HTTP_ENDPOINT_URL, DROP_OFF_TIMEOUT_SECONDS, LLM_PROVIDER (Gemini/OpenAI), LLM_MODEL_NAME (gemini-2.0-flash / gpt-4o-mini), LLM_API_ENDPOINT, LLM_API_KEY_SECRET_NAME, ALERT_EMAIL_RECIPIENT, EMAIL_SERVICE_PROVIDER (Brevo), EMAIL_SERVICE_API_ENDPOINT, EMAIL_SERVICE_API_KEY_SECRET_NAME, FIRESTORE_PROJECT_ID, SERVICE_ACCOUNT_CREDENTIALS_SECRET_NAME, LOG_LEVEL.

### 5.5 LLM Integration Implementation Strategy (Drop-off Analysis)

    API Definition (Orchestrator Backend): The Metadata Orchestrator backend service will define internal functions/clients to call the chosen LLM API (Gemini or OpenAI) for the analyzeDropOff task. Request/response handling will be internal to the orchestrator.
    DI (Orchestrator Backend): The orchestrator will manage dependencies for its HTTP client and LLM API interaction based on configuration (LLM_PROVIDER, LLM_MODEL_NAME, API keys).
    Drop-off Analysis Call (Orchestrator): Orchestrator code calls the LLM API using appropriate HTTP client/SDK.
    App Integration: The Android app does not directly integrate with LLM APIs in v1.3. Remove LlmApi.kt, LlmService.kt, and related DI modules (OpenAIModule.kt, GeminiModule.kt might need renaming/refactoring if they only served this purpose) from the Android project.

(Removed: Obligation Recalculation implementation details from the app side)


### 5.6 Backend Integration Strategy (Camunda, Python BRE, Appwrite - Revised Single Pass)

    Camunda Workflow (BPMN):
        Model the single-pass flow: Start -> Service Task (Call Appwrite via Connector) -> Service Task (Publish External Task for BRE) -> Exclusive Gateway (Evaluate BRE Decision) -> End Events (Approved Path, Rejected Path, Referred Path).
        Configure the Appwrite Service Task (e.g., HTTP connector) to fetch data from the 3 collections (borrower_summary, enquiries, tradelines).
        Configure the BRE Service Task to publish an External Task for the Python BRE worker (e.g., calculateLoanOffer topic). Pass necessary variables from application data and Appwrite results.
    Python BRE Worker:
        Use Camunda External Task Client library (pyzeebe or similar).
        Subscribe to the single topic (e.g., calculateLoanOffer).
        Implement one handler function for this topic. Parse incoming variables. Load rule set. Execute rules (as defined in Section 9.5). Return output variables (decision, offer, flag).
        (Remove handler/logic for a second pass)
    Appwrite Service (AppwriteService.kt / AppwriteServiceImpl.kt): Ensure implementation correctly queries all three collections (borrower_summary, enquiries, tradelines) using the PAN and returns combined/structured data suitable for Camunda/BRE consumption. Handle potential errors. Note: Schema details for enquiries depend on the sample CIBIL report.
    App <> Camunda Interface (CamundaService.kt): Implement method startLoanProcessAndFetchBureau(initialData) -> returns instanceId. Handle API calls securely. (Remove methods related to submitting updates for a second pass).

### 5.7 Implementation Phases (Expanded & Detailed - Revised)

(Adjusted to reflect single-pass BRE and removal of Bureau Confirmation)

    Phase 1: Foundation & Data (Weeks 1-2)
        Task 1.1: Finalize v1.3 Data Catalog & Enums.
        Task 1.2: Implement Firestore schema changes (updated applications fields, metadata subcollection structure, indexes).
        Task 1.3: Set up Appwrite collections (borrower_summary, enquiries, tradelines). Define initial schema based on available info, mark enquiries as pending sample report.
        Task 1.4: Refine Architecture diagrams (System Interactions v1.3).
        Task 1.5: Set up project structure for Metadata Orchestrator service.
    Phase 2: Core Backend - Single Pass BRE (Weeks 3-5)
        Task 2.1: Develop Camunda BPMN for single-pass flow (Start -> Get Bureau -> BRE Task -> Gateway -> End). Deploy v1.
        Task 2.2: Implement Camunda <> Appwrite service call logic (via connector).
        Task 2.3: Develop Python BRE worker logic & rules for single pass (as per Section 9.5).
        Task 2.4: Integrate App -> Camunda trigger (start workflow).
        Task 2.5: Integrate App to receive BRE results from Camunda/Firestore.
    Phase 3: Metadata & Orchestration (Weeks 4-6)
        Task 3.1: Implement metadata collection points in Android App (storing to subcollection).
        Task 3.2: Develop & Deploy Metadata Orchestrator service (e.g., Cloud Function).
        Task 3.3: Implement App -> Orchestrator API communication.
        Task 3.4: Implement Orchestrator drop-off detection logic (>30s).
        Task 3.5: Integrate Orchestrator -> LLM call (Drop-off Analysis - configurable provider/model).
        Task 3.6: Integrate Orchestrator -> Email alert (using Brevo).
    (Removed: Phase related to Bureau Confirmation Screen & LLM Recalc)
    Phase 4: App UI & Bug Fixes (Weeks 6-8) (Renumbered)
        Task 4.1: Implement all specific UI fixes & enhancements from Sec 3.4 (Icons, Layouts, Conditional UI, Text changes, Removals - excluding Bureau Confirmation Screen).
        Task 4.2: Refine Loan Offer screen logic for single-pass display (Approve/Reject/Refer states).
        Task 4.3: Refine Application Complete screen logic for final states.
        Task 4.4: Address FileProvider issue and any remaining critical bugs.
        Task 4.5: Remove Bureau Confirmation screen code and navigation.
        Task 4.6: Remove App-side LLM integration code (LlmApi.kt, LlmService.kt).
        Task 4.7: Remove LinkedIn integration code.
    Phase 5: Testing (Weeks 8-10) (Renumbered)
        Task 5.1: Unit testing (ViewModels, Use Cases, Repositories, Utils, BRE rules).
        Task 5.2: Integration testing (App<->Firebase, App<->Camunda, App<->Orchestrator, Camunda<->BRE, Camunda<->Appwrite, Orchestrator<->LLM).
        Task 5.3: End-to-End testing of all major flows (Approve, Reject, Refer). (Remove E2E tests involving Bureau Confirmation screen).
        Task 5.4: UI Testing (Espresso/Compose UI Tests).
        Task 5.5: Security Audit.
    Phase 6: Deployment & Monitoring (Week 11) (Renumbered)
        Task 6.1: Prepare release builds (Android App).
        Task 6.2: Deploy updated backend components (Camunda BPMN v1, Python BRE v1, Metadata Orchestrator v1).
        Task 6.3: Configure production monitoring & alerting.
        Task 6.4: Go-live. Post-launch monitoring.

## 6. Data Model Specification (Target State - v1.3 Detailed)

This section details the target schemas for Firestore and Appwrite databases, reflecting the single-pass BRE flow and removal of the Bureau Confirmation feature.

### 6.1 Firestore Database Structure (Revised - Single Pass BRE)

    users Collection: (No changes from v1.2 definition - See Data Catalog v1.3 Section 4.1)
    applications Collection: (Document ID: Unique Application ID)
        Schema:
    JSON

    {
      // --- Core Identifiers & Status ---
      "id": "string",                     // Unique Application ID (matches doc ID)
      "userId": "string",                 // Firebase Auth User ID (links to 'users')
      "applicationStatus": "string",      // Overall status (Enum: ApplicationStatus)
      "currentStep": "string",            // Last completed/current UI step (Enum: ApplicationStep)
      "completedSteps": ["string"],       // Array of completed UI steps (Enum: ApplicationStep)

      // --- User Input Data ---
      "personalInfo": { /* ... as defined in Data Catalog v1.3 Section 4.2 ... */ },
      "employmentDetails": { /* ... as defined in Data Catalog v1.3 Section 4.3 ... */ }, // Includes monthlyEmi and declaredEmi2

      // --- BRE & Offer Progression (Single Pass) ---
      "breInputData": "map",              // Snapshot of data sent to BRE (Enum: BREInputData)
      "breOutputData": "map",             // Result from BRE (Enum: BREOutputData)
      "loanOffer": "map",                 // Final offer/decision details presented (Enum: LoanOfferData)

      // --- Timestamps ---
      "createdAt": "timestamp",           // Firestore timestamp when application was created
      "lastUpdatedAt": "timestamp",       // Firestore timestamp of last modification
      "submittedAt": "timestamp",         // Firestore timestamp when user finally submitted
      "finalDecisionAt": "timestamp"      // Firestore timestamp when status changed to Approved/Rejected
    }

        (Removed fields: bureauConfirmationInput, breInputData_1stPass, breOutputData_1stPass, loanOffer_1stPass, breInputData_2ndPass, breOutputData_2ndPass, loanOffer_final)
        Indexes: userId, createdAt DESC; applicationStatus, lastUpdatedAt ASC; userId, applicationStatus.
    documents Collection: (No schema changes from v1.2 definition - See Data Catalog v1.3 Section 4.5 for extractedData details)
    employment_verifications Collection: (No changes from v1.2 definition - See Data Catalog v1.3 Section 4.4)
    otps Collection: (No changes from v1.2 definition - See Data Catalog v1.3 Section 4.4)
    employers Collection: (No changes from v1.2 definition)

### 6.2 Appwrite Bureau Database Structure (Revised - 3 Collections)

    Purpose: Stores processed credit bureau data fetched externally. Accessed via Appwrite API (orchestrated by Camunda).
    borrower_summary Collection: (Document ID: PAN Number)
        Schema: (Detailed fields as listed in Data Catalog v1.3 Section 4.6 - requires sample report)
    enquiries Collection: (Document ID: Unique Enquiry ID) (NEW)
        Schema: (Detailed fields as listed in Data Catalog v1.3 Section 4.6 - requires sample report)
    tradelines Collection: (Document ID: Unique Tradeline ID)
        Schema: (Detailed fields as listed in Data Catalog v1.3 Section 4.6 - requires sample report)
    Indexes (Appwrite): Define indexes on panNumber in enquiries and tradelines collections.

### 6.3 Metadata Structure (Detailed - Subcollection)

    Purpose: To capture detailed analytics and operational data about the user's journey.
    Storage: Stored within Firestore in a dedicated subcollection applications/{appId}/metadata/{eventId}.
    Schema (within each metadata/{eventId} document): (See Data Catalog v1.3 Section 4.11 for detailed fields like eventId, applicationId, eventTimestamp, eventType, and specific event payloads like deviceInfo, appSession, screenVisit, sectionTiming, documentEvent, verificationEvent, errorEvent)

### 6.4 Data Catalog Reference (Revised - Separate Document v1.3)

    All data elements, structures, and enums are formally defined in the Loan Application Data Catalog v1.3 document. This specification refers to that document as the source of truth for data definitions.

## 7. Technical Architecture (Target State - v1.3 Detailed)

The target architecture is designed as a distributed system comprising the Android mobile client and several backend services, communicating primarily via REST APIs over HTTPS.

### 7.1 Android Mobile Client

    Platform: Native Android application developed in Kotlin.
    UI: Built entirely with Jetpack Compose, adhering to Material 3 design guidelines. Implements custom components (EnhancedLoanTextField.kt, LoanOfferSlider.kt, AIAssistantBubble.kt), navigation (AppNavGraph.kt), and theme (Theme.kt). Utilizes Progressive Reveal UI pattern (ContinuousFormContainer.kt, AnimatedSections.kt).
    Architecture: Follows MVVM (Model-View-ViewModel) principles with clear separation into layers (UI, ViewModel, Domain/UseCase, Data/Repository, Service). Leverages Hilt for dependency injection (AppModule.kt, RepositoryModule.kt, ServiceModule.kt, etc.). Use Cases (GetAIAssistanceUseCase.kt, CalculateLoanOfferWithBREUseCase.kt, etc.) encapsulate business logic. Repositories (LoanRepository.kt, DocumentRepository.kt, etc.) abstract data sources.
    Data Handling: Manages UI state using Compose State and ViewModels (collectAsStateWithLifecycle). Persists session tokens and settings using DataStore Preferences (PreferencesDataSource.kt). Interacts with backend services via Retrofit interfaces (AuthApi.kt, PANApi.kt, CamundaApi.kt, AIApi.kt, OrchestratorApi.kt (NEW)). Handles file operations (FileUtils.kt) and uploads/downloads via Firebase Storage integration in DocumentRepositoryImpl.kt. Utilizes ML Kit for on-device OCR (MLKitOCRService.kt). Manages background tasks potentially using WorkManager (DocumentUploadWorker.kt). Note: Removed direct LLM API integration (LlmApi.kt, LlmService.kt).
    Connectivity: Communicates with all backend APIs over HTTPS. Includes interceptors (AuthInterceptor.kt) for adding authentication tokens.

### 7.2 Firebase Suite

    Firebase Authentication: Provides secure phone number OTP-based authentication and user session management. Issues JWT tokens used by the app.
    Firestore Database: Acts as the primary database for application state, user data, document metadata, BRE I/O snapshots, and collected application metadata (stored in applications/{appId}/metadata subcollection). Organized into collections (users, applications, documents, etc.) as defined in Data Catalog v1.3. Uses Firestore Security Rules.
    Firebase Cloud Storage: Securely stores all user-uploaded documents (images, PDFs). Access controlled via Storage Security Rules. App interacts via Firebase Storage SDK.

### 7.3 Core Loan Process Backend

    Camunda Platform 8: Acts as the process orchestrator for the loan application workflow. Receives workflow initiation requests from the Android App via its API (CamundaApi.kt, CamundaService.kt). Executes the defined single-pass BPMN process model (See Section 5.6). Orchestrates calls to dependent services (Appwrite, Python BRE) using appropriate connectors (e.g., REST connector for Appwrite, External Task pattern for BRE). Manages process state. Provides operational visibility via Camunda Operate.
    Python BRE Worker: A dedicated backend service (likely containerized) responsible for executing the business rules for loan eligibility and offer calculation in a single pass. Uses the Camunda 8 External Task Client library to subscribe to a specific topic (e.g., calculateLoanOffer) published by Camunda. Receives task assignments containing input data. Loads and executes business rules (as per Section 9.5). Performs calculations. Completes the external task in Camunda, sending back results (decision, offer, flag).

### 7.4 Supporting Services

    Appwrite DB Service (or equivalent): A backend service exposing an API for querying credit bureau data. Stores bureau data fetched periodically/on-demand. Organizes data into 3 collections: borrower_summary, enquiries, tradelines (See Data Catalog v1.3 Section 4.6). Provides a secure API endpoint for fetching bureau data based on PAN, called by Camunda.
    LLM Service (Google Gemini / OpenAI): Provides access to Large Language Models via API endpoints. Used only for Drop-off Analysis, called by the Metadata Orchestrator with application data/metadata. Configurable between Google Gemini 2.0 Flash and OpenAI 4O-mini.
    Metadata Orchestrator: A lightweight backend service (e.g., Cloud Function, Lambda) responsible for handling real-time events from the Android app for monitoring and intervention. Receives metadata events (via OrchestratorApi.kt). Detects user drop-offs based on timing and application status queried from Firestore. Triggers the LLM Service for drop-off analysis. Sends email summaries via an Email Service (Brevo). Potentially updates application status in Firestore (e.g., to DROPPED_OFF). Distinct from Camunda.
    Email Service (Brevo): Uses Brevo (free tier) API to send email alerts (e.g., LLM drop-off summaries) generated by the Metadata Orchestrator. Securely configured API keys required.
    PAN Verification API: An external third-party API used by the Android App (PANApi.kt) to verify the validity of a PAN number.

### 7.5 Communication & Security

    All communication MUST use HTTPS/TLS encryption.
    Authentication: App uses Firebase Auth. App authenticates API calls using Firebase Auth JWT tokens. Backend services authenticate with each other using secure mechanisms (API Keys, OAuth2, service accounts).
    Authorization: Firestore/Storage access controlled via Security Rules. API endpoints should implement authorization checks. Camunda authorization manages process access.
    Secrets Management: API keys, service account credentials, and other secrets MUST be stored securely (e.g., Google Secret Manager, AWS Secrets Manager, HashiCorp Vault) and NOT hardcoded.

## 8. Orchestrator (Metadata/Drop-off) Design & Config Details

(This section remains largely the same as v1.2, focusing on the separate event-driven service for drop-off detection, with specific updates for LLM and Email config)

### 8.1 Purpose & Distinction

This component is not the main Camunda loan process engine. It's a separate, event-driven service monitoring user journey progression via metadata, detecting drop-offs, and triggering LLM analysis and alerts.

### 8.2 Implementation

Recommended as a serverless function (e.g., Google Cloud Functions, AWS Lambda) or a lightweight microservice. Triggered via an HTTP POST endpoint.
### 8.3 High-Level Process Flow (BPMN-like Description)

(Flow remains the same as v1.2: Trigger via HTTP -> Parse Event -> Gateway on Event Type -> Schedule/Cancel Drop-off Check. Separate flow for Timer/Scheduled Task: Fetch Status/Metadata -> Check if Dropped -> If Yes: Prepare LLM Input -> Call LLM -> Process Response -> Send Email -> Update Status)
### 8.4 Configuration Parameters

    HTTP_ENDPOINT_URL: URL where the orchestrator listens (e.g., https://orchestrator.example.com/event).
    DROP_OFF_TIMEOUT_SECONDS: Timeout duration (e.g., 30 or 60).
    LLM_PROVIDER: Configured provider (e.g., "GOOGLE", "OPENAI").
    LLM_MODEL_NAME: Specific model identifier (e.g., "gemini-2.0-flash", "gpt-4o-mini").
    LLM_ANALYSIS_API_ENDPOINT: Base URL for the LLM service API.
    LLM_API_KEY_SECRET_NAME: Name of the secret holding the LLM API key.
    ALERT_EMAIL_RECIPIENT: Email address for drop-off summaries.
    EMAIL_SERVICE_PROVIDER: Configured provider (e.g., "BREVO").
    EMAIL_SERVICE_API_ENDPOINT: Endpoint for the Brevo (or other) email sending API.
    EMAIL_SERVICE_API_KEY_SECRET_NAME: Name of the secret holding the email service API key.
    FIRESTORE_PROJECT_ID: Google Cloud Project ID.
    SERVICE_ACCOUNT_CREDENTIALS_SECRET_NAME: Name of the secret holding service account credentials.
    LOG_LEVEL: Logging verbosity (INFO, DEBUG, ERROR).

## 9. BRE (Python Worker) Design & Config Details (Revised - Single Pass)

### 9.1 Purpose

To execute business logic for determining loan eligibility and calculating offer terms based on application data, bureau data, and document insights in a single pass. Operates decoupled via Camunda's External Task pattern.
### 9.2 Technology Stack

    Language: Python 3.x.
    Camunda Client: pyzeebe (for Camunda 8) or equivalent.
    Rules Engine (Optional): durable_rules, business-rules, or direct Python implementation.
    Data Handling: Standard Python libraries (json).
    Deployment: Containerized (Docker), deployed on Kubernetes, Cloud Run, etc.

### 9.3 Interaction Model

    Worker polls Zeebe gateway for tasks assigned to its topic (e.g., calculateLoanOffer).
    Client library invokes handler function upon receiving a task.
    Handler receives task details, including process variables (breInputData).
    Handler executes business rules/logic (See Section 9.5).
    Handler completes the task, sending back results (breOutputData).
    Includes error handling (BPMN errors, Incidents).

### 9.4 Input/Output Handling

    Input: Receives process variables from Camunda (breInputData map). Key fields defined in Data Catalog v1.3 Section 4.8 (e.g., applicationId, personalInfo, employmentDetails, effectiveMonthlyEmi, bureauSummary, documentInsights).
    Output: Returns results as a dictionary/JSON (breOutputData map). Key fields defined in Data Catalog v1.3 Section 4.9 (e.g., decisionStatus, approvedAmount, interestRate, maxTenor, processingFeePercentage, rejectionReasons, referralReasons, profileFlag).

### 9.5 Rule Categories & Logic (Single Pass - Illustrative)

Handler for calculateLoanOffer topic:

    Input Preparation:
        Receive breInputData.
        Determine effectiveMonthlyEmi: Use declaredEmi2 if provided and valid, otherwise use monthlyEmi from employmentDetails.
        Derive intermediate variables: Age from DOB, Income Stability score from Bank Statement insights, Employer Risk category, etc.
    PAN Verification Check (profileFlag Determination Part 1):
        Check panVerified status (passed in breInputData or derived from document metadata).
        If PAN verification failed or status is 'N', set internal profileOK = false.
        Else, set profileOK = true.
    Hard Rule Checks:
        Minimum/maximum age.
        Presence of critical negative bureau indicators (e.g., existing defaults in bureauSummary).
        Employment type eligibility.
        Minimum income threshold.
        Basic checks on mandatory data presence.
        If any hard rule check fails, set decisionStatus = REJECTED, provide rejectionReasons, set profileFlag = profileOK, and STOP.
    Risk Assessment & Calculation:
        Calculate internal risk score based on bureau score, income stability, effectiveMonthlyEmi, debt-to-income ratio (using effectiveMonthlyEmi and derived income), potentially employer/industry risk.
        Calculate maximum affordable EMI:
            incomeAvailableForLoan = monthlySalary * 0.60 (configurable factor, default 60%).
            maxPayableEmi = incomeAvailableForLoan - effectiveMonthlyEmi
        If maxPayableEmi <= 0, set decisionStatus = REJECTED, provide rejectionReasons ("Insufficient Repayment Capacity"), set profileFlag = profileOK, and STOP.
        Calculate maximum loan offer amount:
            Use PMT formula inversion or financial library function: calculate_loan_amount(rate=0.12/12, nper=60, pmt=-maxPayableEmi) (Rate 12% annual, Tenor 60 months - easily configurable constants).
        Determine Interest Rate Tier, Max Tenor (up to 60 months), Processing Fee % based on risk score and calculated max loan amount.
    Final Decision Logic:
        If profileOK is false (from Step 2), set decisionStatus = REJECTED, provide rejectionReasons ("Profile Verification Failed"), set profileFlag = false, and STOP.
        If profileOK is true and maxPayableEmi > 0 (implicitly passed Step 4 check):
            Based on risk score and other policy rules (e.g., exposure limits):
                If score/profile meets auto-approval criteria: Set decisionStatus = AUTO_APPROVED, populate approvedAmount (up to calculated max), interestRate, maxTenor, processingFeePercentage. Set profileFlag = true.
                If score/profile requires review or falls into marginal category: Set decisionStatus = REFER, populate calculated interestRate, maxTenor, processingFeePercentage (offer parameters might still be indicative), provide referralReasons. Set profileFlag = true.
                (Edge Case: If policy dictates some referrals should lead to rejection, adjust logic here).
        (Catch-all): Any other unhandled condition should default to REFER with appropriate reasons and profileFlag = true.

### 9.6 Configuration Parameters

    ZEEBE_GATEWAY_ADDRESS: Host and port of the Camunda 8 Zeebe gateway.
    ZEEBE_CLIENT_ID / ZEEBE_CLIENT_SECRET: Credentials for connecting to Zeebe (if secured).
    TASK_TOPIC: Topic name for the BRE task (e.g., calculateLoanOffer).
    WORKER_NAME: Identifier for this worker instance.
    MAX_TASKS_TO_ACTIVATE: Number of tasks the worker polls for at once.
    RULE_CONFIG_PATH: Path to rule files or connection string for rule database.
    RULE_VERSION: Version identifier for the currently loaded rules.
    INCOME_FACTOR_FOR_LOAN: Percentage of declared income considered available (e.g., 0.60).
    DEFAULT_LOAN_IRR: Default annual interest rate for max loan calculation (e.g., 0.12).
    DEFAULT_LOAN_TENOR: Default loan tenor in months for max loan calculation (e.g., 60).
    LOG_LEVEL: Logging verbosity (INFO, DEBUG, etc.).

## 10. Testing & Deployment Strategy (Detailed - Revised)
### 10.1 Testing Strategy

(Revised to reflect single-pass BRE and removal of Bureau Confirmation)

    10.1.1 Unit Testing:
        Android App: Test ViewModels, Use Cases, Repositories, Utils, Validators, Compose UI components.
        Python BRE Worker: Test individual rule functions/classes, data parsing, calculation logic (max EMI, max Loan), decision branches for Approve/Reject/Refer based on single-pass inputs.
        Metadata Orchestrator: Test event parsing, drop-off logic, LLM input prep, email formatting.
    10.1.2 Integration Testing:
        App <> Firebase: Auth, Firestore R/W (incl. metadata subcollection), Storage U/D.
        App <> Camunda: Test starting workflow.
        (Removed: App <> LLM test)
        App <> Metadata Orchestrator: Test sending events.
        (Removed: App <> Appwrite test)
        Camunda <> Python BRE: Test external task assignment (single topic), variable passing, completion/failure.
        Camunda <> Appwrite: Test service task execution for fetching bureau data (3 collections).
        Metadata Orchestrator <> Firestore: Test reading status/metadata, writing status.
        Metadata Orchestrator <> LLM (Drop-off): Test API call, response parsing.
        Metadata Orchestrator <> Email Service (Brevo): Test triggering email.
    10.1.3 End-to-End (E2E) Testing:
        Simulate complete user journeys using automated UI tests or manual scripts.
        Key Flows to Test:
            Successful application -> BRE Approve -> Employment Verification -> Submit -> Approved.
            Application -> BRE Reject (Hard rule / Low EMI / Profile Flag False) -> Submit -> Rejected.
            Application -> BRE Refer (Profile Flag True) -> Emp Verification -> Submit -> Under Review.
            Flow involving dev-only skip options (PAN skip, Loan Offer skip).
            Application -> User Drop-off -> Verify Orchestrator triggers LLM & Email.
            Flows involving document upload errors/retries.
            Flows involving verification failures.
        (Removed: E2E Tests involving Bureau Confirmation screen and second BRE pass)
        Validate data consistency across UI, Firestore, Camunda, logs.
    10.1.4 UI/UX Testing:
        Manual testing across devices/sizes/OS.
        Verify layouts, navigation, animations, progressive reveal.
        Check usability, instructions, error handling.
        Accessibility testing.
        Verify fixes for UI bugs (Sec 3.4).
    10.1.5 Security Testing:
        SAST, DAST, Dependency Scanning, API Security Testing, Infrastructure Review, Secrets Audit, Data Privacy Compliance Check.

### 10.2 Deployment Strategy

(Phases aligned with Section 5.7 v1.3)

    Environment Promotion: Dev -> Staging/UAT -> Production.
    Versioning: Strict version control (App, BPMN, BRE Worker, Orchestrator). Semantic versioning.
    Infrastructure: Use Infrastructure as Code (IaC).
    Deployment Process:
        Deploy DB schema changes (Firestore, Appwrite).
        Deploy backend services (Camunda BPMN v1, Python BRE v1, Metadata Orchestrator v1) to Staging.
        Deploy Android App RC build to Staging.
        Perform UAT/Regression in Staging.
        Schedule Production deployment window.
        Deploy backend updates to Production.
        Release Android App gradually via Google Play Store rollout.
        Monitor production closely.
    Rollback Plan: Documented procedures for rolling back each component.

### 10.3 Monitoring Strategy

    APM: Firebase Performance Monitoring / Sentry for app crashes, ANRs, network times.
    Backend Service Monitoring:
        Camunda: Camunda Operate for workflow monitoring, incidents, metrics. Monitor Zeebe gateway health.
        Python BRE / Metadata Orchestrator / Appwrite: Cloud provider monitoring (CPU/Memory, latency, errors). Structured logging/aggregation.
        LLM Service: Monitor API usage, latency, errors via provider dashboards (Google AI Studio/Vertex AI, OpenAI).
    Database Monitoring:
        Firestore: Monitor R/W ops, latency, rule denials via Cloud Monitoring. Alerts for high usage/errors.
        Appwrite: Monitor DB performance via Appwrite console/monitoring.
    Business Metrics: Dashboards (Looker Studio/etc.) tracking: Completion rates, Drop-off rates, BRE decision distribution, Turnaround time, Document upload success/failure, LLM analysis volume.
    Alerting: Configure alerts for critical errors (API 5xx, crashes, Camunda incidents, failed BRE tasks, high drop-off rates) via PagerDuty, Opsgenie, email/Slack.