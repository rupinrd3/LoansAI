# Loan Application Data Catalog

## Document Information

**Version:** 1.4.0
**Last Updated:** April 25, 2025
**Owner:** Loan Application Team

## Change Log

| Version | Date       | Author       | Description                                                                                                                                                                                                                                                        |
| :------ | :--------- | :----------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0.0   | 2025-04-14 | LoanSAI Team | Initial version based on initial analysis.                                                                                                                                                                                                                         |
| 1.1.0   | 2025-04-19 | LoanSAI Team | Incorporated Metadata concepts, UI changes.                                                                                                                                                                                                                      |
| 1.2.0   | 2025-04-19 | LoanSAI Team | Added revised flow (2nd BRE pass), updated Appwrite structure (2 collections), updated Document structure (documentSourceType, extractionStatus, conditional extractedData), added Bureau Confirmation & LLM fields, refined BRE I/O, detailed Metadata section.      |
| 1.3.0   | 2025-04-20 | Gemini       | Implemented Single-Pass BRE, removed Bureau Confirmation screen/flow & associated fields (bureauConfirmationInput, 2nd pass BRE fields). Updated Appwrite to 3 collections (enquiries added). Updated extractedData with specific fields per doc type. Set Metadata storage to subcollection. Updated effective date. Confirmed VerificationMethod enum. Added specific LLM models. Clarified skip logic. Integrated detailed BRE rules. |
| **1.4.0** | **2025-04-25** | **Gemini** | **Updated Appwrite Bureau Data schema (Sec 4.6) based on `Appwrite Bureau Data Attributes.txt` [cite: 1] and `Bureau Report data schema.json`[cite: 4]. Updated Metadata Structure (Sec 4.11) to reflect Firebase Cloud Functions implementation and subcollection storage. Ensured consistency with single-pass BRE flow across relevant sections.** |

## Table of Contents

1.  Introduction
2.  Version Control Plan
3.  Data Relationships and Flow (Revised v1.4 - Single Pass BRE & Cloud Functions Orchestrator)
4.  Data Catalog - Detailed Fields
    4.1. User Authentication Data (`users` collection - Firestore)
    4.2. Personal Information (`applications.personalInfo` map - Firestore)
    4.3. Employment Details (`applications.employmentDetails` map - Firestore)
    4.4. Verification Data (`employment_verifications`, `otps` collections - Firestore)
    4.5. Document Data (`documents` collection - Firestore)
    4.6. Bureau Report Data (Appwrite - **Revised v1.4 - 3 Collections**)
    4.7. Loan Application & Status Data (`applications` collection - Firestore)
    4.8. BRE Input Data (`applications.breInputData` map - Firestore)
    4.9. BRE Output Data (`applications.breOutputData` map - Firestore)
    4.10. Loan Offer Data (`applications.loanOffer` map - Firestore)
    4.11. Application Metadata (**Revised v1.4 - Subcollection & Cloud Functions Focus**)
5.  Appendix A: Enum Definitions (Revised v1.4)

## 1. Introduction

This document provides the exhaustive definition of all data elements used within the **v1.4** Loan Application system, encompassing the Android application, Firestore database, Appwrite database, Camunda workflow, Python BRE, **Firebase Cloud Functions Metadata Orchestrator**, and LLM interactions. It serves as the single source of truth for data structure, types, sources, sensitivity, and purpose.

## 2. Version Control Plan

*(No changes from v1.3)*

## 3. Data Relationships and Flow (Revised v1.4 - Single Pass BRE & Cloud Functions Orchestrator)

A User authenticates via Firebase Auth (`users` collection). Starting a loan creates/updates an `applications` document (linked to User). User data populates nested maps (`personalInfo`, `employmentDetails`). Uploaded files go to Firebase Storage, with metadata in the `documents` collection. Bureau data is fetched via Camunda orchestration from Appwrite (**`borrower_summary`**, **`enquiries`**, **`tradelines`** collections, linked by PAN). App triggers Camunda for a **single BRE pass**, passing data. Camunda calls Appwrite and the Python BRE. BRE results (`breOutputData`) are stored in `applications`. The final offer is stored in `loanOffer`. Throughout, metadata is stored in the **`applications/{appId}/metadata` subcollection** and critical events are sent to the **Firebase Cloud Functions Metadata Orchestrator**. Verifications are logged (`employment_verifications`, `otps`).

## 4. Data Catalog - Detailed Fields

### 4.1. User Authentication Data (`users` collection - Firestore)

*(No changes from v1.3)*

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

*(No changes from v1.3)*

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

*(No changes from v1.3)*

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

*(No changes from v1.3 - Schema confirmed by `sample_metadata_documents_and_OTPs.txt` [cite: 2])*

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

### 4.5. Document Data (`documents` collection - Firestore)

