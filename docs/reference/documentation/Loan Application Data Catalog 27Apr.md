# Loan Application Data Catalog

## Document Information

**Version:** 1.5.0
**Last Updated:** April 27, 2025
**Owner:** Loan Application Team

## Change Log

| Version | Date       | Author       | Description                                                                                                                                                                                                                                                        |
| :------ | :--------- | :----------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0.0   | 2025-04-14 | LoanSAI Team | Initial version based on initial analysis.                                                                                                                                                                                                                         |
| 1.1.0   | 2025-04-19 | LoanSAI Team | Incorporated Metadata concepts, UI changes.                                                                                                                                                                                                                      |
| 1.2.0   | 2025-04-19 | LoanSAI Team | Added revised flow (2nd BRE pass), updated Appwrite structure (2 collections), updated Document structure (documentSourceType, extractionStatus, conditional extractedData), added Bureau Confirmation & LLM fields, refined BRE I/O, detailed Metadata section.      |
| 1.3.0   | 2025-04-20 | Gemini       | Implemented Single-Pass BRE, removed Bureau Confirmation screen/flow & associated fields (bureauConfirmationInput, 2nd pass BRE fields). Updated Appwrite to 3 collections (enquiries added). Updated extractedData with specific fields per doc type. Set Metadata storage to subcollection. Updated effective date. Confirmed VerificationMethod enum. Added specific LLM models. Clarified skip logic. Integrated detailed BRE rules. |
| 1.4.0   | 2025-04-25 | Gemini       | Updated Appwrite Bureau Data schema (Sec 4.6). Updated Metadata Structure (Sec 4.11) to reflect Firebase Cloud Functions implementation and subcollection storage. Ensured consistency with single-pass BRE flow.                                                  |
| **1.5.0** | **2025-04-27** | **Gemini** | **Introduced Bureau Confirmation screen flow. Added `obligationRefinement` subcollection (Sec 4.12). Updated `documents.extractedData` with optional LLM-extracted fields (Sec 4.5). Revised data flow (Sec 3). Updated `applications` (Sec 4.7) and `breInputData` (Sec 4.8) to reflect potential recalculated obligation. Updated `ApplicationStep` enum (Appendix A). Aligned with v1.5 Requirements.** |

## Table of Contents

1.  Introduction
2.  Version Control Plan
3.  Data Relationships and Flow (Revised v1.5)
4.  Data Catalog - Detailed Fields
    4.1. User Authentication Data (`users` collection - Firestore)
    4.2. Personal Information (`applications.personalInfo` map - Firestore)
    4.3. Employment Details (`applications.employmentDetails` map - Firestore)
    4.4. Verification Data (`employment_verifications`, `otps` collections - Firestore)
    4.5. Document Data (`documents` collection - Firestore - **Revised v1.5**)
    4.6. Bureau Report Data (Appwrite - 3 Collections)
    4.7. Loan Application & Status Data (`applications` collection - Firestore - **Revised v1.5**)
    4.8. BRE Input Data (`applications.breInputData` map - Firestore - **Revised v1.5**)
    4.9. BRE Output Data (`applications.breOutputData` map - Firestore)
    4.10. Loan Offer Data (`applications.loanOffer` map - Firestore)
    4.11. Application Metadata (`applications/{appId}/metadata` subcollection)
    4.12. Obligation Refinement Data (`applications/{appId}/obligationRefinement` subcollection - **New v1.5**)
5.  Appendix A: Enum Definitions (**Revised v1.5**)

## 1. Introduction

This document provides the exhaustive definition of all data elements used within the **v1.5.0** Loan Application system. This version introduces the **Bureau Confirmation & Obligation Refinement** process and utilizes **backend LLM services** for document processing and obligation recalculation. It covers the Android application, Firestore database (including new subcollections), Appwrite database, Camunda workflow, Python BRE, Backend LLM API, and Firebase Cloud Functions Metadata Orchestrator. It serves as the single source of truth for data structure, types, sources, sensitivity, and purpose.

## 2. Version Control Plan

*(Standard version control practices apply. This document represents v1.5.0)*

## 3. Data Relationships and Flow (Revised v1.5)

