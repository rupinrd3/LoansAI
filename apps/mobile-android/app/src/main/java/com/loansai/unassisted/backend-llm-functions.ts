// backend-llm-functions.ts

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import axios from "axios";
import * as path from "path";
import * as fs from "fs";
import * as os from "os";

// Initialize Firebase Admin SDK
try {
  admin.initializeApp();
  console.log("Firebase Admin SDK initialized successfully.");
} catch (e) {
  console.error("Firebase Admin SDK initialization failed:", e);
}

// Get Firestore instance
const db = admin.firestore();

// Configuration constants
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-1.5-flash";
const TIMEOUT_MS = 30000; // 30 seconds timeout for API calls

/**
 * Helper function to validate request parameters
 */
function validateRequest(req: functions.https.Request, requiredParams: string[]): string | null {
  for (const param of requiredParams) {
    if (!req.body[param]) {
      return `Missing required parameter: ${param}`;
    }
  }
  return null;
}

/**
 * Cloud Function for document processing
 * Receives document content (OCR text or PDF base64), processes it with Gemini,
 * and stores the extracted data in Firestore
 */
export const processDocument = functions.https.onRequest(async (req, res) => {
  // Set CORS headers
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST");
  res.set("Access-Control-Allow-Headers", "Content-Type");
  
  // Handle preflight OPTIONS request
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  
  // Ensure method is POST
  if (req.method !== "POST") {
    res.status(405).send({ success: false, error: "Method not allowed" });
    return;
  }
  
  // Validate request
  const requiredParams = ["applicationId", "documentId", "documentType", "content", "sourceType"];
  const validationError = validateRequest(req, requiredParams);
  if (validationError) {
    res.status(400).send({ success: false, error: validationError });
    return;
  }
  
  const { 
    applicationId, 
    documentId, 
    documentType, 
    content, 
    sourceType, 
    userId,
    fileName,
    fileType,
    isBase64 = false
  } = req.body;
  
  console.log(`Processing document ${documentId} of type ${documentType} for application ${applicationId}`);
  
  try {
    // First, update document status to PROCESSING
    await db.collection("documents").doc(documentId).update({
      documentStatus: "PROCESSING",
      extractionStatus: "PENDING",
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    // Record start time for processing duration
    const startTime = Date.now();
    
    // Content to analyze - either raw text or base64 decoded content
    let textToAnalyze = content;
    let tmpFilePath = null;
    
    // If content is base64 encoded (for PDFs), we need to decode and extract text first
    if (isBase64 && fileType.toLowerCase() === "pdf") {
      try {
        // For PDFs, we might need to save file temporarily and use a PDF text extraction library
        // This is a placeholder for that functionality
        const buffer = Buffer.from(content, "base64");
        tmpFilePath = path.join(os.tmpdir(), `${documentId}.pdf`);
        fs.writeFileSync(tmpFilePath, buffer);
        
        // Here we would extract text from PDF using a library like pdf-parse
        // For now, we'll use a simplified approach for the example
        textToAnalyze = `Content from PDF file ${fileName}`;
        
        console.log(`Extracted text from PDF: ${textToAnalyze.substring(0, 100)}...`);
      } catch (pdfError) {
        console.error(`Error processing PDF: ${pdfError}`);
        // Update document with error status
        await db.collection("documents").doc(documentId).update({
          documentStatus: "ERROR",
          extractionStatus: "FAILURE",
          failureReason: `PDF processing error: ${pdfError.message}`,
          lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        res.status(500).send({ 
          success: false, 
          documentId,
          applicationId, 
          status: "FAILURE", 
          errorMessage: `PDF processing error: ${pdfError.message}`
        });
        return;
      } finally {
        // Clean up temp file if it was created
        if (tmpFilePath && fs.existsSync(tmpFilePath)) {
          fs.unlinkSync(tmpFilePath);
        }
      }
    }
    
    // Create prompt based on document type
    let prompt = "";
    switch (documentType.toUpperCase()) {
      case "BANK_STATEMENT":
        prompt = `
          You are a financial data extraction expert. Please analyze this bank statement text and extract the following information in JSON format:
          - bankName: The name of the bank
          - accountNumber: The account number (mask with asterisks if visible)
          - accountHolderName: The name of the account holder
          - statementPeriodStart: The start date of the statement period in YYYY-MM-DD format
          - statementPeriodEnd: The end date of the statement period in YYYY-MM-DD format
          - openingBalance: The opening balance
          - closingBalance: The closing balance
          - averageBalance: The average balance during the period
          - totalCredits: The total amount credited during the period
          - totalDebits: The total amount debited during the period
          - transactionsCount: The total number of transactions
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      case "SALARY_SLIP":
        prompt = `
          You are a payroll data extraction expert. Please analyze this salary slip text and extract the following information in JSON format:
          - employerNameOnSlip: The name of the employer
          - employeeNameOnSlip: The name of the employee
          - employeeIdOnSlip: The employee ID
          - salaryMonth: The month for which the salary is being paid
          - salaryYear: The year as a number
          - basicSalary: The basic salary amount
          - grossSalary: The gross salary amount
          - netSalary: The net salary amount
          - totalDeductions: The total deductions
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      case "INCOME_TAX_RETURN":
        prompt = `
          You are a tax document expert. Please analyze this Income Tax Return text and extract the following information in JSON format:
          - itrType: The type of ITR
          - assessmentYear: The assessment year
          - panOnItr: The PAN number in the ITR
          - nameOnItr: The name on the ITR
          - incomeFromSalary: Income from salary
          - incomeBusiness: Income from business
          - incomeOther: Income from other sources
          - totalGrossIncome: The total gross income
          - totalDeductions: The total deductions
          - taxableIncome: The final taxable income
          - taxPaid: The total tax paid
          - verificationNumber: The verification number if present
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      case "FORM_26AS":
        prompt = `
          You are a tax document expert. Please analyze this Form 26AS text and extract the following information in JSON format:
          - panOn26AS: The PAN number on Form 26AS
          - nameOn26AS: The name on Form 26AS
          - assessmentYear26AS: The assessment year
          - totalTdsAmount: The total TDS amount
          - totalTaxPaid: The total tax paid
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      case "ID_CARD":
        prompt = `
          You are an ID card data extraction expert. Please analyze this ID card text and extract the following information in JSON format:
          - companyName: The company name
          - employeeId: The employee ID
          - employeeName: The employee name
          - designation: The job designation
          - department: The department
          - validity: The validity date in YYYY-MM-DD format
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      case "PAN_CARD":
        prompt = `
          You are a document verification expert. Please analyze this PAN card text and extract the following information in JSON format:
          - panNumber: The PAN number
          - name: The name on the PAN card
          - fatherName: The father's name
          - dateOfBirth: The date of birth in DD/MM/YYYY format
          
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
        break;
        
      default:
        // Generic extraction for other document types
        prompt = `
          You are a document data extraction expert. Please analyze this document text and extract any relevant information in JSON format.
          Focus on key fields like name, dates, amounts, account numbers, and identifiers.
          If you cannot find a particular field, use null. Respond ONLY with valid JSON.
          
          TEXT TO ANALYZE:
          ${textToAnalyze}
        `;
    }
    
    // Call Gemini API for text extraction
    console.log(`Calling Gemini API for ${documentType} extraction...`);
    
    const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`;
    
    const response = await axios.post(
      apiUrl,
      {
        contents: [
          {
            parts: [
              {
                text: prompt
              }
            ]
          }
        ],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 800
        }
      },
      {
        timeout: TIMEOUT_MS,
        headers: {
          "Content-Type": "application/json"
        }
      }
    );
    
    // Check if we received a valid response
    if (!response.data.candidates || !response.data.candidates[0] || !response.data.candidates[0].content) {
      throw new Error("Invalid response from Gemini API");
    }
    
    // Get the generated text
    const generatedText = response.data.candidates[0].content.parts[0].text;
    
    // Calculate processing time
    const processingTimeMs = Date.now() - startTime;
    
    // Parse the response as JSON
    let extractedData = {};
    try {
      // Clean the response text - remove markdown code blocks if present
      const cleanedText = generatedText.replace(/```json|```/g, '').trim();
      extractedData = JSON.parse(cleanedText);
      console.log(`Successfully parsed extracted data: ${JSON.stringify(extractedData).substring(0, 200)}...`);
    } catch (jsonError) {
      console.error(`Error parsing JSON from Gemini response: ${jsonError}`);
      console.log(`Raw response: ${generatedText.substring(0, 500)}...`);
      
      // Update document with parsing error
      await db.collection("documents").doc(documentId).update({
        documentStatus: "PROCESSED",
        extractionStatus: "FAILURE",
        failureReason: `JSON parsing error: ${jsonError.message}`,
        lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        processedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      
      res.status(500).send({
        success: false,
        documentId,
        applicationId,
        status: "FAILURE",
        documentType,
        modelUsed: GEMINI_MODEL,
        processedAt: new Date().toISOString(),
        errorMessage: `JSON parsing error: ${jsonError.message}`,
        processingTimeMs
      });
      return;
    }
    
    // Update document in Firestore with extracted data
    await db.collection("documents").doc(documentId).update({
      documentStatus: "PROCESSED",
      extractionStatus: "SUCCESS",
      extractedData: extractedData,
      processedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    // Send success response
    res.status(200).send({
      success: true,
      documentId,
      applicationId,
      status: "SUCCESS",
      documentType,
      modelUsed: GEMINI_MODEL,
      processedAt: new Date().toISOString(),
      extractedData,
      processingTimeMs
    });
    
    console.log(`Document ${documentId} processed successfully in ${processingTimeMs}ms`);
    
  } catch (error) {
    console.error(`Error processing document ${documentId}:`, error);
    
    // Update document with error status
    try {
      await db.collection("documents").doc(documentId).update({
        documentStatus: "PROCESSED",
        extractionStatus: "FAILURE",
        failureReason: `Processing error: ${error.message}`,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
        lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } catch (updateError) {
      console.error(`Error updating document status: ${updateError}`);
    }
    
    // Send error response
    res.status(500).send({
      success: false,
      documentId,
      applicationId,
      status: "FAILURE",
      documentType,
      modelUsed: GEMINI_MODEL,
      processedAt: new Date().toISOString(),
      errorMessage: `Processing error: ${error.message}`,
      processingTimeMs: Date.now() - startTime
    });
  }
});

/**
 * Cloud Function for obligation recalculation
 * Analyzes user EMI inputs and tradeline data, then uses Gemini to 
 * calculate a refined monthly obligation amount
 */
export const recalculateObligation = functions.https.onRequest(async (req, res) => {
  // Set CORS headers
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST");
  res.set("Access-Control-Allow-Headers", "Content-Type");
  
  // Handle preflight OPTIONS request
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  
  // Ensure method is POST
  if (req.method !== "POST") {
    res.status(405).send({ success: false, error: "Method not allowed" });
    return;
  }
  
  // Validate request
  const requiredParams = ["applicationId", "obligationRefinementId"];
  const validationError = validateRequest(req, requiredParams);
  if (validationError) {
    res.status(400).send({ success: false, error: validationError });
    return;
  }
  
  const { 
    applicationId, 
    obligationRefinementId, 
    userId,
    fetchDataFromFirestore = true,
    tradelines = null,
    userProvidedEmis = null,
    userComments = null,
    preferredModel = null
  } = req.body;
  
  console.log(`Recalculating obligation for application ${applicationId}, refinement record ${obligationRefinementId}`);
  
  // Record start time for processing duration
  const startTime = Date.now();
  const modelToUse = preferredModel || GEMINI_MODEL;
  
  try {
    // First, update obligation refinement status to PENDING
    await db.collection("applications")
      .doc(applicationId)
      .collection("obligationRefinement")
      .doc(obligationRefinementId)
      .update({
        llmProcessingStatus: "PENDING",
        lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    
    // Data to be analyzed
    let tradelineData = tradelines;
    let userEmis = userProvidedEmis;
    let comments = userComments;
    
    // If data should be fetched from Firestore
    if (fetchDataFromFirestore) {
      console.log("Fetching data from Firestore...");
      
      // Get the obligation refinement record
      const refinementDoc = await db.collection("applications")
        .doc(applicationId)
        .collection("obligationRefinement")
        .doc(obligationRefinementId)
        .get();
      
      if (!refinementDoc.exists) {
        throw new Error(`Obligation refinement record ${obligationRefinementId} not found`);
      }
      
      const refinementData = refinementDoc.data();
      userEmis = refinementData?.userProvidedEmis || {};
      comments = refinementData?.userComments || "";
      
      // Fetch bureau data from application document
      const appDoc = await db.collection("applications").doc(applicationId).get();
      if (!appDoc.exists) {
        throw new Error(`Application ${applicationId} not found`);
      }
      
      // Retrieve bureau data (tradelines)
      // In a real implementation, this might come from Appwrite or another source
      // For this example, we'll assume the data is stored in the application document
      const appData = appDoc.data();
      if (appData?.bureauData?.tradelines) {
        tradelineData = appData.bureauData.tradelines;
      } else {
        console.log("No tradeline data found in application document, checking Appwrite...");
        // Here you would implement Appwrite API call to get tradelines
        // For this example, we'll use a placeholder
        tradelineData = {};
      }
    }
    
    // Validate that we have the necessary data
    if (!tradelineData || Object.keys(tradelineData).length === 0) {
      throw new Error("No tradeline data available for recalculation");
    }
    
    if (!userEmis || Object.keys(userEmis).length === 0) {
      throw new Error("No user-provided EMIs available for recalculation");
    }
    
    // Prepare the data for Gemini
    const tradelineSummary = Object.entries(tradelineData).map(([id, data]) => {
      const tradeline = data as any;
      return {
        id,
        lender: tradeline.memberName || "Unknown Lender",
        accountType: tradeline.accountType || "Unknown Account Type",
        accountNumber: tradeline.accountNumber || "Unknown Account Number",
        facilityStatus: tradeline.facilityStatus || "Unknown Status",
        currentBalance: tradeline.currentBalance || 0,
        creditLimit: tradeline.creditLimit || 0,
        emiAmount: tradeline.emiAmount || 0,
        dateOpened: tradeline.dateOpened || "Unknown",
        dateClosed: tradeline.dateClosed || null,
        userProvidedEmi: userEmis[id] || 0
      };
    });
    
    // Create prompt for Gemini
    const prompt = `
      You are a financial analysis expert. Please analyze the following tradeline data and user-provided EMI values 
      to calculate a final monthly obligation amount.
      
      TRADELINE DATA:
      ${JSON.stringify(tradelineSummary, null, 2)}
      
      USER COMMENTS:
      ${comments || "No comments provided"}
      
      Your task is to:
      1. Analyze each tradeline and compare the bureau-reported EMI with the user-provided EMI
      2. Identify which loans might be closed, fully paid, or temporarily inactive based on the user's input and comments
      3. Decide which loans should be included in the monthly obligation calculation
      4. Calculate the final monthly obligation amount by summing the appropriate EMIs
      5. Provide a list of loans that you excluded from the calculation and why
      
      Important rules:
      - If a user sets an EMI to 0, it likely means the loan is paid off or inactive
      - If dateOpened is recent and facilityStatus is "Active", the loan is likely still active
      - If the user mentioned specific loans in their comments, honor their explanation if reasonable
      - For active loans, use the user-provided EMI value when calculating the total obligation
      - For loans that appear to be closed or inactive, exclude them from the calculation
      
      Respond with VALID JSON in the following format:
      {
        "recalculatedObligation": number,   // The final monthly obligation amount in rupees (integer)
        "excludedLoans": [                  // Array of loans excluded from calculation
          {
            "tradelineId": "string",        // The ID of the tradeline
            "reason": "string"              // Reason for exclusion
          }
        ]
      }
    `;
    
    // Call Gemini API for recalculation
    console.log("Calling Gemini API for obligation recalculation...");
    
    const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelToUse}:generateContent?key=${GEMINI_API_KEY}`;
    
    const response = await axios.post(
      apiUrl,
      {
        contents: [
          {
            parts: [
              {
                text: prompt
              }
            ]
          }
        ],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 800
        }
      },
      {
        timeout: TIMEOUT_MS,
        headers: {
          "Content-Type": "application/json"
        }
      }
    );
    
    // Check if we received a valid response
    if (!response.data.candidates || !response.data.candidates[0] || !response.data.candidates[0].content) {
      throw new Error("Invalid response from Gemini API");
    }
    
    // Get the generated text
    const generatedText = response.data.candidates[0].content.parts[0].text;
    
    // Calculate processing time
    const processingTimeMs = Date.now() - startTime;
    
    // Parse the response as JSON
    let recalculationResult;
    try {
      // Clean the response text - remove markdown code blocks if present
      const cleanedText = generatedText.replace(/```json|```/g, '').trim();
      recalculationResult = JSON.parse(cleanedText);
      console.log(`Successfully parsed recalculation result: ${JSON.stringify(recalculationResult).substring(0, 200)}...`);
    } catch (jsonError) {
      console.error(`Error parsing JSON from Gemini response: ${jsonError}`);
      console.log(`Raw response: ${generatedText.substring(0, 500)}...`);
      
      // Update obligation refinement with parsing error
      await db.collection("applications")
        .doc(applicationId)
        .collection("obligationRefinement")
        .doc(obligationRefinementId)
        .update({
          llmProcessingStatus: "FAILED",
          llmProcessedAt: admin.firestore.FieldValue.serverTimestamp(),
          failureReason: `JSON parsing error: ${jsonError.message}`,
          lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
      
      res.status(500).send({
        success: false,
        applicationId,
        obligationRefinementId,
        status: "FAILURE",
        modelUsed: modelToUse,
        processedAt: new Date().toISOString(),
        errorMessage: `JSON parsing error: ${jsonError.message}`,
        processingTimeMs
      });
      return;
    }
    
    // Validate the result
    if (typeof recalculationResult.recalculatedObligation !== "number") {
      throw new Error("Invalid recalculation result: missing or invalid recalculatedObligation");
    }
    
    // Make sure recalculatedObligation is an integer
    const recalculatedObligation = Math.round(recalculationResult.recalculatedObligation);
    const excludedLoans = recalculationResult.excludedLoans || [];
    
    // Update obligation refinement in Firestore with recalculation result
    await db.collection("applications")
      .doc(applicationId)
      .collection("obligationRefinement")
      .doc(obligationRefinementId)
      .update({
        llmRecalculatedObligation: recalculatedObligation,
        llmExcludedLoans: excludedLoans,
        llmProcessingStatus: "SUCCESS",
        llmProcessedAt: admin.firestore.FieldValue.serverTimestamp(),
        lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    
    // Send success response
    res.status(200).send({
      success: true,
      applicationId,
      obligationRefinementId,
      status: "SUCCESS",
      modelUsed: modelToUse,
      processedAt: new Date().toISOString(),
      recalculatedObligation,
      excludedLoans,
      processingTimeMs
    });
    
    console.log(`Obligation recalculation for ${obligationRefinementId} completed successfully in ${processingTimeMs}ms`);
    
  } catch (error) {
    console.error(`Error recalculating obligation for ${obligationRefinementId}:`, error);
    
    // Update obligation refinement with error status
    try {
      await db.collection("applications")
        .doc(applicationId)
        .collection("obligationRefinement")
        .doc(obligationRefinementId)
        .update({
          llmProcessingStatus: "FAILED",
          llmProcessedAt: admin.firestore.FieldValue.serverTimestamp(),
          failureReason: `Processing error: ${error.message}`,
          lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
    } catch (updateError) {
      console.error(`Error updating obligation refinement status: ${updateError}`);
    }
    
    // Send error response
    res.status(500).send({
      success: false,
      applicationId,
      obligationRefinementId,
      status: "FAILURE",
      modelUsed: modelToUse,
      processedAt: new Date().toISOString(),
      errorMessage: `Processing error: ${error.message}`,
      processingTimeMs: Date.now() - startTime
    });
  }
});