*(No significant schema changes from v1.3, structure confirmed by `sample_metadata_documents_and_OTPs.txt` [cite: 2])*

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
| `extractedData`      | Extracted key-value pairs (Specific)| Map       | OCR/Parser            | Varies    | Structure depends on `documentType`                 |
| `failureReason`      | Reason for PROCESSING/ERROR status  | String    | System/Manual         | No        |                                                     |
| `cameraMetadata`     | Camera details if source is camera  | Map       | App                   | No        | Optional: {resolution, ambientLight, cameraAngle}   |

*(Details for `extractedData` remain the same as v1.3)*

### 4.6. Bureau Report Data (Appwrite - **Revised v1.4 - 3 Collections**)

* **Purpose:** Stores processed credit bureau data fetched externally. Accessed via Appwrite API (orchestrated by Camunda).
* **Schema Source:** Defined by `Appwrite Bureau Data Attributes.txt` [cite: 1] and `Bureau Report data schema.json`[cite: 4].

* **`borrower_summary` Collection (Appwrite)** (Document ID: PAN Number) [cite: 1, 4]
    * Stores aggregated bureau info for a person.

    | Key                 | Type    | Default | Notes                               |
    | :------------------ | :------ | :------ | :---------------------------------- |
    | `panNumber`         | string  | -       | Primary identifier                |
    | `controlNumber`     | string  | -       | CIBIL Control Number              |
    | `customerName`      | string  | -       | Full Name                         |
    | `creditScore`       | integer | -       | 300-900, null if NH               |
    | `reportDate`        | string  | -       | Date string (e.g., "YYYY-MM-DD")  |
    | `dateOfBirth`       | string  | -       | Date string (e.g., "YYYY-MM-DD")  |
    | `gender`            | string  | -       | "Male", "Female", etc.            |
    | `addresses`         | string  | -       | Likely JSON string of addresses   |
    | `contacts`          | string  | -       | Likely JSON string of contacts    |
    | `email`             | string  | -       | Email address                     |
    | `totalAccounts`     | integer | -       | Total number of accounts reported |
    | `openAccounts`      | integer | -       | Number of currently open accounts |
    | `closedAccounts`    | integer | -       | Number of closed accounts         |
    | `totalLoanAmount`   | double  | -       | Sum of sanctioned amounts/limits  |
    | `currentBalance`    | double  | -       | Total outstanding balance         |
    | `totalOverdueAmount`| double  | -       | Total amount currently overdue    |
    | `suitFiled`         | boolean | false   | Flag if legal suit filed          |
    | `wilfulDefault`     | boolean | false   | Flag if marked as wilful defaulter|
    | `writtenOffStatus`  | boolean | false   | Flag if any account written off   |
    | `delinquencyStatus` | string  | -       | Overall delinquency indicator     |

* **`enquiries` Collection (Appwrite)** (Document ID: Unique Enquiry ID) [cite: 1, 4]
    * Stores details of individual credit enquiries made.

    | Key           | Type   | Default | Notes                                |
    | :------------ | :----- | :------ | :----------------------------------- |
    | `panNumber`   | string | -       | Links to borrower\_summary         |
    | `enquiryDate` | string | -       | Date string (e.g., "YYYY-MM-DD")   |
    | `memberName`  | string | -       | Name of institution making enquiry |
    | `purpose`     | string | -       | Purpose of enquiry (e.g., "Loan")  |
    | `type`        | string | -       | Type of enquiry                    |
    | `amount`      | double | -       | Amount enquired for (if specified) |

* **`tradelines` Collection (Appwrite)** (Document ID: Unique Tradeline ID) [cite: 1, 4]
    * Stores details of individual loans/credit lines reported.

    | Key                   | Type    | Default | Notes                                   |
    | :-------------------- | :------ | :------ | :-------------------------------------- |
    | `panNumber`           | string  | -       | Links to borrower\_summary            |
    | `memberName`          | string  | -       | Name of lending institution           |
    | `accountType`         | string  | -       | e.g., "Personal Loan", "Credit Card"  |
    | `accountNumber`       | string  | -       | Loan/Card account number (masked)     |
    | `ownership`           | string  | -       | e.g., "Individual", "Joint"           |
    | `creditLimit`         | double  | -       | Credit limit (Cards) / Sanction Amt   |
    | `highCredit`          | double  | -       | Highest utilized amount               |
    | `currentBalance`      | double  | -       | Current outstanding balance           |
    | `amountOverdue`       | double  | -       | Amount currently overdue              |
    | `rateOfInterest`      | double  | -       | Interest rate (if reported)           |
    | `repaymentTenure`     | integer | -       | Loan tenure in months (if applicable) |
    | `emiAmount`           | double  | -       | EMI amount (if applicable)            |
    | `dateOpened`          | string  | -       | Date string (e.g., "YYYY-MM-DD")      |
    | `dateClosed`          | string  | -       | Date string (nullable)                |
    | `lastPaymentDate`     | string  | -       | Date string (nullable)                |
    | `dateReported`        | string  | -       | Date string (when this info reported) |
    | `facilityStatus`      | string  | -       | e.g., "Active", "Closed", "Written Off"|
    | `suitFiled`           | string  | -       | Suit filed status ("Yes"/"No"/Null)   |
    | `paymentFrequency`    | string  | -       | e.g., "Monthly"                       |
    | `paymentHistory`      | string  | -       | Encoded payment history string (DPD)  |
    | `writtenOffTotal`     | double  | -       | Total amount written off (nullable)   |
    | `writtenOffPrincipal` | double  | -       | Principal amount written off (nullable)|
    | `controlNumber`       | string  | -       | CIBIL Control Number                  |