A User authenticates via Firebase Auth (`users` collection). Starting a loan creates/updates an `applications` document. User data populates `personalInfo` and `employmentDetails`. Documents are uploaded to Firebase Storage, metadata stored in `documents`. **App triggers Backend LLM API for asynchronous document processing; results update `documents`.** App triggers Camunda for Bureau Fetch from Appwrite (`borrower_summary`, `enquiries`, `tradelines`). **If bureau score is valid (1-1000), user proceeds to Bureau Confirmation screen; input saved to `obligationRefinement` subcollection, triggering Backend LLM API for recalculation.** App compiles data (**potentially including recalculated obligation**) and triggers Camunda for BRE. Camunda calls Python BRE. BRE results (`breOutputData`) stored in `applications`. Final offer stored in `loanOffer`. Metadata events sent to Cloud Functions Orchestrator and stored in `metadata` subcollection. Verifications logged (`employment_verifications`, `otps`).

## 4. Data Catalog - Detailed Fields

### 4.1. User Authentication Data (`users` collection - Firestore)

| Field Name                | Description                     | Data Type | Source          | Sensitive | Notes                           |
| :------------------------ | :------------------------------ | :-------- | :-------------- | :-------- | :------------------------------ |
| `id`                      | Firebase Auth User ID           | String    | Firebase Auth   | No        | Document ID                     |
| `phoneNumber`             | User's phone number             | String    | User Input      | Yes       | Primary auth method (E.164)     |
| `email`                   | User's email address            | String    | User Input      | Yes       | Optional, potentially used for comms |
| `isPrivacyPolicyAccepted` | Consent flag for privacy policy | Boolean   | User Input      | No        | Set during OTP verification     |
| `privacyPolicyVersion`    | Version of accepted policy      | String    | System Config   | No        | e.g., "1.5"                     |
| `privacyPolicyAcceptedAt` | Timestamp of acceptance         | Timestamp | System          | No        | Server timestamp                |
| `createdAt`               | Account creation time           | Timestamp | Firebase Auth   | No        |                                 |
| `lastSignInAt`            | Last sign-in time               | Timestamp | System          | No        | Updated on successful sign-in   |
| `fcmTokens`               | FCM registration tokens map     | Map       | FCM SDK         | Maybe     | Key: Device ID, Value: FCM Token|

### 4.2. Personal Information (`applications.personalInfo` map - Firestore)

| Field Name               | Description                      | Data Type | Source           | Sensitive | Notes                           |
| :----------------------- | :------------------------------- | :-------- | :--------------- | :-------- | :------------------------------ |
| `name`                   | Full Name as per documents       | String    | User Input / OCR | Yes       |                                 |
| `dateOfBirth`            | Date of Birth                    | String    | User Input / OCR | Yes       | Format: "YYYY-MM-DD"            |
| `gender`                 | User's gender                    | String    | User Input       | No        | Enum: Gender                    |
| `panNumber`              | Permanent Account Number         | String    | User Input / OCR | Yes       | Displayed on Key Fact Sheet     |
| `mobileNumber`           | Registered Mobile Number         | String    | User Profile     | Yes       | Displayed on Key Fact Sheet     |
| `email`                  | Email Address                    | String    | User Input       | Yes       | Used for communication          |
| `maritalStatus`          | Marital Status                   | String    | User Input       | No        | Enum: MaritalStatus (Optional) |
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
| `employmentType`         | Type of Employment                | String    | User Input    | No        | Enum: EmploymentType                                             |
| `employerName`           | Name of Employer / Business       | String    | User Input    | Yes       |                                                                    |
| `monthlySalary`          | Gross Monthly Income              | Number    | User Input    | Yes       | Numeric value                                                      |
| `monthlyEmi`             | Declared Existing EMIs (Initial)  | Number    | User Input    | Yes       | User's initial declaration. May be overridden by `llmRecalculatedObligation`. |
| `workEmail`              | Official Work Email Address       | String    | User Input    | Yes       | Optional for 'GOVERNMENT'. Displayed on Key Fact Sheet.          |
| `designation`            | Job Title / Designation           | String    | User Input    | No        | Not applicable/removed for 'PRIVATE_SECTOR'. Required for 'GOVERNMENT'. |
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
| `verificationMethod`| Method used                      | String    | System/User Selection | No        | Enum: VerificationMethod         |
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

### 4.5. Document Data (`documents` collection - Firestore - **Revised v1.5**)

