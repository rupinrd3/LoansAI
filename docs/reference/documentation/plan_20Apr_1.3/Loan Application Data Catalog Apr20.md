# Loan Application Data Catalog

## Document Information

**Version:** 1.3.0
**Last Updated:** April 20, 2025
**Owner:** Loan Application Team

## Change Log

| Version | Date       | Author       | Description                                                                                                                                                                                                                                                        |
| :------ | :--------- | :----------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0.0   | 2025-04-14 | LoanSAI Team | Initial version based on initial analysis.                                                                                                                                                                                                                         |
| 1.1.0   | 2025-04-19 | LoanSAI Team | Incorporated Metadata concepts, UI changes.                                                                                                                                                                                                                      |
| 1.2.0   | 2025-04-19 | LoanSAI Team | Added revised flow (2nd BRE pass), updated Appwrite structure (2 collections), updated Document structure (documentSourceType, extractionStatus, conditional extractedData), added Bureau Confirmation & LLM fields, refined BRE I/O, detailed Metadata section.      |
| 1.3.0   | 2025-04-20 | Gemini       | Implemented Single-Pass BRE, removed Bureau Confirmation screen/flow & associated fields (bureauConfirmationInput, 2nd pass BRE fields). Updated Appwrite to 3 collections (enquiries added). Updated extractedData with specific fields per doc type. Set Metadata storage to subcollection. Updated effective date. Confirmed VerificationMethod enum. Added specific LLM models. Clarified skip logic. Integrated detailed BRE rules. |

## Table of Contents

1.  Introduction
2.  Version Control Plan
3.  Data Relationships and Flow (Revised v1.3 - Single Pass BRE)
4.  Data Catalog - Detailed Fields
    4.1. User Authentication Data (`users` collection - Firestore)
    4.2. Personal Information (`applications.personalInfo` map - Firestore)
    4.3. Employment Details (`applications.employmentDetails` map - Firestore)
    4.4. Verification Data (`employment_verifications`, `otps` collections - Firestore)
    4.5. Document Data (Revised v1.3) (`documents` collection - Firestore)
    4.6. Bureau Report Data (Appwrite - Revised v1.3)
    4.7. Loan Application & Status Data (Revised v1.3) (`applications` collection - Firestore)
    4.8. BRE Input Data (Revised v1.3) (`applications.breInputData` map - Firestore)
    4.9. BRE Output Data (Revised v1.3) (`applications.breOutputData` map - Firestore)
    4.10. Loan Offer Data (Revised v1.3) (`applications.loanOffer` map - Firestore)
    4.11. Application Metadata (Revised v1.3) (`applications/{appId}/metadata` subcollection - Firestore)
5.  Appendix A: Enum Definitions (Revised v1.3)

## 1. Introduction

This document provides the exhaustive definition of all data elements used within the v1.3 Loan Application system, encompassing the Android application, Firestore database, Appwrite database, Camunda workflow, Python BRE, Metadata Orchestrator, and LLM interactions. It serves as the single source of truth for data structure, types, sources, sensitivity, and purpose.

## 2. Version Control Plan

Changes to this catalog MUST follow a version control process. Minor updates increment the patch version (1.3.x), schema changes increment the minor version (1.x.0), and major architectural shifts increment the major version (x.0.0). All changes MUST be documented in the Change Log. Application code, backend services, and database schemas MUST align with the currently approved version of this catalog.

## 3. Data Relationships and Flow (Revised v1.3 - Single Pass BRE)

A User authenticates via Firebase Auth, creating a record in the `users` collection. When starting a loan, an `applications` document is created, linked to the User. As the user progresses, data populates the nested maps (`personalInfo`, `employmentDetails`) within the `applications` document. Uploaded files result in entries in the `documents` collection (linked to `applications` and `users`) and files in Firebase Storage. Bureau data is fetched from Appwrite (`borrower_summary`, `enquiries`, `tradelines` collections, linked by PAN) and associated with the `applications` document. The app triggers Camunda, passing relevant data from the `applications` document for a single Business Rules Engine (BRE) pass. Camunda orchestrates calls to Appwrite and the Python BRE. BRE results (decision, offer parameters, profile flag) are stored back in the `applications` document (`breOutputData`). Final offer details are stored in `loanOffer`. Throughout this process, detailed interaction metadata is collected by the app and stored within a dedicated `metadata` subcollection under the `applications` document, and critical events are sent to the Metadata Orchestrator. Verification attempts are logged in `employment_verifications` and temporary OTPs in the `otps` collection. **Note:** The previous flow involving a Bureau Confirmation screen and a second BRE pass triggered by LLM recalculation has been removed in favor of the single-pass BRE approach.