* **Indexes (Appwrite):** Define indexes on `panNumber` in `enquiries` and `tradelines` collections for efficient querying.

### 4.7. Loan Application & Status Data (`applications` collection - Firestore)

*(Structure remains the same as v1.3, confirmed by `sample_metadata_Applications.txt` [cite: 3])*

| Field Name        | Description                         | Data Type     | Source                  | Sensitive | Notes                                     |
| :---------------- | :---------------------------------- | :------------ | :---------------------- | :-------- | :---------------------------------------- |
| `id`              | Unique Application ID               | String        | System                  | No        | Document ID                               |
| `userId`          | Associated User ID                  | String        | System                  | No        | Link to `users`                           |
| `applicationStatus`| Overall status                      | String        | System/BRE/Orchestrator | No        | Enum: ApplicationStatus (includes DROPPED_OFF) |
| `currentStep`     | Current step in the UI flow         | String        | App                     | No        | Enum: ApplicationStep                     |
| `completedSteps`  | List of completed UI steps          | Array[String] | App                     | No        | Enum: ApplicationStep                     |
| `breInputData`    | Data sent to BRE                    | Map           | System                  | Varies    | Snapshot for audit/retry (Single Pass)    |
| `breOutputData`   | Result from BRE                     | Map           | BRE                     | Varies    | Includes decision, offer params, profileFlag |
| `loanOffer`       | Final offer/decision details presented | Map           | System                  | Varies    | Based on BRE output                       |
| `createdAt`       | Application creation timestamp      | Timestamp     | System                  | No        |                                           |
| `lastUpdatedAt`   | Last update timestamp               | Timestamp     | System                  | No        | Useful for drop-off detection             |
| `submittedAt`     | Final submission timestamp          | Timestamp     | System                  | No        | When user hits final submit               |
| `finalDecisionAt` | Timestamp of final Approve/Reject | Timestamp     | System                  | No        | When status becomes non-reviewable        |
| `personalInfo`    | (Reference Section 4.2)             | Map           | App/Firestore          | Yes       |                                           |
| `employmentDetails`| (Reference Section 4.3)             | Map           | App/Firestore          | Yes       |                                           |
| `documentIds`     | List of associated document IDs     | Array[String] | App/System             | No        | Link to `documents` collection            |

### 4.8. BRE Input Data (`applications.breInputData` map - Firestore)

*(Structure remains the same as v1.3, sample in `sample_metadata_Applications.txt` [cite: 3])*

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

### 4.9. BRE Output Data (`applications.breOutputData` map - Firestore)