| Field Name           | Description                         | Data Type | Source                | Sensitive | Notes                                               |
| :------------------- | :---------------------------------- | :-------- | :-------------------- | :-------- | :-------------------------------------------------- |
| `id`                 | Unique Document ID                  | String    | System (UUID)         | No        | Document ID                                         |
| `applicationId`      | Associated Application ID           | String    | System                | No        | Link to `applications` collection                   |
| `userId`             | User ID                             | String    | System                | No        | Link to `users` collection                          |
| `documentType`       | Type of document                    | String    | User Selection        | No        | Enum: DocumentType                                  |
| `documentSourceType` | How the document was provided       | String    | System                | No        | Enum: DocumentSourceType                            |
| `documentStatus`     | Current status                      | String    | System                | No        | Enum: DocumentStatus                                |
| `fileName`           | Original file name                  | String    | User Upload/Sys       | No        |                                                     |
| `fileType`           | File extension (pdf, jpg, png)      | String    | System                | No        | Mime type might be better                           |
| `fileSize`           | File size in bytes                  | Number    | System                | No        |                                                     |
| `storageUrl`         | URL in Firebase Storage             | String    | System                | No        | gs:// or https:// URL                               |
| `uploadedAt`         | Timestamp of upload                 | Timestamp | System                | No        |                                                     |
| `processedAt`        | Timestamp processing finished       | Timestamp | Backend LLM API       | No        |                                                     |
| `extractionStatus`   | Status of data extraction by LLM    | String    | Backend LLM API       | No        | Enum: ExtractionStatus (SUCCESS, FAILURE, ...)    |
| `extractedData`      | Extracted key-value pairs (**Optional**) | Map    | Backend LLM API       | Varies    | Populated by LLM Service via Backend API. **All sub-fields are optional.** Structure depends on `documentType`. |
| `failureReason`      | Reason for PROCESSING/ERROR status  | String    | System/Manual         | No        |                                                     |
| `cameraMetadata`     | Camera details if source is camera  | Map       | App                   | No        | Optional: {resolution, ambientLight, cameraAngle}   |

**`extractedData` Sub-Fields (ALL OPTIONAL):**

* **If `documentType` == BANK_STATEMENT:**
    * `bankName` (String)
    * `accountNumber` (String)
    * `accountHolderName` (String)
    * `statementPeriodStart` (String: "YYYY-MM-DD")
    * `statementPeriodEnd` (String: "YYYY-MM-DD")
    * `openingBalance` (Number)
    * `closingBalance` (Number)
    * `averageBalance` (Number)
    * `totalCredits` (Number)
    * `totalDebits` (Number)
    * `transactionsCount` (Number)
* **If `documentType` == SALARY_SLIP:**
    * `employerNameOnSlip` (String)
    * `employeeNameOnSlip` (String)
    * `employeeIdOnSlip` (String)
    * `salaryMonth` (String)
    * `salaryYear` (Number)
    * `basicSalary` (Number)
    * `grossSalary` (Number)
    * `netSalary` (Number)
    * `totalDeductions` (Number)
* **If `documentType` == INCOME_TAX_RETURN:**
    * `itrType` (String)
    * `assessmentYear` (String)
    * `panOnItr` (String)
    * `nameOnItr` (String)
    * `incomeFromSalary` (Number)
    * `incomeBusiness` (Number)
    * `incomeOther` (Number)
    * `totalGrossIncome` (Number)
    * `totalDeductions` (Number)
    * `taxableIncome` (Number)
    * `taxPaid` (Number)
    * `verificationNumber` (String)
* **If `documentType` == FORM_26AS:**
    * `panOn26AS` (String)
    * `nameOn26AS` (String)
    * `assessmentYear26AS` (String)
    * `totalTdsAmount` (Number)
    * `totalTaxPaid` (Number)
* **If `documentType` == ID_CARD:**
    * `companyName` (String)
    * `employeeId` (String)
    * `employeeName` (String)
    * `designation` (String)
    * `department` (String)
    * `validity` (String: "YYYY-MM-DD")
* **If `documentType` == PAN_CARD:**
    * `panNumber` (String)
    * `name` (String)
    * `fatherName` (String)
    * `dateOfBirth` (String: "DD/MM/YYYY")

### 4.6. Bureau Report Data (Appwrite - 3 Collections)