## 4. Data Catalog - Detailed Fields

### 4.1. User Authentication Data (`users` collection - Firestore)

| Field Name                | Description                     | Data Type | Source          | Sensitive | Notes                           |
| :------------------------ | :------------------------------ | :-------- | :-------------- | :-------- | :------------------------------ |
| `id`                      | Firebase Auth User ID           | String    | Firebase Auth   | No        | Document ID                     |
| `phoneNumber`             | User's phone number             | String    | User Input      | Yes       | Primary auth method (E.164)     |
| `email`                   | User's email address            | String    | User Input      | Yes       | Optional, potentially used for comms |
| `isPrivacyPolicyAccepted` | Consent flag for privacy policy | Boolean   | User Input      | No        | Set during OTP verification     |
| `privacyPolicyVersion`    | Version of accepted policy      | String    | System Config   | No        | e.g., "1.0"                     |
| `privacyPolicyAcceptedAt` | Timestamp of acceptance         | Timestamp | System          | No        | Server timestamp                |
| `createdAt`               | Account creation time           | Timestamp | Firebase Auth   | No        |                                 |
| `lastSignInAt`            | Last sign-in time               | Timestamp | System          | No        | Updated on successful sign-in   |
| `fcmTokens`               | FCM registration tokens map     | Map       | FCM SDK         | Maybe     | Key: Device ID, Value: FCM Token|

### 4.2. Personal Information (`applications.personalInfo` map - Firestore)

| Field Name               | Description                      | Data Type | Source           | Sensitive | Notes                           |
| :----------------------- | :------------------------------- | :-------- | :--------------- | :-------- | :------------------------------ |
| `name`                   | Full Name as per documents       | String    | User Input / OCR | Yes       |                                 |
| `dateOfBirth`            | Date of Birth                    | String    | User Input / OCR | Yes       | Format: "YYYY-MM-DD"            |
| `gender`                 | User's gender                    | String    | User Input       | No        | Enum: Gender (MALE, FEMALE, OTHER) |
| `panNumber`              | Permanent Account Number         | String    | User Input / OCR | Yes       | Displayed on Key Fact Sheet     |
| `mobileNumber`           | Registered Mobile Number         | String    | User Profile     | Yes       | Displayed on Key Fact Sheet     |
| `email`                  | Email Address                    | String    | User Input       | Yes       | Used for communication          |
| `maritalStatus`          | Marital Status                   | String    | User Input       | No        | Enum: MaritalStatus (SINGLE, MARRIED, ...) (Optional) |
| `alternatePhoneNumber`   | Alternate contact number         | String    | User Input       | Yes       | Optional                        |
| `address`                | Current Residential Address Map  | Map       | User Input / Bureau | Yes       | Nested Map                      |
| `address.addressLine1`   | Flat/House No, Building Name     | String    | User Input / Bureau | Yes       |                                 |
| `address.addressLine2`   | Street Name, Area, Locality    | String    | User Input / Bureau | Yes       | Optional                        |
| `address.city`           | City Name                        | String    | User Input / Bureau | Yes       |                                 |
| `address.state`          | State Name                       | String    | User Input / Bureau | Yes       |                                 |
| `address.postalCode`     | Postal Code / Pincode            | String    | User Input / Bureau | Yes       |                                 |
| `address.country`        | Country                          | String    | System Default   | No        | Default: "India"                |
| `address.addressType`    | Type of address                  | String    | System Default   | No        | Default: CURRENT (Enum: AddressType) |

### 4.3. Employment Details (`applications.employmentDetails` map - Firestore)

