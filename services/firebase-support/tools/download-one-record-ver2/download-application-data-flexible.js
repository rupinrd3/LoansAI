const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json'); // Path to your key
const fs = require('fs');

// --- CONFIGURATION ---
// Replace with the Application ID you want to download
const APPLICATION_ID_TO_DOWNLOAD = 'app-1752899249127'; // <<< CHANGE THIS

// List of top-level collections that contain an 'applicationId' field
// Add or remove collection names here as your database structure changes
const COLLECTIONS_WITH_APPLICATION_ID_FIELD = [
  'documents',
  'application-documents',
  'employment_verifications',
  'bre_input',
  'bre_output',
  'loan_offers',
  'otps',
  'mcp_sessions', // Added missing collection
];

// Specify subcollections that are directly under an application document
const APPLICATION_SUBCOLLECTIONS = [
  'metadata',
  'obligationRefinement',
  'mcp_interactions', // Added missing subcollection
];
// --- END CONFIGURATION ---


// Initialize Firebase Admin SDK
try {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: `https://${serviceAccount.project_id}.firebaseio.com`
  });
} catch (error) {
  console.error('Error initializing Firebase Admin SDK:', error);
  process.exit(1);
}


const db = admin.firestore();

async function downloadApplicationData(applicationId) {
  const applicationData = {};

  try {
    console.log(`Downloading data for Application ID: ${applicationId}`);

    // 1. Get the main application document (assuming ID is the application ID)
    const appDocRef = db.collection('applications').doc(applicationId);
    const appDoc = await appDocRef.get();

    if (!appDoc.exists) {
      console.log(`Application document with ID ${applicationId} does not exist.`);
      // Proceed to check other collections even if the main doc is missing
    } else {
        applicationData.applications = {
            id: appDoc.id, // Include the document ID
            ...appDoc.data()
        };
        console.log(`Downloaded application document.`);

        // 2. Get specified subcollections of the application document
        applicationData.applications.subcollections = {};
        for (const subcollectionName of APPLICATION_SUBCOLLECTIONS) {
            try {
                const subcollectionSnapshot = await appDocRef.collection(subcollectionName).get();
                if (!subcollectionSnapshot.empty) {
                    applicationData.applications.subcollections[subcollectionName] = subcollectionSnapshot.docs.map(doc => ({
                        id: doc.id,
                        ...doc.data()
                    }));
                    console.log(`Downloaded ${subcollectionSnapshot.size} documents from ${subcollectionName} subcollection.`);
                } else {
                    console.log(`No documents found in ${subcollectionName} subcollection.`);
                }
            } catch (error) {
                console.warn(`Could not access subcollection "${subcollectionName}". Error: ${error.message}`);
            }
        }
    }

    // 3. Query specified top-level collections by 'applicationId' field
    for (const collectionName of COLLECTIONS_WITH_APPLICATION_ID_FIELD) {
        try {
            const collectionSnapshot = await db.collection(collectionName)
                .where('applicationId', '==', applicationId)
                .get();

            if (!collectionSnapshot.empty) {
                applicationData[collectionName] = collectionSnapshot.docs.map(doc => ({
                    id: doc.id,
                    ...doc.data()
                }));
                console.log(`Downloaded ${collectionSnapshot.size} documents from ${collectionName} collection.`);
            } else {
                console.log(`No documents found in ${collectionName} collection for this application ID.`);
            }
        } catch (error) {
            console.warn(`Could not query collection "${collectionName}". It might not exist or 'applicationId' field is missing/not indexed. Error: ${error.message}`);
        }
    }

    // 4. Additional logic for collections that might use different linking fields
    // For example, if 'otps' collection links via 'userId' or 'email' instead of 'applicationId'
    // You can add custom logic here if needed
    
    return applicationData;

  } catch (error) {
    console.error('Error during data download process:', error);
    return null;
  }
}

async function runDownload() {
  if (APPLICATION_ID_TO_DOWNLOAD === 'YOUR_APPLICATION_ID') {
      console.error("Please replace 'YOUR_APPLICATION_ID' with the actual application ID in the script.");
      process.exit(1);
  }

  const data = await downloadApplicationData(APPLICATION_ID_TO_DOWNLOAD);

  if (data && Object.keys(data).length > 0) { // Check if any data was fetched
    const outputFileName = `application_data_${APPLICATION_ID_TO_DOWNLOAD}.json`;
    fs.writeFileSync(outputFileName, JSON.stringify(data, null, 2));
    console.log(`\nSuccessfully downloaded data to ${outputFileName}`);
    
    // Print summary of what was downloaded
    console.log('\n--- Download Summary ---');
    Object.keys(data).forEach(collection => {
      if (collection === 'applications') {
        console.log(`applications: 1 document`);
        if (data.applications.subcollections) {
          Object.keys(data.applications.subcollections).forEach(subCol => {
            console.log(`  └─ ${subCol}: ${data.applications.subcollections[subCol].length} documents`);
          });
        }
      } else {
        console.log(`${collection}: ${Array.isArray(data[collection]) ? data[collection].length : 0} documents`);
      }
    });
    
    process.exit(0);
  } else {
    console.log('\nNo data found or failed to download application data.');
    process.exit(1);
  }
}

runDownload();