* **Purpose:** Stores processed credit bureau data fetched externally. Accessed via Appwrite API (orchestrated by Camunda).
* **Schema Source:** Based on `Appwrite Bureau Data Attributes.txt` and `Bureau Report data schema.json`.

* **`borrower_summary` Collection (Appwrite)** (Document ID: PAN Number)
    * Stores aggregated bureau info.

    | Key                 | Type    | Notes                               |
    | :------------------ | :------ | :---------------------------------- |
    | `panNumber`         | string  | Primary identifier                |
    | `controlNumber`     | string  | CIBIL Control Number              |
    | `customerName`      | string  | Full Name                         |
    | `creditScore`       | integer | 300-900, null if NH               |
    | `reportDate`        | string  | Date string (e.g., "YYYY-MM-DD")  |
    | `dateOfBirth`       | string  | Date string (e.g., "YYYY-MM-DD")  |
    | `gender`            | string  | "Male", "Female", etc.            |
    | `addresses`         | string  | JSON string of addresses          |
    | `contacts`          | string  | JSON string of contacts           |
    | `email`             | string  | Email address                     |
    | `totalAccounts`     | integer | Total number of accounts reported |
    | `openAccounts`      | integer | Number of currently open accounts |
    | `closedAccounts`    | integer | Number of closed accounts         |
    | `totalLoanAmount`   | double  | Sum of sanctioned amounts/limits  |
    | `currentBalance`    | double  | Total outstanding balance         |
    | `totalOverdueAmount`| double  | Total amount currently overdue    |
    | `suitFiled`         | boolean | Flag if legal suit filed          |
    | `wilfulDefault`     | boolean | Flag if marked as wilful defaulter|
    | `writtenOffStatus`  | boolean | Flag if any account written off   |
    | `delinquencyStatus` | string  | Overall delinquency indicator     |

* **`enquiries` Collection (Appwrite)** (Document ID: Unique Enquiry ID)
    * Stores details of individual credit enquiries made.

    | Key           | Type   | Notes                                |
    | :------------ | :----- | :----------------------------------- |
    | `panNumber`   | string | Links to `borrower_summary`         |
    | `enquiryDate` | string | Date string (e.g., "YYYY-MM-DD")   |
    | `memberName`  | string | Name of institution making enquiry |
    | `purpose`     | string | Purpose of enquiry (e.g., "Loan")  |
    | `type`        | string | Type of enquiry                    |
    | `amount`      | double | Amount enquired for (if specified) |

* **`tradelines` Collection (Appwrite)** (Document ID: Unique Tradeline ID)
    * Stores details of individual loans/credit lines reported.

    | Key                   | Type    | Notes                                   |
    | :-------------------- | :------ | :-------------------------------------- |
    | `panNumber`           | string  | Links to `borrower_summary`            |
    | `memberName`          | string  | Name of lending institution           |
    | `accountType`         | string  | e.g., "Personal Loan", "Credit Card"  |
    | `accountNumber`       | string  | Loan/Card account number (masked)     |
    | `ownership`           | string  | e.g., "Individual", "Joint"           |
    | `creditLimit`         | double  | Credit limit (Cards) / Sanction Amt   |
    | `highCredit`          | double  | Highest utilized amount               |
    | `currentBalance`      | double  | Current outstanding balance           |
    | `amountOverdue`       | double  | Amount currently overdue              |
    | `rateOfInterest`      | double  | Interest rate (if reported)           |
    | `repaymentTenure`     | integer | Loan tenure in months (if applicable) |
    | `emiAmount`           | double  | EMI amount (if applicable)            |
    | `dateOpened`          | string  | Date string (e.g., "YYYY-MM-DD")      |
    | `dateClosed`          | string  | Date string (nullable)                |
    | `lastPaymentDate`     | string  | Date string (nullable)                |
    | `dateReported`        | string  | Date string (when this info reported) |
    | `facilityStatus`      | string  | e.g., "Active", "Closed", "Written Off"|
    | `suitFiled`           | string  | Suit filed status ("Yes"/"No"/Null)   |
    | `paymentFrequency`    | string  | e.g., "Monthly"                       |
    | `paymentHistory`      | string  | Encoded payment history string (DPD)  |
    | `writtenOffTotal`     | double  | Total amount written off (nullable)   |
    | `writtenOffPrincipal` | double  | Principal amount written off (nullable)|
    | `controlNumber`       | string  | CIBIL Control Number                  |

