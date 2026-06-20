"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.dropOffChecker = exports.processEvent = exports.sendOtp = void 0;
const logger = __importStar(require("firebase-functions/logger"));
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
// No SendGrid import needed
const axios_1 = __importDefault(require("axios")); // Import axios
const https_1 = require("firebase-functions/v1/https");
// WriteResult import removed as it was unused
// --- Initialization ---
try {
    admin.initializeApp();
    logger.info("Firebase Admin SDK initialized successfully.");
}
catch (e) {
    logger.error("Firebase Admin SDK initialization failed:", e);
}
const db = admin.firestore();
// --- Configuration Access ---
const getConfigValue = (keys) => {
    let current = functions.config();
    for (const key of keys) {
        if (current === undefined || current === null)
            return undefined;
        current = current[key];
    }
    return typeof current === 'string' ? current : undefined;
};
// --- Brevo Configuration (Used for BOTH OTP and Alerts) ---
const BREVO_API_KEY = getConfigValue(["brevo", "key"]);
const BREVO_FROM_EMAIL = getConfigValue(["brevo", "from"]) || "noreply@example.com"; // Used as the sender for all emails
const BREVO_ALERT_TO_EMAIL = getConfigValue(["brevo", "to"]) || "support@example.com"; // Destination for alert emails
if (!BREVO_API_KEY) {
    logger.warn("Brevo API Key (brevo.key) is not set. ALL email sending will fail." +
        " Run: firebase functions:config:set brevo.key=\"YOUR_KEY\" brevo.from=\"verified@example.com\" brevo.to=\"support@example.com\"");
}
else {
    logger.info("Brevo API Key configured for email sending.");
}
// --- LLM Configuration ---
const LLM_API_KEY = getConfigValue(["llm", "api_key"]);
const LLM_PROVIDER = getConfigValue(["llm", "provider"]) || "gemini";
const LLM_MODEL_GEMINI = getConfigValue(["llm", "model_gemini"]) || "gemini-1.5-flash-latest";
const LLM_MODEL_OPENAI = getConfigValue(["llm", "model_openai"]) || "gpt-4o-mini";
if (!LLM_API_KEY) {
    logger.warn("LLM API Key (llm.api_key) is not set. Drop-off analysis will use basic summary.");
}
// === OTP Function (Using Brevo) ===
exports.sendOtp = functions.https.onCall(async (data, context) => {
    var _a, _b, _c;
    const email = data.email;
    const applicationId = data.applicationId;
    if (!email || typeof email !== "string") {
        logger.error("sendOtp called without a valid email.", { data });
        throw new https_1.HttpsError("invalid-argument", "Email is required.");
    }
    // Check for Brevo config instead of SendGrid
    if (!BREVO_API_KEY) {
        logger.error("Brevo not configured for OTP.");
        throw new https_1.HttpsError("internal", "Email service unavailable.");
    }
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const now = new Date();
    const expiresAt = new Date(now.getTime() + 15 * 60 * 1000); // 15 minutes
    const otpData = {
        otp: otp, email: email, applicationId: applicationId || "",
        createdAt: admin.firestore.Timestamp.fromDate(now),
        expiresAt: admin.firestore.Timestamp.fromDate(expiresAt),
        isVerified: false, attemptCount: 0,
    };
    const subject = "Your Verification Code for Your Loan Application";
    // --- IMPORTANT: Replace with your actual email HTML ---
    const htmlContent = `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;">
        <div style="text-align: center; margin-bottom: 20px;">
           </div>
        <h2 style="color: #0A4287;">Your Verification Code</h2>
        <p>Dear Customer,</p>
        <p>Please use the following verification code to proceed:</p>
        <div style="background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 5px; margin: 20px 0;">
          ${otp}
        </div>
        <p>This code will expire in 15 minutes.</p>
        <p>If you did not request this code, please ignore this email.</p>
        <p>Thank you,<br>Your Loan Team</p>
      </div>
    `;
    // --- End Email HTML ---
    // --- Send Email via Brevo API ---
    const brevoApiUrl = 'https://api.brevo.com/v3/smtp/email';
    const payload = {
        sender: { name: 'Loan App Verification', email: BREVO_FROM_EMAIL },
        to: [{ email: email }],
        subject: subject,
        htmlContent: htmlContent,
    };
    try {
        // 1. Store OTP in Firestore
        await db.collection("otps").doc(email).set(otpData, { merge: true });
        logger.info(`OTP data stored for ${email}.`);
        // 2. Send email via Brevo
        await axios_1.default.post(brevoApiUrl, payload, {
            headers: { 'api-key': BREVO_API_KEY, 'Content-Type': 'application/json', 'Accept': 'application/json' },
        });
        logger.info(`OTP email initiated via Brevo for ${email}.`);
        return { success: true };
    }
    catch (error) {
        logger.error(`Error processing sendOtp for ${email} using Brevo:`, error);
        // Check if it's an axios error from Brevo
        if (error.response) {
            logger.error("Brevo API error response:", ((_a = error.response) === null || _a === void 0 ? void 0 : _a.data) || error.message);
            throw new https_1.HttpsError("internal", `Failed to send OTP email via provider: ${((_c = (_b = error.response) === null || _b === void 0 ? void 0 : _b.data) === null || _c === void 0 ? void 0 : _c.message) || error.message}`);
        }
        else {
            // Could be Firestore error or other issue
            throw new https_1.HttpsError("internal", `Internal error sending OTP: ${error.message}`);
        }
    }
});
// === Metadata Event Processor ===
exports.processEvent = functions.https.onRequest(async (request, response) => {
    logger.info("Received processEvent request", { body: request.body });
    try {
        const { applicationId, eventType, eventData } = request.body;
        if (!applicationId || !eventType) {
            logger.error("Missing required fields: applicationId or eventType");
            response.status(400).json({ error: "Missing required fields" });
            return;
        }
        const eventId = db.collection("temp").doc().id;
        const eventTimestamp = admin.firestore.FieldValue.serverTimestamp();
        const metadataRef = db.collection("applications").doc(applicationId).collection("metadata").doc(eventId);
        const metadataDoc = Object.assign({ eventId, applicationId, eventTimestamp, eventType }, eventData);
        await metadataRef.set(metadataDoc);
        logger.info(`Stored metadata event ${eventId} for application ${applicationId}`);
        if (eventType === "APP_BACKGROUNDED") {
            const dropOffTime = new Date();
            dropOffTime.setSeconds(dropOffTime.getSeconds() + 30); // 30s timeout
            await db.collection("backgrounded_apps").doc(applicationId).set({
                applicationId, backgroundedAt: eventTimestamp,
                dropOffCheckTime: admin.firestore.Timestamp.fromDate(dropOffTime),
                processed: false,
            }, { merge: true });
            logger.info(`Scheduled drop-off check for ${applicationId}`);
        }
        else if (eventType === "APP_FOREGROUNDED") {
            try {
                await db.collection("backgrounded_apps").doc(applicationId).delete();
                logger.info(`Cancelled drop-off check for ${applicationId}`);
            }
            catch (deleteError) {
                logger.warn(`Could not delete backgrounded_apps doc ${applicationId}: ${deleteError.message}`);
            }
        }
        response.status(200).json({ success: true, eventId: eventId });
    }
    catch (error) {
        logger.error("Error processing metadata event:", error);
        response.status(500).json({ error: "Failed to process event", details: error.message });
    }
});
// === Drop-off Checker (Scheduled Function) ===
exports.dropOffChecker = functions.pubsub
    .schedule("every 1 minutes")
    .onRun(async (context) => {
    logger.info("Running dropOffChecker function", { timestamp: context.timestamp });
    const now = admin.firestore.Timestamp.now();
    try {
        const querySnapshot = await db.collection("backgrounded_apps")
            .where("dropOffCheckTime", "<=", now)
            .where("processed", "==", false).get();
        if (querySnapshot.empty) {
            logger.info("No drop-offs to process.");
            return null;
        }
        logger.info(`Found ${querySnapshot.size} potential drop-offs.`);
        const processingPromises = [];
        querySnapshot.forEach((doc) => {
            const bgData = doc.data();
            const applicationId = bgData.applicationId;
            if (!applicationId) {
                logger.warn("Skipping backgrounded_app doc without applicationId.", { docId: doc.id });
                const markProcessedPromise = doc.ref.update({ processed: true })
                    .catch(err => logger.error(`Failed to mark invalid doc ${doc.id} as processed:`, err))
                    .then(() => { }); // Ensure it resolves to void
                processingPromises.push(markProcessedPromise);
                return;
            }
            const checkPromise = doc.ref.update({ processed: true })
                .then(async () => {
                logger.info(`Marked ${applicationId} as processed, checking details.`);
                await analyzeAndAlertIfDroppedOff(applicationId, bgData.backgroundedAt);
            }).catch(error => logger.error(`Error marking/checking ${applicationId}:`, error))
                .then(() => { }); // Ensure final promise resolves to void
            processingPromises.push(checkPromise);
        });
        await Promise.all(processingPromises);
        logger.info("Finished processing potential drop-offs batch.");
    }
    catch (error) {
        logger.error("Error in dropOffChecker function:", error);
        return null;
    }
    return null;
});
// === Helper: Analyze and Alert Logic ===
async function analyzeAndAlertIfDroppedOff(applicationId, backgroundedTimestamp) {
    try {
        logger.info(`Starting detailed drop-off check for ${applicationId}`);
        const appRef = db.collection("applications").doc(applicationId);
        const appDoc = await appRef.get();
        if (!appDoc.exists) {
            logger.warn(`App ${applicationId} not found.`);
            await db.collection("backgrounded_apps").doc(applicationId).delete().catch(() => { });
            return;
        }
        const appData = appDoc.data();
        if (!appData) {
            logger.error(`App data null for ${applicationId}.`);
            return;
        }
        if (appData.applicationStatus !== "IN_PROGRESS") {
            logger.info(`App ${applicationId} status ${appData.applicationStatus}, not IN_PROGRESS.`);
            await db.collection("backgrounded_apps").doc(applicationId).delete().catch(() => { });
            return;
        }
        const recentEventsSnapshot = await db.collection("applications").doc(applicationId).collection("metadata")
            .where("eventTimestamp", ">", backgroundedTimestamp).orderBy("eventTimestamp", "desc").limit(1).get();
        if (!recentEventsSnapshot.empty) {
            logger.info(`Recent activity found for ${applicationId}. Not dropped off.`);
            await db.collection("backgrounded_apps").doc(applicationId).delete().catch(() => { });
            return;
        }
        // CONFIRMED DROP-OFF
        logger.info(`CONFIRMED DROP-OFF for ${applicationId}. Taking action.`);
        await appRef.update({ applicationStatus: "DROPPED_OFF", lastUpdatedAt: admin.firestore.FieldValue.serverTimestamp() });
        logger.info(`Updated ${applicationId} status to DROPPED_OFF.`);
        const metadataSnapshot = await db.collection("applications").doc(applicationId).collection("metadata")
            .orderBy("eventTimestamp", "desc").limit(20).get();
        const metadata = metadataSnapshot.docs.map((doc) => doc.data());
        const analysis = await callLlmService(appData, metadata); // Calls LLM helper
        logger.info(`LLM Analysis result for ${applicationId}:`, analysis);
        // Optional: Store analysis
        // await db.collection("dropoff_analyses").doc(applicationId).set({ ...analysis, analyzedAt: admin.firestore.FieldValue.serverTimestamp() });
        await sendDropOffEmailAlert(applicationId, appData, analysis); // Calls Brevo Alert helper
    }
    catch (error) {
        logger.error(`Error during detailed drop-off check for ${applicationId}:`, error);
    }
}
// === Helper: LLM Service Call ===
async function callLlmService(appData, metadata) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m;
    logger.info(`Calling LLM Service (Provider: ${LLM_PROVIDER}) for ${appData.id}`);
    if (!LLM_API_KEY) {
        logger.warn("LLM API Key not configured. Returning basic analysis.");
        return { customerProfile: `No LLM API Key`, lastStep: appData.currentStep || "Unknown", followUpPriority: 5, issuesSummary: `LLM disabled.` };
    }
    // --- IMPORTANT: Review and refine this prompt for your specific needs ---
    const prompt = `
    Analyze this loan application drop-off data and provide a brief summary:
    Applicant Name (if available): ${((_a = appData.personalInfo) === null || _a === void 0 ? void 0 : _a.name) || 'N/A'}
    Current Application Status: ${appData.applicationStatus}
    Last Known Step in UI: ${appData.currentStep || 'N/A'}
    Completed Steps: ${appData.completedSteps ? appData.completedSteps.join(', ') : 'None'}
    Employment Type: ${((_b = appData.employmentDetails) === null || _b === void 0 ? void 0 : _b.employmentType) || 'N/A'}
    Declared Monthly Salary: ${((_c = appData.employmentDetails) === null || _c === void 0 ? void 0 : _c.monthlySalary) || 'N/A'}

    Recent Metadata Events (latest first, max 10 shown):
    ${JSON.stringify(metadata.slice(0, 10), null, 2)}

    Please provide:
    1. Customer Profile: Brief summary of the applicant type and situation based ONLY on the data provided.
    2. Last Known Step: Where they likely dropped off based on data.
    3. Follow-Up Priority (1-10): How important it is to follow up (higher for potentially valuable customers).
    4. Issues Summary: Potential reasons for drop-off based on the last step and metadata. Be concise.

    Format your response ONLY as a valid JSON object with keys: "customerProfile", "lastStep", "followUpPriority", "issuesSummary". Ensure the JSON is perfectly formatted.
    `;
    try {
        let generatedContent;
        if (LLM_PROVIDER === "gemini") {
            const modelToUse = LLM_MODEL_GEMINI || "gemini-1.5-flash-latest";
            const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelToUse}:generateContent?key=${LLM_API_KEY}`;
            logger.info(`Calling Gemini API: ${apiUrl}`);
            const response = await axios_1.default.post(apiUrl, { contents: [{ parts: [{ text: prompt }] }] }, { timeout: 15000 }); // Added timeout
            if (!((_h = (_g = (_f = (_e = (_d = response.data.candidates) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.content) === null || _f === void 0 ? void 0 : _f.parts) === null || _g === void 0 ? void 0 : _g[0]) === null || _h === void 0 ? void 0 : _h.text))
                throw new Error("Invalid Gemini response structure received.");
            generatedContent = response.data.candidates[0].content.parts[0].text;
        }
        else if (LLM_PROVIDER === "openai") {
            const modelToUse = LLM_MODEL_OPENAI || "gpt-4o-mini";
            const apiUrl = 'https://api.openai.com/v1/chat/completions';
            logger.info(`Calling OpenAI API: ${apiUrl} with model ${modelToUse}`);
            const response = await axios_1.default.post(apiUrl, {
                model: modelToUse,
                messages: [{ role: "user", content: prompt }],
                temperature: 0.3,
                response_format: { type: "json_object" }
            }, { headers: { 'Authorization': `Bearer ${LLM_API_KEY}`, 'Content-Type': 'application/json' }, timeout: 15000 }); // Added timeout
            if (!((_l = (_k = (_j = response.data.choices) === null || _j === void 0 ? void 0 : _j[0]) === null || _k === void 0 ? void 0 : _k.message) === null || _l === void 0 ? void 0 : _l.content))
                throw new Error("Invalid OpenAI response structure.");
            generatedContent = response.data.choices[0].message.content;
        }
        else {
            throw new Error(`Unsupported LLM provider: ${LLM_PROVIDER}`);
        }
        logger.info("Raw LLM response:", generatedContent);
        try {
            const cleanedJsonString = generatedContent.replace(/^```json\s*|```$/g, '').trim();
            return JSON.parse(cleanedJsonString);
        }
        catch (parseError) {
            logger.error("Failed to parse LLM JSON:", parseError);
            return extractAnalysisFromText(generatedContent, appData.currentStep);
        }
    }
    catch (error) {
        logger.error("Error calling LLM Service:", ((_m = error.response) === null || _m === void 0 ? void 0 : _m.data) || error.message, { axiosError: error.isAxiosError });
        return { customerProfile: "LLM Error.", lastStep: appData.currentStep || "Unknown", followUpPriority: 5, issuesSummary: `LLM API failed: ${error.message}` };
    }
}
// === Helper: Extract Analysis from Text ===
function extractAnalysisFromText(text, currentStep) {
    logger.warn("Attempting to extract analysis from non-JSON LLM response text.");
    let result = { customerProfile: 'Analysis format error.', lastStep: currentStep || 'Unknown', followUpPriority: 5, issuesSummary: 'Could not parse LLM response.' };
    try {
        const profileMatch = text.match(/Customer Profile:\s*"?([^"\n]+)"?/i);
        if (profileMatch)
            result.customerProfile = profileMatch[1].trim();
        const stepMatch = text.match(/Last Known Step:\s*"?([^"\n]+)"?/i);
        if (stepMatch)
            result.lastStep = stepMatch[1].trim();
        const priorityMatch = text.match(/Follow-Up Priority:\s*"?(\d+)"?/i);
        if (priorityMatch)
            result.followUpPriority = parseInt(priorityMatch[1], 10);
        // Adjusted regex to better capture multi-line summaries potentially ending the string
        const issuesMatch = text.match(/Issues Summary:\s*"?((?:.|\s)*?)(?:\n\n|###|$)/i);
        if (issuesMatch)
            result.issuesSummary = issuesMatch[1].replace(/^["\s]+|["\s]+$/g, '').trim();
    }
    catch (extractError) {
        logger.error("Error extracting analysis from text:", extractError);
    }
    return result;
}
// === Helper: Send Drop-off Email Alert (Uses Brevo) ===
async function sendDropOffEmailAlert(applicationId, appData, analysis) {
    var _a, _b;
    logger.info(`Sending drop-off email alert for ${applicationId} via Brevo.`);
    if (!BREVO_API_KEY) {
        logger.error("Brevo API Key missing for alerts.");
        return;
    }
    try {
        const apiUrl = 'https://api.brevo.com/v3/smtp/email';
        const name = ((_a = appData.personalInfo) === null || _a === void 0 ? void 0 : _a.name) || 'Applicant';
        // --- IMPORTANT: Replace with your actual alert email HTML ---
        const htmlContent = `
          <div style="font-family: sans-serif;">
            <h2>Loan Application Drop-off Alert</h2>
            <p><strong>Application ID:</strong> ${applicationId}</p>
            <p><strong>Applicant Name:</strong> ${name}</p>
            <hr>
            <h3>Drop-off Analysis Summary</h3>
            <p><strong>Customer Profile:</strong> ${analysis.customerProfile || 'N/A'}</p>
            <p><strong>Last Known Step:</strong> ${analysis.lastStep || 'N/A'}</p>
            <p><strong>Follow-Up Priority:</strong> ${analysis.followUpPriority || 'N/A'}/10</p>
            <p><strong>Potential Issues:</strong></p>
            <pre style="background-color: #f5f5f5; padding: 10px; border-radius: 4px;">${analysis.issuesSummary || 'N/A'}</pre>
            <hr>
            <p><i>This is an automated alert from the Loan Application System.</i></p>
          </div>
        `;
        // --- End Alert Email HTML ---
        await axios_1.default.post(apiUrl, {
            sender: { name: 'Loan App System Alerts', email: BREVO_FROM_EMAIL },
            to: [{ email: BREVO_ALERT_TO_EMAIL, name: 'Support Team' }],
            subject: `Drop-off Alert: Loan App ${applicationId} - ${name}`,
            htmlContent: htmlContent,
        }, { headers: { 'api-key': BREVO_API_KEY, 'Content-Type': 'application/json', 'Accept': 'application/json' }, timeout: 10000 }); // Added timeout
        logger.info(`Successfully sent drop-off alert email for ${applicationId}`);
    }
    catch (error) {
        logger.error(`Error sending drop-off email via Brevo for ${applicationId}:`, ((_b = error.response) === null || _b === void 0 ? void 0 : _b.data) || error.message, { axiosError: error.isAxiosError });
    }
}
//# sourceMappingURL=index.js.map