| Field Name               | Description                       | Data Type | Source        | Sensitive | Notes                                                              |
| :----------------------- | :-------------------------------- | :-------- | :------------ | :-------- | :----------------------------------------------------------------- |
| `employmentType`         | Type of Employment                | String    | User Input    | No        | Enum: EmploymentType (PRIVATE_SECTOR, GOVERNMENT, ...)           |
| `employerName`           | Name of Employer / Business       | String    | User Input    | Yes       |                                                                    |
| `monthlySalary`          | Gross Monthly Income              | Number    | User Input    | Yes       | Numeric value                                                      |
| `monthlyEmi`             | Declared Existing EMIs (EMI 1)    | Number    | User Input    | Yes       | User's initial declaration (Can be overridden by declaredEmi2)     |
| `declaredEmi2`           | LLM/User-derived EMI (EMI 2)      | Number    | LLM / User Input | Yes       | Optional. If present, overrides monthlyEmi for BRE calculation.    |
| `workEmail`              | Official Work Email Address       | String    | User Input    | Yes       | Optional for 'GOVERNMENT' type, Displayed on Key Fact Sheet        |
| `designation`            | Job Title / Designation           | String    | User Input    | No        | Not applicable/removed for 'PRIVATE_SECTOR'                      |
| `department`             | Department Name                   | String    | User Input    | No        | Optional                                                           |
| `officeAddress`          | Office Address Map                | Map       | User Input    | Yes       | Nested Map, Displayed on Key Fact Sheet                          |
| `officeAddress.address*` | (Fields similar to home addr)     | String    | User Input    | Yes       |                                                                    |
| `officeAddress.addrType` | Type of address                   | String    | System Default| No        | Default: OFFICE (Enum: AddressType)                              |

### 4.4. Verification Data (`employment_verifications`, `otps` collections - Firestore)

**`employment_verifications` Collection** (Document ID: Unique Verification ID or App ID)

| Field Name         | Description                      | Data Type | Source                | Sensitive | Notes                            |
| :----------------- | :------------------------------- | :-------- | :-------------------- | :-------- | :------------------------------- |
| `verificationId`   | Unique ID for this attempt       | String    | System (UUID)         | No        | If multiple attempts allowed     |
| `applicationId`    | Application ID                   | String    | System                | No        | Link to `applications`           |
| `userId`           | User ID                          | String    | System                | No        | Link to `users`                  |
| `verificationMethod`| Method used                      | String    | System/User Selection | No        | Enum: VerificationMethod (EMAIL_OTP, ID_CARD_SCAN) |
| `status`           | Verification status              | String    | System                | No        | Enum: VerificationStatus         |
| `isVerified`       | Convenience flag                 | Boolean   | System                | No        | True if status == VERIFIED       |
| `initiatedAt`      | Timestamp verification started   | Timestamp | System                | No        |                                  |
| `verifiedAt`       | Timestamp verification completed | Timestamp | System                | No        |                                  |
| `verifiedEmail`    | Email used for OTP method        | String    | System                | Yes       |                                  |
| `details`          | Failure reason or notes          | String    | System                | No        |                                  |

**`otps` Collection** (Document ID: Target Identifier - Phone/Email)

| Field Name        | Description                   | Data Type | Source    | Sensitive | Notes                             |
| :---------------- | :---------------------------- | :-------- | :-------- | :-------- | :-------------------------------- |
| `targetIdentifier`| Phone (E.164) or Email        | String    | System    | Yes       | Document ID                       |
| `otp`             | The 6-digit OTP code          | String    | System    | Yes       | Should be hashed ideally          |
| `isVerified`      | Was OTP successfully used?    | Boolean   | System    | No        |                                   |
| `createdAt`       | Timestamp OTP generated       | Timestamp | System    | No        |                                   |
| `expiresAt`       | Timestamp OTP becomes invalid | Timestamp | System    | No        | e.g., createdAt + 15 minutes    |
| `attemptCount`    | Number of validation tries    | Number    | System    | No        | Increment on failed verification  |

### 4.5. Document Data (Revised v1.3) (`documents` collection - Firestore)

