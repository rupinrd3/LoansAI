# Appwrite Database Setup Instructions

## Overview

The Android app requires three Appwrite collections to be set up for proper bureau report functionality:
- `borrower_summary`
- `enquiries`
- `tradelines`

Since the Android client SDK cannot perform administrative operations (like creating collections), these must be set up using either:
1. Appwrite Console (Manual)
2. Server-side script with Appwrite Admin SDK (Automated)

## Method 1: Using Appwrite Console (Manual)

1. Log in to your Appwrite Console at https://cloud.appwrite.io
2. Navigate to your project: `project-fra-67fb549a0036c841fb32`
3. Go to the Database section
4. Select database: `database-67fb572700006c9fc191`
5. Create collections manually with the following specifications:

### Borrower Summary Collection
- Collection ID: `borrower_summary`
- Name: `Borrower Summary`
- Create the following attributes (select "Attributes" tab and click "Create attribute"):

| Attribute | Type | Size | Required | Min | Max | Default |
|-----------|------|------|----------|-----|-----|---------|
| panNumber | String | 10 | Yes | - | - | - |
| controlNumber | String | 50 | Yes | - | - | - |
| customerName | String | 100 | No | - | - | - |
| creditScore | Integer | - | No | 300 | 900 | - |
| reportDate | String | 30 | No | - | - | - |
| dateOfBirth | String | 30 | No | - | - | - |
| gender | String | 10 | No | - | - | - |
| addresses | String | 65535 | No | - | - | - |
| contacts | String | 65535 | No | - | - | - |
| email | String | 255 | No | - | - | - |
| totalAccounts | Integer | - | No | 0 | - | - |
| openAccounts | Integer | - | No | 0 | - | - |
| closedAccounts | Integer | - | No | 0 | - | - |
| totalLoanAmount | Float | - | No | 0 | - | - |
| currentBalance | Float | - | No | 0 | - | - |
| totalOverdueAmount | Float | - | No | 0 | - | - |
| suitFiled | Boolean | - | No | - | - | false |
| wilfulDefault | Boolean | - | No | - | - | false |
| writtenOffStatus | Boolean | - | No | - | - | false |
| delinquencyStatus | String | 50 | No | - | - | - |

Create index:
- Name: `pan_index`
- Type: `key`
- Attributes: `panNumber`

### Enquiries Collection
(Similar process for `enquiries` collection - refer to script for attributes)

### Tradelines Collection
(Similar process for `tradelines` collection - refer to script for attributes)

## Method 2: Using Server-side Script (Automated)

1. Create a new Node.js project:
```bash
mkdir appwrite-setup
cd appwrite-setup
npm init -y
```

2. Install Appwrite Server SDK:
```bash
npm install node-appwrite dotenv
```

3. Create `.env` file with your API credentials:
```env
APPWRITE_API_KEY=your_api_key_here
```

4. Copy the `setup-appwrite-collections.js` script to your project

5. Run the script:
```bash
node setup-appwrite-collections.js
```

## Getting the API Key

1. Go to your Appwrite Console
2. Navigate to your project
3. Go to Settings > API Keys
4. Generate a new API key with the following scopes:
   - Database: All
   - Collections: All
   - Attributes: All
   - Indexes: All

## Important Notes

- Never include the API key in your Android app code
- The Android app should only perform client-side operations (read/write documents)
- All administrative operations (creating collections, attributes, indexes) must be done through the console or server-side scripts
- Make sure collections are created before deploying the Android app to production

## Expected Behavior

After setup, your Android app will:
1. Attempt to fetch bureau reports from these collections
2. Fall back to Firebase Firestore if Appwrite collections are not available
3. Generate dummy reports if no data is found in either system

## Troubleshooting

If you encounter errors:
1. Verify all collection IDs match exactly
2. Ensure all attributes are created with correct types and sizes
3. Check that indexes are created properly
4. Confirm the database ID is correct
5. Make sure your API key has the required permissions