/**
 * setup-appwrite-collections.js
 *
 * 1. Loads .env
 * 2. Trims & validates the 4 required vars
 * 3. Verifies the project + key by fetching the project metadata (COMMENTED OUT)
 * 4. Creates borrower_summary, enquiries & tradelines collections
 * (and all their attributes + indexes)
 */

const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '.env') });

// Print API Key if required
// console.log('→ Raw API key:', JSON.stringify(process.env.APPWRITE_API_KEY));


// ─────────────────────────────────────────────────────────────────────────────
// 1) Read & trim env vars
const endpoint   = process.env.APPWRITE_ENDPOINT?.trim();
const projectId  = process.env.APPWRITE_PROJECT_ID?.trim();
const apiKey     = process.env.APPWRITE_API_KEY?.trim();
const databaseId = process.env.APPWRITE_DATABASE_ID?.trim();

// 2) Validate
if (!endpoint || !projectId || !apiKey || !databaseId) {
  console.error(`
❌ Missing one or more required environment variables!
   • APPWRITE_ENDPOINT   = ${endpoint}
   • APPWRITE_PROJECT_ID = ${projectId}
   • APPWRITE_API_KEY    = ${apiKey ? 'present' : 'MISSING'}
   • APPWRITE_DATABASE_ID= ${databaseId}

Make sure you have a file named ".env" next to this script containing lines exactly like:

  APPWRITE_ENDPOINT=https://cloud.appwrite.io/v1
  APPWRITE_PROJECT_ID=your-project-id
  APPWRITE_API_KEY=your-secret-api-key
  APPWRITE_DATABASE_ID=your-database-id
  `);
  process.exit(1);
}

// 3) Initialize Appwrite SDK
const { Client, Databases } = require('node-appwrite');
const client    = new Client()
  .setEndpoint(endpoint)    // e.g. https://cloud.appwrite.io/v1 or regional endpoint
  .setProject(projectId)    // your Project ID
  .setKey(apiKey);          // your secret API key
const databases = new Databases(client);

// 4) Helper to run each step safely
async function safe(fn, desc) {
  try {
    await fn();
    console.log(`✅ ${desc}`);
  } catch (err) {
    console.error(`❌ ${desc} — ${err.message}`);
    // Optionally add more error details if needed:
    // console.error(`   Code: ${err.code}, Type: ${err.type}`);
    // console.error(err); // Full error object
  }
}

// 5) Test that project & key actually work (COMMENTED OUT)
/*
async function verifyProject() {
  console.log('🔹 Verifying project access…');
  try {
    // This call requires the 'projects.read' scope which might not be available/intended for API keys
    const project = await client.call('get', `/projects/${projectId}`);
    console.log(`   👍 Success! Project name: ${project.name}`);
  } catch (err) {
    console.error('   👎 Failed to fetch project metadata:', err.message);
    console.error('   → Check that your API key is valid and scoped to this project.');
    process.exit(1);
  }
}
*/

// 6) Collection & attribute/index setup functions

async function setupBorrowerSummary() {
  const cid = 'borrower_summary';
  await safe(
    () => databases.createCollection(databaseId, cid, 'Borrower Summary', [], false),
    'Create collection borrower_summary'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'panNumber',        10,   true),
    'Add panNumber (String,10,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'controlNumber',    50,   true),
    'Add controlNumber (String,50,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'customerName',     100,  false),
    'Add customerName (String,100)'
  );
  await safe(
    () => databases.createIntegerAttribute(databaseId, cid, 'creditScore', false, 300, 900),
    'Add creditScore (Integer,300–900)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'reportDate',       30,   false),
    'Add reportDate (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'dateOfBirth',      30,   false),
    'Add dateOfBirth (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'gender',            10,   false),
    'Add gender (String,10)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'addresses',      65535, false),
    'Add addresses (String,65535)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'contacts',       65535, false),
    'Add contacts (String,65535)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'email',            255,  false),
    'Add email (String,255)'
  );
  await safe(
    () => databases.createIntegerAttribute(databaseId, cid, 'totalAccounts', false),
    'Add totalAccounts (Integer)'
  );
  await safe(
    () => databases.createIntegerAttribute(databaseId, cid, 'openAccounts',  false),
    'Add openAccounts (Integer)'
  );
  await safe(
    () => databases.createIntegerAttribute(databaseId, cid, 'closedAccounts',false),
    'Add closedAccounts (Integer)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'totalLoanAmount',   false, 0, null),
    'Add totalLoanAmount (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'currentBalance',    false, 0, null),
    'Add currentBalance (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'totalOverdueAmount',false, 0, null),
    'Add totalOverdueAmount (Float)'
  );
  await safe(
    () => databases.createBooleanAttribute(databaseId, cid, 'suitFiled',       false, false),
    'Add suitFiled (Boolean)'
  );
  await safe(
    () => databases.createBooleanAttribute(databaseId, cid, 'wilfulDefault',   false, false),
    'Add wilfulDefault (Boolean)'
  );
  await safe(
    () => databases.createBooleanAttribute(databaseId, cid, 'writtenOffStatus',false, false),
    'Add writtenOffStatus (Boolean)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'delinquencyStatus',50, false),
    'Add delinquencyStatus (String,50)'
  );
  await safe(
    () => databases.createIndex(databaseId, cid, 'pan_index', 'key', ['panNumber']),
    'Create pan_index on borrower_summary'
  );
}