| Field Name           | Description                         | Data Type | Source                | Sensitive | Notes                                               |
| :------------------- | :---------------------------------- | :-------- | :-------------------- | :-------- | :-------------------------------------------------- |
| `id`                 | Unique Document ID                  | String    | System (UUID)         | No        | Document ID                                         |
| `applicationId`      | Associated Application ID           | String    | System                | No        | Link to `applications` collection                   |
| `userId`             | User ID                             | String    | System                | No        | Link to `users` collection                          |
| `documentType`       | Type of document                    | String    | User Selection        | No        | Enum: DocumentType                                  |
| `documentSourceType` | How the document was provided       | String    | System                | No        | Enum: DocumentSourceType (CAMERA_IMAGE, IMAGE_UPLOAD, ...) |
| `documentStatus`     | Current status                      | String    | System                | No        | Enum: DocumentStatus                                |
| `fileName`           | Original file name                  | String    | User Upload/Sys       | No        |                                                     |
| `fileType`           | File extension (pdf, jpg, png)      | String    | System                | No        | Mime type might be better                           |
| `fileSize`           | File size in bytes                  | Number    | System                | No        |                                                     |
| `storageUrl`         | URL in Firebase Storage             | String    | System                | No        | gs:// or https:// URL                               |
| `uploadedAt`         | Timestamp of upload                 | Timestamp | System                | No        |                                                     |
| `processedAt`        | Timestamp processing finished       | Timestamp | System                | No        |                                                     |
| `extractionStatus`   | Status of data extraction           | String    | OCR/Parser            | No        | Enum: ExtractionStatus (SUCCESS, PARTIAL_SUCCESS, ...) |
| `extractedData`      | Extracted key-value pairs (Specific)| Map       | OCR/Parser            | Varies    | See details below                                   |
| `failureReason`      | Reason for PROCESSING/ERROR status  | String    | System/Manual         | No        |                                                     |
| `cameraMetadata`     | Camera details if source is camera  | Map       | App                   | No        | Optional: {resolution, ambientLight, cameraAngle}   |

**Details for `extractedData` Map (Based on User Input):**

* If `documentType` is `PAN_CARD`:
    * `fullName`: String (Sensitive: Yes)
    * `dateOfBirth`: String (YYYY-MM-DD) (Sensitive: Yes)
    * `panNumber`: String (Sensitive: Yes)
* If `documentType` is `BANK_STATEMENT`:
    * `customerName`: String (Sensitive: Yes)
    * `address`: String (Sensitive: Yes)
    * `bankAccountNumber`: String (Sensitive: Yes)
    * `transactions`: Array[Map] (Sensitive: Yes) containing:
        * `transactionDate`: String (or Timestamp)
        * `narration`: String
        * `transactionAmount`: Number
        * `balance`: Number (Optional)
* If `documentType` is `SALARY_SLIP`:
    * `employerName`: String (Sensitive: Yes)
    * `employeeName`: String (Sensitive: Yes)
    * `monthOfSalary`: String (e.g., "YYYY-MM") (Sensitive: No)
    * `grossSalaryAmount`: Number (Sensitive: Yes)
    * `netSalaryAmount`: Number (Sensitive: Yes)
* If `documentType` is `FORM_16`:
    * `employerName`: String (Sensitive: Yes)
    * `employeeName`: String (Sensitive: Yes)
    * `employerPAN`: String (Sensitive: Yes)
    * `employeePAN`: String (Sensitive: Yes)
    * `assessmentYear`: String (e.g., "YYYY-YY") (Sensitive: No)
    * `totalAmountPaidCredited`: Number (Sensitive: Yes)
    * `totalTaxDeducted`: Number (Sensitive: Yes)
* If `documentType` is `FORM_26AS`:
    * `panNumber`: String (Sensitive: Yes)
    * `assessmentYear`: String (e.g., "YYYY-YY") (Sensitive: No)
    * `assesseeName`: String (Sensitive: Yes)
    * `largestDeductorName`: String (Sensitive: Yes)
    * `largestDeductorTAN`: String (Sensitive: Yes)
    * `totalAmountPaidCredited`: Number (Sensitive: Yes)
    * `totalTaxDeducted`: Number (Sensitive: Yes)

### 4.6. Bureau Report Data (Appwrite - Revised v1.3)

* **`borrower_summary` Collection (Appwrite)** (Document ID: PAN Number) - Stores aggregated bureau info for a person.
    * *(Detailed field list to be defined based on sample CIBIL report. Includes fields like panNumber, bureauType, customerName, creditScore, reportDate, dateOfBirth, gender, addresses (array), phoneNumbers (array), emails (array), account summary counts, aggregate amounts, recentEnquiries count, rawDataReference)*