* **Indexes (Appwrite):** Define indexes on `panNumber` in `enquiries` and `tradelines` collections.

### 4.7. Loan Application & Status Data (`applications` collection - Firestore - **Revised v1.5**)

| Field Name          | Description                         | Data Type     | Source                       | Sensitive | Notes                                                                   |
| :------------------ | :---------------------------------- | :------------ | :--------------------------- | :-------- | :---------------------------------------------------------------------- |
| `id`                | Unique Application ID               | String        | System                       | No        | Document ID                                                             |
| `userId`            | Associated User ID                  | String        | System                       | No        | Link to `users`                                                         |
| `applicationStatus` | Overall status                      | String        | System/BRE/Orchestrator      | No        | Enum: ApplicationStatus (includes DROPPED_OFF)                          |
| `currentStep`       | Current step in the UI flow         | String        | App                          | No        | Enum: ApplicationStep (incl. BUREAU_CONFIRMATION)                     |
| `completedSteps`    | List of completed UI steps          | Array[String] | App                          | No        | Enum: ApplicationStep                                                 |
| `breInputData`      | Data sent to BRE                    | Map           | System                       | Varies    | Snapshot for audit/retry. May include `llmRecalculatedObligation`.    |
| `breOutputData`     | Result from BRE                     | Map           | BRE                          | Varies    | Includes decision, offer params, profileFlag                          |
| `loanOffer`         | Final offer/decision details presented | Map           | System                       | Varies    | Based on BRE output                                                     |
| `createdAt`         | Application creation timestamp      | Timestamp     | System                       | No        |                                                                         |
| `lastUpdatedAt`     | Last update timestamp               | Timestamp     | System                       | No        | Useful for drop-off detection                                           |
| `submittedAt`       | Final submission timestamp          | Timestamp     | System                       | No        | When user hits final submit                                             |
| `finalDecisionAt`   | Timestamp of final Approve/Reject | Timestamp     | System                       | No        | When status becomes non-reviewable                                    |
| `personalInfo`      | (Reference Section 4.2)             | Map           | App/Firestore               | Yes       |                                                                         |
| `employmentDetails` | (Reference Section 4.3)             | Map           | App/Firestore               | Yes       |                                                                         |
| `documentIds`       | List of associated document IDs     | Array[String] | App/Backend LLM API/System | No        | Link to `documents` collection                                          |
| `bureauFetchStatus` | Status of bureau data fetch         | String        | Camunda/System               | No        | e.g., PENDING, SUCCESS, FAILED (Optional)                               |
| `bureauScore`       | Cached bureau score                 | Number        | Appwrite/System              | Yes       | Used for Bureau Confirmation trigger (Optional caching)                   |

### 4.8. BRE Input Data (`applications.breInputData` map - Firestore - **Revised v1.5**)

| Field Name                 | Description                         | Data Type | Source                 | Sensitive | Notes                                                              |
| :------------------------- | :---------------------------------- | :-------- | :--------------------- | :-------- | :----------------------------------------------------------------- |
| `applicationId`            | Application ID                      | String    | System                 | No        | For correlation                                                    |
| `timestamp`                | When input was generated            | Timestamp | System                 | No        |                                                                    |
| `personalInfo`             | Relevant personal details           | Map       | App/Firestore          | Yes       | Age, CityTier, StateRisk, etc. derived from raw data             |
| `employmentDetails`        | Relevant employment details         | Map       | App/Firestore          | Yes       | Type, Income, EmployerRisk, IndustryRisk derived                     |
| `llmRecalculatedObligation`| Obligation from LLM (**Optional**)  | Number    | Backend LLM API / Firestore | Yes       | If present from `obligationRefinement`, use this for calculation. |
| `userDeclaredEmi`          | EMI from `employmentDetails`        | Number    | App/Firestore          | Yes       | Used if `llmRecalculatedObligation` is absent.                   |
| `bureauSummary`            | Key data from `borrower_summary`    | Map       | Appwrite/Firestore     | Yes       | Score, AgeOfCredit, Utilization, Delinquency flags etc.            |
| `documentInsights`         | Key data from `documents.extractedData`| Map       | Backend LLM API / Firestore | Yes    | Bank Bal Stability, Salary Credit Match, ITR data etc.             |