async function setupEnquiries() {
  const cid = 'enquiries';
  await safe(
    () => databases.createCollection(databaseId, cid, 'Enquiries', [], false),
    'Create collection enquiries'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'borrowerId', 36, true),
    'Add borrowerId (String,36,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'panNumber',  10, true),
    'Add panNumber (String,10,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'enquiryDate', 30, true),
    'Add enquiryDate (String,30,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'memberName', 100, false),
    'Add memberName (String,100)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'purpose',    100, false),
    'Add purpose (String,100)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'type',       50, false),
    'Add type (String,50)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'amount', false, 0, null),
    'Add amount (Float)'
  );
  await safe(
    () => databases.createIndex(databaseId, cid, 'borrower_id_index', 'key', ['borrowerId']),
    'Create borrower_id_index on enquiries'
  );
  await safe(
    () => databases.createIndex(databaseId, cid, 'pan_index',         'key', ['panNumber']),
    'Create pan_index on enquiries'
  );
}

async function setupTradelines() {
  const cid = 'tradelines';
  await safe(
    () => databases.createCollection(databaseId, cid, 'Tradelines', [], false),
    'Create collection tradelines'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'borrowerId', 36, true),
    'Add borrowerId (String,36,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'panNumber',  10, true),
    'Add panNumber (String,10,required)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'memberName', 100, false),
    'Add memberName (String,100)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'accountType', 50, false),
    'Add accountType (String,50)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'accountNumber', 50, false),
    'Add accountNumber (String,50)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'ownership',    50, false),
    'Add ownership (String,50)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'creditLimit', false, 0, null),
    'Add creditLimit (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'highCredit', false, 0, null),
    'Add highCredit (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'currentBalance', false, 0, null),
    'Add currentBalance (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'amountOverdue', false, 0, null),
    'Add amountOverdue (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'rateOfInterest', false, 0, 100),
    'Add rateOfInterest (Float,0–100)'
  );
  await safe(
    () => databases.createIntegerAttribute(databaseId, cid, 'repaymentTenure', false),
    'Add repaymentTenure (Integer)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'emiAmount', false, 0, null),
    'Add emiAmount (Float)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'dateOpened',      30, false),
    'Add dateOpened (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'dateClosed',      30, false),
    'Add dateClosed (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'lastPaymentDate', 30, false),
    'Add lastPaymentDate (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'dateReported',    30, false),
    'Add dateReported (String,30)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'facilityStatus',  50, false),
    'Add facilityStatus (String,50)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'suitFiled',       10, false),
    'Add suitFiled (String,10)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'paymentFrequency',50, false),
    'Add paymentFrequency (String,50)'
  );
  await safe(
    () => databases.createStringAttribute(databaseId, cid, 'paymentHistory',65535, false),
    'Add paymentHistory (String,65535)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'writtenOffTotal', false, 0, null),
    'Add writtenOffTotal (Float)'
  );
  await safe(
    () => databases.createFloatAttribute(databaseId, cid, 'writtenOffPrincipal', false, 0, null),
    'Add writtenOffPrincipal (Float)'
  );
  await safe(
    () => databases.createIndex(databaseId, cid, 'borrower_id_index', 'key', ['borrowerId']),
    'Create borrower_id_index on tradelines'
  );
  await safe(
    () => databases.createIndex(databaseId, cid, 'pan_index',         'key', ['panNumber']),
    'Create pan_index on tradelines'
  );
}

// ─────────────────────────────────────────────────────────────────────────────

(async function main() {
  console.log('🚀 Starting Appwrite collections setup…\n');
  // await verifyProject(); // Commented out as 'projects.read' scope might not be available/needed
  await setupBorrowerSummary();
  await setupEnquiries();
  await setupTradelines();
  console.log('\n🎉 All done!');
})();