* **`enquiries` Collection (Appwrite)** (Document ID: Unique Enquiry ID) - Stores details of individual credit enquiries made. (NEW)
    * *(Detailed field list to be defined based on sample CIBIL report. Likely includes panNumber (FK), enquiryDate, enquiringMemberName, enquiryPurpose, enquiryAmount)*
* **`tradelines` Collection (Appwrite)** (Document ID: Unique Tradeline ID) - Stores details of individual loans/credit lines reported.
    * *(Detailed field list to be defined based on sample CIBIL report. Includes tradelineId, panNumber (FK), accountNumber, lenderName, productType, sanctionAmount, currentBalance, openDate, lastReportedDate, accountStatus, paymentHistory (string), overdueAmount, emiAmount)*

**Note:** The exact schema for the Appwrite collections (`borrower_summary`, `enquiries`, `tradelines`) requires the sample CIBIL report for accurate definition.

### 4.7. Loan Application & Status Data (Revised v1.3) (`applications` collection - Firestore)

| Field Name        | Description                         | Data Type     | Source                  | Sensitive | Notes                                     |
| :---------------- | :---------------------------------- | :------------ | :---------------------- | :-------- | :---------------------------------------- |
| `id`              | Unique Application ID               | String        | System                  | No        | Document ID                               |
| `userId`          | Associated User ID                  | String        | System                  | No        | Link to `users`                           |
| `applicationStatus`| Overall status                      | String        | System/BRE/Orchestrator | No        | Enum: ApplicationStatus (includes DROPPED_OFF) |
| `currentStep`     | Current step in the UI flow         | String        | App                     | No        | Enum: ApplicationStep (Bureau Confirmation Removed) |
| `completedSteps`  | List of completed UI steps          | Array[String] | App                     | No        | Enum: ApplicationStep                     |
| `breInputData`    | Data sent to BRE                    | Map           | System                  | Varies    | Snapshot for audit/retry (Single Pass)    |
| `breOutputData`   | Result from BRE                     | Map           | BRE                     | Varies    | Includes decision, offer params, profileFlag |
| `loanOffer`       | Final offer/decision details presented | Map           | System                  | Varies    | Based on BRE output                       |
| `createdAt`       | Application creation timestamp      | Timestamp     | System                  | No        |                                           |
| `lastUpdatedAt`   | Last update timestamp               | Timestamp     | System                  | No        | Useful for drop-off detection             |
| `submittedAt`     | Final submission timestamp          | Timestamp     | System                  | No        | When user hits final submit               |
| `finalDecisionAt` | Timestamp of final Approve/Reject | Timestamp     | System                  | No        | When status becomes non-reviewable        |

*(Removed: `bureauConfirmationInput`, `breInputData_1stPass`, `breOutputData_1stPass`, `loanOffer_1stPass`, `breInputData_2ndPass`, `breOutputData_2ndPass`, `loanOffer_final` - replaced by single pass fields)*

### 4.8. BRE Input Data (Revised v1.3) (`applications.breInputData` map - Firestore)

| Field Name            | Description                         | Data Type | Source                 | Sensitive | Notes                                     |
| :-------------------- | :---------------------------------- | :-------- | :--------------------- | :-------- | :---------------------------------------- |
| `applicationId`       | Application ID                      | String    | System                 | No        | For correlation                           |
| `timestamp`           | When input was generated            | Timestamp | System                 | No        |                                           |
| `personalInfo`        | Relevant personal details           | Map       | App/Firestore          | Yes       | Age, CityTier, StateRisk, etc. derived from raw data |
| `employmentDetails`   | Relevant employment details         | Map       | App/Firestore          | Yes       | Type, Income, EmployerRisk, IndustryRisk derived |
| `effectiveMonthlyEmi` | EMI value used for calculation      | Number    | System Logic           | Yes       | Equals `declaredEmi2` if present, else `monthlyEmi` |
| `bureauSummary`       | Key data from `borrower_summary`    | Map       | Appwrite/Firestore     | Yes       | Score, AgeOfCredit, Utilization, Delinquency flags etc. |
| `documentInsights`    | Key data from `documents.extractedData`| Map       | OCR/Parser/Firestore   | Yes       | Bank Bal Stability, Salary Credit Match, ITR data etc. |
| `requestedAmount`     | Loan amount requested by user       | Number    | User Input/Firestore   | No        | From Loan Offer screen interaction if modified (Refer case only) |
| `requestedTenor`      | Loan tenor requested by user        | Number    | User Input/Firestore   | No        | From Loan Offer screen interaction if modified (Refer case only) |