### 4.9. BRE Output Data (`applications.breOutputData` map - Firestore)

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

| Field Name                | Description                             | Data Type | Source      | Sensitive | Notes                                     |
| :------------------------ | :-------------------------------------- | :-------- | :---------- | :-------- | :---------------------------------------- |
| `offerStatus`             | Status relative to user interaction     | String    | System/User | No        | Enum: OfferStatus                         |
| `generatedAt`             | Timestamp offer was calculated by BRE   | Timestamp | System      | No        | Copied from `breOutputData.timestamp`     |
| `presentedAt`             | Timestamp offer was shown to user       | Timestamp | System      | No        |                                           |
| `acceptedAt`              | Timestamp user accepted the offer       | Timestamp | User        | No        |                                           |
| `selectedAmount`          | Amount selected/accepted by user        | Number    | User/System | No        | Can differ from `approvedAmount`          |
| `selectedTenor`           | Tenor selected/accepted by user         | Number    | User/System | No        | Can differ from `maxTenor`                |
| `interestRate`            | Final rate for selected offer           | Number    | BRE/System  | No        |                                           |
| `processingFeePercentage` | Final fee % for selected offer          | Number    | BRE/System  | No        |                                           |
| `processingFeeAmount`     | Calculated fee amount                   | Number    | System      | No        | `selectedAmount` * `processingFeePercentage` / 100 |
| `emiAmount`               | Calculated EMI for selected offer       | Number    | System      | No        | Calculated using PMT formula              |
| `decisionStatus`          | Copied from BRE Output for context      | String    | BRE/System  | No        |                                           |

### 4.11. Application Metadata (`applications/{appId}/metadata` subcollection)

* **Storage:** Firestore subcollection `applications/{applicationId}/metadata/{eventId}`.
* **Orchestration:** Critical events sent to Firebase Cloud Functions `processEvent`.
* **Fields (within each `metadata/{eventId}` document):**

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
    | `obligationEvent` | Details for Obligation Refinement  | Map       | App            | Yes       | Present when Bureau Confirmation submitted |
    | `llmInteraction`  | Details of a Backend LLM call      | Map       | Backend API    | No        | Present after LLM Document/Obligation call |

### 4.12. Obligation Refinement Data (`applications/{appId}/obligationRefinement` subcollection - **New v1.5**)

* **Purpose:** Stores user input from Bureau Confirmation screen and results from Backend LLM obligation recalculation.
* **Structure:** Each document represents one refinement attempt. Document ID (`recordId`) can be a UUID or timestamp-based identifier.

| Field Name                  | Description                                      | Data Type            | Source          | Sensitive | Notes                                                                 |
| :-------------------------- | :----------------------------------------------- | :------------------- | :-------------- | :-------- | :-------------------------------------------------------------------- |
| `recordId`                  | Unique ID for this refinement attempt            | String               | System (UUID)   | No        | Document ID                                                           |
| `createdAt`                 | Timestamp user submitted Bureau Conf. screen     | Timestamp            | App             | No        |                                                                       |
| `userProvidedEmis`          | Map of EMIs entered by user                      | Map<String, Number>  | User Input (App)| Yes       | Key: Tradeline ID/Identifier, Value: User-entered EMI amount        |
| `userComments`              | Comments provided by the user                    | String               | User Input (App)| Yes       | Explanations for discrepancies, closed loans etc.                   |
| `llmRecalculatedObligation` | Total monthly obligation recalculated by LLM     | Number               | Backend LLM API | Yes       | Nullable. Populated after successful LLM call.                      |
| `llmExcludedLoans`          | List of loans excluded by LLM during recalc      | List<Map<String, String>> | Backend LLM API | Yes       | Nullable. List of maps, each map: `{"tradelineId": "...", "reason": "..."}` |
| `llmProcessingStatus`       | Status of the backend LLM recalculation process  | String               | Backend LLM API | No        | e.g., PENDING, SUCCESS, FAILED                                        |
| `llmProcessedAt`            | Timestamp LLM recalculation finished             | Timestamp            | Backend LLM API | No        | Nullable                                                              |

## 5. Appendix A: Enum Definitions (**Revised v1.5**)

