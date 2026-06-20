const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json'); // Path to your key

// Collections to clear
const COLLECTIONS_TO_CLEAR = [
  'applications',
  'documents',
  'otps',
  'employment_verifications',
  'application-documents',
  'bre_input',
  'bre_output',
  'loan_offers'
];

// Initialize Firebase
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  storageBucket: 'loansai.appspot.com' // Update this with your bucket name
});

const db = admin.firestore();
const bucket = admin.storage().bucket();

// Maximum batch size is 500 operations, using 450 to be safe
const MAX_BATCH_SIZE = 450;

// Function to delete a storage file
async function deleteFile(filePath) {
  try {
    await bucket.file(filePath).delete();
    console.log(`  Deleted file: ${filePath}`);
    return true;
  } catch (error) {
    console.error(`  Error deleting file ${filePath}:`, error.message);
    return false;
  }
}

// Function to delete all files in a directory
async function deleteDirectory(dirPath) {
  console.log(`Deleting all files in directory: ${dirPath}`);
  
  try {
    // List all files in the directory
    const [files] = await bucket.getFiles({ prefix: dirPath });
    
    console.log(`Found ${files.length} files in ${dirPath}`);
    
    let deletedCount = 0;
    
    // Delete each file
    for (const file of files) {
      try {
        await file.delete();
        deletedCount++;
        
        // Log progress every 20 files
        if (deletedCount % 20 === 0) {
          console.log(`  Deleted ${deletedCount}/${files.length} files...`);
        }
      } catch (error) {
        console.error(`  Error deleting file ${file.name}:`, error.message);
      }
    }
    
    console.log(`✅ Deleted ${deletedCount}/${files.length} files from ${dirPath}`);
    return deletedCount;
  } catch (error) {
    console.error(`Error listing/deleting files in ${dirPath}:`, error.message);
    return 0;
  }
}

// Function to delete all application files
async function deleteAllApplicationFiles() {
  console.log('\nCleaning up Storage files...');
  
  // Delete the entire applications directory
  const applicationsPath = 'applications/';
  const deletedCount = await deleteDirectory(applicationsPath);
  
  console.log(`✅ Completed storage cleanup. Total files deleted: ${deletedCount}`);
  return deletedCount;
}

// Function to clear a specific Firestore collection
async function clearCollection(collectionName) {
  console.log(`\nStarting to clear collection: ${collectionName}`);
  let totalDeleted = 0;
  
  // Track application IDs if we're clearing the applications collection
  const applicationIds = [];
  
  // Get documents in batches to handle large collections
  const fetchBatchSize = 200;
  let lastDoc = null;
  let hasMoreDocs = true;
  
  while (hasMoreDocs) {
    let query = db.collection(collectionName).limit(fetchBatchSize);
    
    // If we have a last document from previous batch, start after it
    if (lastDoc) {
      query = query.startAfter(lastDoc);
    }
    
    const snapshot = await query.get();
    
    // If we got fewer documents than requested, there are no more
    if (snapshot.size < fetchBatchSize) {
      hasMoreDocs = false;
    }
    
    if (snapshot.empty) {
      console.log(`No documents found in ${collectionName}`);
      break;
    }
    
    // Keep track of the last document for pagination
    lastDoc = snapshot.docs[snapshot.size - 1];
    
    // Process documents in this batch
    console.log(`Found ${snapshot.size} documents in ${collectionName}`);
    
    // Create batches for deletion
    let batch = db.batch();
    let batchCount = 0;
    
    for (const doc of snapshot.docs) {
      const docId = doc.id;
      
      // If this is an application document, store its ID for later storage cleanup
      if (collectionName === 'applications') {
        applicationIds.push(docId);
      }
      
      // If this is a document document, collect its storageUrl for later cleanup
      if (collectionName === 'documents') {
        const storageUrl = doc.data().storageUrl;
        if (storageUrl) {
          // Try to extract the file path from the URL
          try {
            const urlObj = new URL(storageUrl);
            const pathMatch = urlObj.pathname.match(/\/o\/(.+?)(?:\?|$)/);
            if (pathMatch && pathMatch[1]) {
              const filePath = decodeURIComponent(pathMatch[1]);
              console.log(`  Found storage file to delete: ${filePath}`);
              
              // Delete the file
              await deleteFile(filePath);
            }
          } catch (error) {
            console.log(`  Could not parse storage URL: ${storageUrl}`);
          }
        }
      }
      
      // Special handling for applications collection - check for metadata subcollection
      if (collectionName === 'applications') {
        // Get metadata subcollection for this application
        const metadataSnapshot = await db.collection('applications')
          .doc(doc.id)
          .collection('metadata')
          .get();
          
        if (!metadataSnapshot.empty) {
          console.log(`Found ${metadataSnapshot.size} metadata documents for application ${doc.id}`);
          
          // Add metadata deletions to batch
          for (const metaDoc of metadataSnapshot.docs) {
            batch.delete(metaDoc.ref);
            batchCount++;
            
            // Commit batch if getting too large
            if (batchCount >= MAX_BATCH_SIZE) {
              await batch.commit();
              console.log(`Committed batch with ${batchCount} operations`);
              totalDeleted += batchCount;
              batch = db.batch();
              batchCount = 0;
            }
          }
        }
      }
      
      // Add document to deletion batch
      batch.delete(doc.ref);
      batchCount++;
      
      // Commit batch if getting too large
      if (batchCount >= MAX_BATCH_SIZE) {
        await batch.commit();
        console.log(`Committed batch with ${batchCount} operations`);
        totalDeleted += batchCount;
        batch = db.batch();
        batchCount = 0;
      }
    }
    
    // Commit any remaining operations
    if (batchCount > 0) {
      await batch.commit();
      console.log(`Committed final batch with ${batchCount} operations`);
      totalDeleted += batchCount;
    }
  }
  
  console.log(`✅ Completed clearing ${collectionName}. Total documents deleted: ${totalDeleted}`);
  
  return { totalDeleted, applicationIds };
}

async function clearAllCollections() {
  console.log('Starting to clear all collections and storage files');
  
  let grandTotal = 0;
  let allApplicationIds = [];
  
  for (const collection of COLLECTIONS_TO_CLEAR) {
    try {
      const { totalDeleted, applicationIds } = await clearCollection(collection);
      grandTotal += totalDeleted;
      
      // If this was the applications collection, store the IDs
      if (collection === 'applications' && applicationIds.length > 0) {
        allApplicationIds = allApplicationIds.concat(applicationIds);
      }
    } catch (error) {
      console.error(`Error clearing ${collection}:`, error);
    }
  }
  
  console.log(`\nFinished clearing all collections. Total documents deleted: ${grandTotal}`);
  
  // Delete all application files from storage
  const filesDeleted = await deleteAllApplicationFiles();
  
  console.log(`\n=== DELETION SUMMARY ===`);
  console.log(`Total Firestore documents deleted: ${grandTotal}`);
  console.log(`Total Storage files deleted: ${filesDeleted}`);
  console.log(`===== END SUMMARY =====`);
}

// Run the deletion process
clearAllCollections()
  .then(() => {
    console.log('\nAll records and files have been deleted while preserving collections.');
    process.exit(0);
  })
  .catch(error => {
    console.error('Unhandled error:', error);
    process.exit(1);
  });