*(Removed: `passNumber`, `recalculatedObligation`, `declaredObligation` - replaced by `effectiveMonthlyEmi`)*

### 4.9. BRE Output Data (Revised v1.3) (`applications.breOutputData` map - Firestore)

| Field Name                | Description                           | Data Type     | Source    | Sensitive | Notes                                         |
| :------------------------ | :------------------------------------ | :------------ | :-------- | :-------- | :-------------------------------------------- |
| `applicationId`           | Application ID                        | String        | System    | No        | Echoed back                                   |
| `timestamp`               | When output was generated             | Timestamp     | BRE       | No        |                                               |
| `decisionStatus`          | BRE decision                          | String        | BRE       | No        | Enum: DecisionStatus (AUTO_APPROVED, REJECTED, REFER) |
| `approvedAmount`          | Max amount approved (if applicable)   | Number        | BRE       | No        |                                               |
| `interestRate`            | Offered interest rate (if applicable) | Number        | BRE       | No        | Annual %                                      |
| `maxTenor`                | Max tenor offered (if applicable)     | Number        | BRE       | No        | Months                                        |
| `processingFeePercentage` | Offered processing fee (if applicable)| Number        | BRE       | No        | %                                             |
| `rejectionReasons`        | Codes/list of reasons if rejected     | Array[String] | BRE       | No        | Standardized codes/descriptions               |
| `referralReasons`         | Codes/list of reasons if referred     | Array[String] | BRE       | No        | Standardized codes/descriptions               |
| `profileFlag`             | Flag indicating profile check status  | Boolean       | BRE       | No        | True = Profile OK, False = Profile Not Okay |
| `riskScore`               | Internal risk score calculated by BRE | Number        | BRE       | Yes       | Optional, for analytics                       |
| `ruleVersion`             | Version of rules used for decision    | String        | BRE       | No        | For auditability                              |

*(Removed: `passNumber`. Clarified `profileFlag` meaning based on user rules)*

### 4.10. Loan Offer Data (Revised v1.3) (`applications.loanOffer` map - Firestore)

| Field Name                | Description                             | Data Type | Source      | Sensitive | Notes                                     |
| :------------------------ | :-------------------------------------- | :-------- | :---------- | :-------- | :---------------------------------------- |
| `offerStatus`             | Status relative to user interaction     | String    | System/User | No        | Enum: OfferStatus                         |
| `generatedAt`             | Timestamp offer was calculated by BRE   | Timestamp | System      | No        | Copied from `breOutputData.timestamp`     |
| `presentedAt`             | Timestamp offer was shown to user       | Timestamp | System      | No        |                                           |
| `acceptedAt`              | Timestamp user accepted the offer       | Timestamp | User        | No        |                                           |
| `selectedAmount`          | Amount selected/accepted by user        | Number    | User/System | No        | Can differ from `approvedAmount` only if `decisionStatus`==REFER |
| `selectedTenor`           | Tenor selected/accepted by user         | Number    | User/System | No        | Can differ from `maxTenor` only if `decisionStatus`==REFER |
| `interestRate`            | Final rate for selected offer           | Number    | BRE/System  | No        |                                           |
| `processingFeePercentage` | Final fee % for selected offer          | Number    | BRE/System  | No        |                                           |
| `processingFeeAmount`     | Calculated fee amount                   | Number    | System      | No        | `selectedAmount` * `processingFeePercentage` / 100 |
| `emiAmount`               | Calculated EMI for selected offer       | Number    | System      | No        | Calculated using PMT formula              |
| `decisionStatus`          | Copied from BRE Output for context      | String    | BRE/System  | No        |                                           |

### 4.11. Application Metadata (Revised v1.3) (`applications/{appId}/metadata` subcollection - Firestore)

**Storage:** Metadata will be stored in a dedicated subcollection named `metadata` under each `applications/{applicationId}` document. Each event or snapshot within the subcollection will have its own Document ID (e.g., UUID or timestamp-based). The `applicationId` should also be stored within each metadata document for easier querying if needed directly on the subcollection.

**Fields (within each metadata doc):**

