// upload-report.js
// Idempotent upload script: uses stable IDs to upsert documents
const fs       = require('fs');
const sdk      = require('node-appwrite');
const readline = require('readline');
require('dotenv').config();

const client = new sdk.Client()
  .setEndpoint(process.env.APPWRITE_ENDPOINT)    // e.g. https://cloud.appwrite.io/v1
  .setProject (process.env.APPWRITE_PROJECT_ID)  // your project ID
  .setKey     (process.env.APPWRITE_API_KEY);    // your API key

const db    = new sdk.Databases(client);
const DB_ID = '67fb572700006c9fc191';

// Keys defined in each collection:
const SUMMARY_KEYS = [
  'panNumber','controlNumber','customerName','creditScore','reportDate',
  'dateOfBirth','gender','addresses','contacts','email','totalAccounts',
  'openAccounts','closedAccounts','totalLoanAmount','currentBalance',
  'totalOverdueAmount','suitFiled','wilfulDefault','writtenOffStatus',
  'delinquencyStatus'
];

const TRADELINE_KEYS = [
  'panNumber','controlNumber','memberName','accountType','accountNumber',
  'ownership','creditLimit','highCredit','currentBalance','amountOverdue',
  'rateOfInterest','repaymentTenure','emiAmount','dateOpened','dateClosed',
  'lastPaymentDate','dateReported','facilityStatus','suitFiled',
  'paymentFrequency','paymentHistory','writtenOffTotal','writtenOffPrincipal'
];

const ENQUIRY_KEYS = [
  'panNumber','enquiryDate','memberName','purpose','type','amount'
];

const rl = readline.createInterface({
  input:  process.stdin,
  output: process.stdout
});

rl.question('Enter the path to your JSON file: ', async (path) => {
  rl.close();

  // 1) Read & parse
  let raw;
  try {
    raw = fs.readFileSync(path.trim(), 'utf8');
  } catch (e) {
    console.error(`❌ Cannot read file:`, e.message);
    return process.exit(1);
  }
  let data;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    console.error(`❌ Invalid JSON:`, e.message);
    return process.exit(1);
  }

  // 2) Build borrower_summary payload
  const bs = data.borrower_summary || {};
  bs.addresses = JSON.stringify(bs.addresses || []);
  bs.contacts  = JSON.stringify(bs.contacts  || []);

  const summary = {};
  SUMMARY_KEYS.forEach(k => {
    if (bs[k] !== undefined) summary[k] = bs[k];
  });

  const pan = summary.panNumber;
  // Upsert borrower_summary using panNumber as document ID
  try {
    await db.createDocument(DB_ID, 'borrower_summary', pan, summary);
  } catch (err) {
    if (err.code === 409) {
      await db.updateDocument(DB_ID, 'borrower_summary', pan, summary);
    } else {
      console.error('❌ Failed to upsert borrower_summary:', err);
      return process.exit(1);
    }
  }

  // 3) Tradelines: map & whitelist each record, then upsert
  for (const rawTL of data.tradelines || []) {
    const tl = {
      ...rawTL,
      lastPaymentDate: rawTL.dateLastPayment,
      dateReported:   rawTL.dateReportedAndCertified,
      paymentHistory: JSON.stringify(rawTL.paymentHistory || [])
    };

    const clean = { panNumber: pan };
    TRADELINE_KEYS.forEach(k => {
      if (tl[k] !== undefined) clean[k] = tl[k];
    });

    // Use stable ID: "<PAN>_<AccountNumber>"
    const docId = `${clean.panNumber}_${clean.accountNumber}`;
    try {
      await db.createDocument(DB_ID, 'tradelines', docId, clean);
    } catch (err) {
      if (err.code === 409) {
        await db.updateDocument(DB_ID, 'tradelines', docId, clean);
      } else {
        console.error('❌ Failed to upsert tradeline:', err);
        return process.exit(1);
      }
    }
  }

  // 4) Enquiries: map & whitelist each record, then upsert
  for (const rawEQ of data.enquiries || []) {
    const eq = {
      ...rawEQ,
      panNumber: pan
    };

    const clean = {};
    ENQUIRY_KEYS.forEach(k => {
      if (eq[k] !== undefined) clean[k] = eq[k];
    });

    // Use stable ID: "<PAN>_<EnquiryDate>"
    const docId = `${clean.panNumber}_${clean.enquiryDate}`;
    try {
      await db.createDocument(DB_ID, 'enquiries', docId, clean);
    } catch (err) {
      if (err.code === 409) {
        await db.updateDocument(DB_ID, 'enquiries', docId, clean);
      } else {
        console.error('❌ Failed to upsert enquiry:', err);
        return process.exit(1);
      }
    }
  }

  console.log('✅ All documents upserted successfully!');
});