*(Structure remains the same as v1.3, sample in `sample_metadata_Applications.txt` [cite: 3] shows `null` as it wasn't run)*

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

### 4.10. Loan Offer Data (`applications.loanOffer` map - Firestore)

*(Structure remains the same as v1.3, sample in `sample_metadata_Applications.txt` [cite: 3] shows `null` as it wasn't generated yet)*

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

### 4.11. Application Metadata (**Revised v1.4 - Subcollection & Cloud Functions Focus**)

* **Storage:** Stored within Firestore in the subcollection **`applications/{applicationId}/metadata/{eventId}`**. Each event is a separate document.
* **Schema Source:** Defined by `cloud-functions-guide.txt` [cite: 15, 28, 48, 68, 71] and exemplified in `sample_metadata_Applications.txt`[cite: 3].
* **Orchestration:** Critical events are sent to the Firebase Cloud Functions endpoint (`processEvent`). Cloud Functions manage storage into this subcollection and trigger drop-off logic based on event types (`APP_BACKGROUNDED`, `APP_FOREGROUNDED`) and scheduled checks (`dropOffChecker`).

**Fields (within each `metadata/{eventId}` document):**

| Field Name        | Description                          | Data Type | Source         | Sensitive | Notes                                        |
| :---------------- | :----------------------------------- | :-------- | :------------- | :-------- | :------------------------------------------- |
| `eventId`         | Unique ID for the metadata event     | String    | App/Cloud Func | No        | Document ID of the metadata record           |
| `applicationId`   | Parent Application ID                | String    | App            | No        | Explicitly stored field                      |
| `eventTimestamp`  | Timestamp of the event               | Timestamp | App/Cloud Func | No        | When the specific event occurred             |
| `eventType`       | Type of metadata event               | String    | App            | No        | Enum: MetadataEventType                      |
| `deviceInfo`      | Phone make, model, OS, app version   | Map       | App            | No        | Present in `APP_SESSION_START` event         |
| `appSession`      | App start/end times, duration, drop| Map       | App            | No        | Present in `APP_SESSION_START`/`END` events  |
| `screenVisit`     | Data for a single screen visit       | Map       | App            | No        | Present in `SCREEN_VISIT` event             |
| `sectionTiming`   | Data for section completion          | Map       | App            | No        | Present in `SECTION_COMPLETED` event       |
| `documentEvent`   | Details for a single doc action      | Map       | App            | Varies    | Present in `DOCUMENT_EVENT` event           |
| `verificationEvent`| Details for verification attempt     | Map       | App            | No        | Present in `VERIFICATION_EVENT` event       |
| `errorEvent`      | Details for a critical app error     | Map       | App            | No        | Present in `ERROR_OCCURRED` event           |
| `...`             | *Other event-specific data* | *Varies* | App            | *Varies* |                                              |

## 5. Appendix A: Enum Definitions (Revised v1.4)

*(Minor updates to ensure consistency)*

### Application Status (`applications.applicationStatus`)
```kotlin
enum class ApplicationStatus { CREATED, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, CANCELLED, EXPIRED, DROPPED_OFF }

Decision Status (BRE Output) (breOutputData.decisionStatus)
Kotlin

enum class DecisionStatus { AUTO_APPROVED, REJECTED, REFER }

Document Type (documents.documentType)
Kotlin

enum class DocumentType { PAN_CARD, BANK_STATEMENT, SALARY_SLIP, FORM_16, FORM_26AS, ID_CARD, ADDRESS_PROOF, PHOTO, OTHER }

Document Status (documents.documentStatus)
Kotlin

enum class DocumentStatus { PENDING_UPLOAD, UPLOADING, UPLOADED, PROCESSING, PROCESSED, VERIFICATION_PENDING, VERIFIED, REJECTED, ERROR }

Document Source Type (documents.documentSourceType)
Kotlin

enum class DocumentSourceType { CAMERA_IMAGE, IMAGE_UPLOAD, PDF_UPLOAD }

Extraction Status (documents.extractionStatus)
Kotlin

enum class ExtractionStatus { NOT_ATTEMPTED, SUCCESS, PARTIAL_SUCCESS, FAILURE }

Offer Status (User Interaction with Offer) (loanOffer.offerStatus)
Kotlin

enum class OfferStatus { GENERATED, PRESENTED, ACCEPTED, REJECTED, EXPIRED, MODIFIED }

Application Step (UI Flow Tracking) (applications.currentStep, applications.completedSteps[])
Kotlin

enum class ApplicationStep { LOGIN, PRIVACY_POLICY, HOME, PAN_ENTRY, PERSONAL_INFO, EMPLOYMENT_DETAILS, DOCUMENT_UPLOAD, LOAN_OFFER, EMPLOYMENT_VERIFICATION, KEY_FACT_SHEET, APPLICATION_SUBMITTED }

Gender (applications.personalInfo.gender)
Kotlin

enum class Gender { MALE, FEMALE, OTHER }

Marital Status (applications.personalInfo.maritalStatus)
Kotlin

enum class MaritalStatus { SINGLE, MARRIED, DIVORCED, WIDOWED }

Employment Type (applications.employmentDetails.employmentType)
Kotlin

enum class EmploymentType { PRIVATE_SECTOR, GOVERNMENT, SELF_EMPLOYED, BUSINESS_OWNER, RETIRED, OTHER }

Address Type (applications.*.address.addressType)
Kotlin

enum class AddressType { CURRENT, PERMANENT, OFFICE }

Verification Method (employment_verifications.verificationMethod)
Kotlin

enum class VerificationMethod { EMAIL_OTP, ID_CARD_SCAN, MANUAL }

Verification Status (employment_verifications.status)
Kotlin

enum class VerificationStatus { PENDING, SENT, FAILED_TO_SEND, VERIFIED, REJECTED, ERROR }

Metadata Event Type (metadata.eventType)
Kotlin

enum class MetadataEventType { APP_SESSION_START, APP_SESSION_END, APP_BACKGROUNDED, APP_FOREGROUNDE