| Field Name      | Description                      | Data Type | Source    | Sensitive | Notes                                        |
| :-------------- | :------------------------------- | :-------- | :-------- | :-------- | :------------------------------------------- |
| `eventId`       | Unique ID for the metadata event | String    | App       | No        | Document ID of the metadata record           |
| `applicationId` | Parent Application ID            | String    | App       | No        | Explicitly stored field                      |
| `eventTimestamp`| Timestamp of the event           | Timestamp | App       | No        | When the specific event occurred             |
| `eventType`     | Type of metadata event           | String    | App       | No        | Enum: MetadataEventType                      |
| `deviceInfo`    | Phone make, model, OS, app version | Map       | App       | No        | {make, model, osVersion, appVersion} (Likely in session start event) |
| `appSession`    | App start/end times, duration, drop | Map       | App       | No        | {sessionId, startTime, endTime, durationSeconds, droppedOff, dropOffTimestamp} |
| `screenVisit`   | Data for a single screen visit   | Map       | App       | No        | {screenName, startTime, endTime, durationSeconds} |
| `sectionTiming` | Data for a single section completion | Map       | App       | No        | {screenName, sectionName, startTime, endTime, durationSeconds} |
| `documentEvent` | Details for a single doc action  | Map       | App       | Varies    | {docType, docSource, action, status, failureReason, extractionStatus, cameraMeta, fileMeta} |
| `verificationEvent`| Details for a verification attempt| Map       | App       | No        | {verificationType, status, failureReason}   |
| `errorEvent`    | Details for a critical app error | Map       | App       | No        | {screenName, errorCode, errorMessage, stackTrace?} |

## 5. Appendix A: Enum Definitions (Revised v1.3)