* **Application Status (`applications.applicationStatus`)**
    ```kotlin
    enum class ApplicationStatus { CREATED, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, CANCELLED, EXPIRED, DROPPED_OFF }
    ```
* **Decision Status (BRE Output) (`breOutputData.decisionStatus`)**
    ```kotlin
    enum class DecisionStatus { AUTO_APPROVED, REJECTED, REFER }
    ```
* **Document Type (`documents.documentType`)**
    ```kotlin
    enum class DocumentType { PAN_CARD, BANK_STATEMENT, SALARY_SLIP, INCOME_TAX_RETURN, FORM_26AS, ID_CARD, ADDRESS_PROOF, PHOTO, OTHER }
    ```
* **Document Status (`documents.documentStatus`)**
    ```kotlin
    // Represents the overall status including upload and processing
    enum class DocumentStatus { PENDING_UPLOAD, UPLOADING, UPLOADED, PROCESSING, PROCESSED, VERIFICATION_PENDING, VERIFIED, REJECTED, ERROR }
    ```
* **Document Source Type (`documents.documentSourceType`)**
    ```kotlin
    enum class DocumentSourceType { CAMERA_IMAGE, IMAGE_UPLOAD, PDF_UPLOAD, MANUAL_ENTRY }
    ```
* **Extraction Status (`documents.extractionStatus`)**
    ```kotlin
    // Represents the status of LLM data extraction
    enum class ExtractionStatus { NOT_ATTEMPTED, PENDING, SUCCESS, FAILURE }
    ```
* **Offer Status (User Interaction with Offer) (`loanOffer.offerStatus`)**
    ```kotlin
    enum class OfferStatus { GENERATED, PRESENTED, ACCEPTED, REJECTED, EXPIRED, MODIFIED }
    ```
* **Application Step (UI Flow Tracking) (`applications.currentStep`, `applications.completedSteps[]`)**
    ```kotlin
    enum class ApplicationStep {
        LOGIN, PRIVACY_POLICY, HOME, PAN_ENTRY, PERSONAL_INFO, EMPLOYMENT_DETAILS,
        DOCUMENT_UPLOAD, BUREAU_CONFIRMATION, LOAN_OFFER, EMPLOYMENT_VERIFICATION,
        KEY_FACT_SHEET, APPLICATION_SUBMITTED
    }
    ```
* **Gender (`applications.personalInfo.gender`)**
    ```kotlin
    enum class Gender { MALE, FEMALE, OTHER }
    ```
* **Marital Status (`applications.personalInfo.maritalStatus`)**
    ```kotlin
    enum class MaritalStatus { SINGLE, MARRIED, DIVORCED, WIDOWED, OTHER }
    ```
* **Employment Type (`applications.employmentDetails.employmentType`)**
    ```kotlin
    enum class EmploymentType { PRIVATE_SECTOR, GOVERNMENT, SELF_EMPLOYED, BUSINESS_OWNER, RETIRED, OTHER }
    ```
* **Address Type (`applications.*.address.addressType`)**
    ```kotlin
    enum class AddressType { CURRENT, PERMANENT, OFFICE }
    ```
* **Verification Method (`employment_verifications.verificationMethod`)**
    ```kotlin
    enum class VerificationMethod { EMAIL_OTP, ID_CARD_SCAN, MANUAL }
    ```
* **Verification Status (`employment_verifications.status`)**
    ```kotlin
    enum class VerificationStatus { PENDING, SENT, FAILED_TO_SEND, VERIFIED, REJECTED, ERROR }
    ```
* **Metadata Event Type (`metadata.eventType`)**
    ```kotlin
    enum class MetadataEventType {
        APP_SESSION_START, APP_SESSION_END, APP_BACKGROUNDED, APP_FOREGROUNDED,
        SCREEN_VISIT, SECTION_COMPLETED, DOCUMENT_EVENT, VERIFICATION_EVENT,
        OBLIGATION_REFINEMENT_SUBMITTED, LLM_CALL_INITIATED, LLM_CALL_COMPLETED,
        ERROR_OCCURRED, APPLICATION_SUBMITTED, APPLICATION_CANCELLED
    }
    ```
* **LLM Processing Status (`obligationRefinement.llmProcessingStatus`)**
    ```kotlin
    enum class LlmProcessingStatus { PENDING, SUCCESS, FAILED }
    ```