### Application Status (`applications.applicationStatus`)
```kotlin
// Represents the overall state of the loan application process
enum class ApplicationStatus {
    CREATED,        // Initial state when record is created
    IN_PROGRESS,    // User is actively filling the form or interacting
    SUBMITTED,      // User has completed the form and submitted for final decision/review
    UNDER_REVIEW,   // Application requires manual underwriter review (typically after 'REFER' decision)
    APPROVED,       // Final Decision: Loan Approved
    REJECTED,       // Final Decision: Loan Rejected
    CANCELLED,      // User explicitly cancelled the application
    EXPIRED,        // Offer or application validity period expired
    DROPPED_OFF     // User abandoned journey for extended period (detected by orchestrator)
}

Decision Status (BRE Output) (breOutputData.decisionStatus)
Kotlin

// Represents the outcome of the Business Rules Engine execution
enum class DecisionStatus {
    AUTO_APPROVED,  // Approved automatically by rules
    REJECTED,       // Rejected automatically by rules
    REFER           // Requires further action (manual review or specific follow-up)
}

Document Type (documents.documentType)
Kotlin

// Represents the type of document being handled
enum class DocumentType {
    PAN_CARD,
    BANK_STATEMENT, // Typically last 3-6 months
    SALARY_SLIP,    // Typically last 3 months
    FORM_16,        // Income Tax Form 16
    FORM_26AS,      // Tax Credit Statement (Form 26AS)
    ID_CARD,        // Generic ID Proof (e.g., Aadhaar, Voter ID - specific type might be needed)
    ADDRESS_PROOF,  // Separate address proof if ID card doesn't suffice (e.g., Utility Bill)
    PHOTO,          // Applicant photograph
    OTHER           // For any miscellaneous documents
}

Document Status (documents.documentStatus)
Kotlin

// Represents the lifecycle status of a specific document
enum class DocumentStatus {
    PENDING_UPLOAD, // Placeholder exists, awaiting file upload
    UPLOADING,      // File upload currently in progress
    UPLOADED,       // File successfully stored in Firebase Storage, awaiting processing
    PROCESSING,     // Backend OCR/parsing/validation is in progress
    PROCESSED,      // Backend processing completed (check extractionStatus for details)
    VERIFICATION_PENDING, // Processed, awaiting system/manual validation of extracted data
    VERIFIED,       // Extracted data or document content validated
    REJECTED,       // Document invalid (wrong type, illegible, fraudulent, expired)
    ERROR           // System error occurred during upload or processing
}

Document Source Type (documents.documentSourceType)
Kotlin

// Indicates how the document file was provided by the user
enum class DocumentSourceType {
    CAMERA_IMAGE,   // Captured live using the in-app camera interface
    IMAGE_UPLOAD,   // User uploaded an existing image file (jpg, png, etc.) from gallery/storage
    PDF_UPLOAD      // User uploaded a PDF file from gallery/storage
}

Extraction Status (documents.extractionStatus)
Kotlin

// Indicates the success level of extracting required data fields from a processed document
enum class ExtractionStatus {
    NOT_ATTEMPTED,  // Processing not done or didn't include extraction
    SUCCESS,        // All necessary/required fields were successfully extracted
    PARTIAL_SUCCESS,// Some required fields were extracted, but others failed or were missing
    FAILURE         // Unable to extract most/all required fields, or document deemed unreadable
}

Offer Status (User Interaction with Offer) (loanOffer.offerStatus)
Kotlin

// Represents the user's interaction state with a specific loan offer presentation
enum class OfferStatus {
    GENERATED,      // Offer calculated by BRE, ready to be shown
    PRESENTED,      // Offer currently displayed to the user
    ACCEPTED,       // User explicitly accepted the presented/selected offer
    REJECTED,       // User explicitly declined the presented offer (or skipped resulting in Refer)
    EXPIRED,        // Offer timed out without user action
    MODIFIED        // User adjusted amount/tenor sliders (only applicable if decisionStatus == REFER)
}

Application Step (UI Flow Tracking) (applications.currentStep, applications.completedSteps[])
Kotlin

// Represents distinct screens or logical steps in the user interface flow
// Note: BUREAU_CONFIRMATION removed due to single-pass BRE
enum class ApplicationStep {
    LOGIN,
    PRIVACY_POLICY,
    HOME,
    PAN_ENTRY,
    PERSONAL_INFO,
    EMPLOYMENT_DETAILS,
    DOCUMENT_UPLOAD,
    LOAN_OFFER,
    EMPLOYMENT_VERIFICATION,
    KEY_FACT_SHEET,
    APPLICATION_SUBMITTED // Final confirmation screen shown
}

Gender (applications.personalInfo.gender)
Kotlin

enum class Gender { MALE, FEMALE, OTHER }

Marital Status (applications.personalInfo.maritalStatus)
Kotlin

enum class MaritalStatus { SINGLE, MARRIED, DIVORCED, WIDOWED }

Employment Type (applications.employmentDetails.employmentType)
Kotlin

enum class EmploymentType { PRIVATE_SECTOR, GOVERNMENT, SELF_EMPLOYED, OTHER }

Address Type (applications.personalInfo.address.addressType, applications.employmentDetails.officeAddress.addressType)
Kotlin

enum class AddressType { CURRENT, PERMANENT, OFFICE }

Verification Method (employment_verifications.verificationMethod)
Kotlin

// Aligned with Data Catalog, ensure app code (VerificationMethod.kt) matches this
enum class VerificationMethod {
    EMAIL_OTP,      // Verification via OTP sent to work email
    ID_CARD_SCAN,   // Verification via scanning official ID card
    MANUAL          // Requires manual backend verification (fallback)
}

Verification Status (employment_verifications.status)
Kotlin

// Represents the status of a specific verification attempt
enum class VerificationStatus {
    PENDING,        // Verification initiated, awaiting action (e.g., OTP sent, scan pending)
    SENT,           // OTP Sent (for EMAIL_OTP method)
    FAILED_TO_SEND, // Unable to send OTP
    VERIFIED,       // Verification successful (OTP matched, ID card processed/validated)
    REJECTED,       // Verification failed (Incorrect OTP, invalid ID, manual rejection)
    ERROR           // System error during verification process
}

Metadata Event Type (metadata.eventType)
Kotlin

// Represents the type of metadata event being logged
enum class MetadataEventType {
    APP_SESSION_START,
    APP_SESSION_END,
    APP_BACKGROUNDED, // Specific event for drop-off detection trigger
    APP_FOREGROUNDED, // Specific event for potential drop-off cancellation
    SCREEN_VISIT,
    SECTION_COMPLETED,
    DOCUMENT_EVENT,   // Covers capture attempt, upload attempt, processing result
    VERIFICATION_EVENT,
    ERROR_OCCURRED,
    APPLICATION_SUBMITTED, // User hit final submit
    APPLICATION_CANCELLED // User explicitly cancelled
    // Add other specific interaction events as needed
}
