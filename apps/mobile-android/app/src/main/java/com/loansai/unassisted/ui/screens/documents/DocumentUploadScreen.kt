package com.loansai.unassisted.ui.screens.documents

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.ExtractionStatus
import com.loansai.unassisted.ui.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.DisposableEffect
import com.loansai.unassisted.domain.model.MetadataEventType
import androidx.compose.runtime.LaunchedEffect
import com.loansai.unassisted.ui.navigation.Screen
import com.loansai.unassisted.ui.components.EnhancedDocumentUploadCard


/**
 * Document Upload Screen with continuous scrolling form
 * Updated for v1.5.0 with document processing status section
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DocumentUploadScreen(
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val processingStatusMap by viewModel.processingStatusMap.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    
    // Add screen visit tracking
    DisposableEffect(Unit) {
        // Record screen visit when screen is shown
        viewModel.recordScreenVisit(Screen.DocumentUpload.route)
        
        onDispose {
            // End screen visit when leaving the screen
            viewModel.endScreenVisit(Screen.DocumentUpload.route)
        }
    }
    
    // Show warning dialog for proceeding without documents
    var showNoDocumentWarning by remember { mutableStateOf(false) }
    
    // Set context for file operations
    LaunchedEffect(context) {
        viewModel.setContext(context)
    }
    
    // Permission state for camera
    val cameraPermission = rememberPermissionState(permission = Manifest.permission.CAMERA)
    
    // Check if camera permission is granted
    fun checkCameraPermission(onGranted: () -> Unit) {
        if (cameraPermission.status.isGranted) {
            onGranted()
        } else {
            cameraPermission.launchPermissionRequest()
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                // If picture was taken successfully, process it
                when (val currentState = processingState) {
                    is DocumentProcessingState.WaitingForCamera -> {
                        viewModel.processSelectedFile(currentState.outputUri)
                    }
                    else -> {
                        // Unexpected state, do nothing
                    }
                }
            }
        }
    )
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.processSelectedFile(uri)
            }
        }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Empty title - we'll display it in the content */ },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // Calculate progress
        val currentStep = 4 // Document upload is step 4
        val totalSteps = 7 
        val progress = currentStep.toFloat() / totalSteps.toFloat()

        Box(modifier = Modifier.fillMaxSize()) {
            // Main content using ContinuousFormContainer
            ContinuousFormContainer(
                title = "Document Upload",
                subtitle = "Upload relevant documents to support your application",
                progress = progress,
                isLoading = state.isLoading || 
                           processingState is DocumentProcessingState.Processing || 
                           processingState is DocumentProcessingState.Uploading,
                modifier = Modifier.padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                )
            ) {
                // Document upload instructions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Document Guidelines",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• Files must be clear and readable\n• Maximum file size: 5MB per document\n• Supported formats: JPG, PNG, PDF\n• All pages must be included",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                FormSectionDivider()
                
                // Bank Statement Section
                LaunchedEffect(Unit) {
                    viewModel.startSectionTiming(Screen.DocumentUpload.route, "BANK_STATEMENT")
                }

                AnimatedFormSection(
                    title = "Bank Statement",
                    subtitle = "Last 6 months statement from your salary account",
                    visible = true
                ) {
                    EnhancedDocumentUploadCard(
                        documentType = DocumentType.BANK_STATEMENT,
                        document = state.bankStatement,
                        onCameraClick = { 
                            checkCameraPermission {
                                viewModel.startCamera(DocumentType.BANK_STATEMENT, cameraLauncher)
                            }
                        },
                        onGalleryClick = { viewModel.openGallery(DocumentType.BANK_STATEMENT, filePickerLauncher) },
                        onDeleteClick = { viewModel.removeDocument(DocumentType.BANK_STATEMENT) },
                        processingState = processingState
                    )
                }
                
                FormSectionDivider()
                
                // Salary Slip Section
                LaunchedEffect(Unit) {
                    viewModel.startSectionTiming(Screen.DocumentUpload.route, "SALARY_SLIP")
                }

                AnimatedFormSection(
                    title = "Salary Slip",
                    subtitle = "Last 3 months salary slips from your employer",
                    visible = true
                ) {
                    EnhancedDocumentUploadCard(
                        documentType = DocumentType.SALARY_SLIP,
                        document = state.salarySlip,
                        onCameraClick = { 
                            checkCameraPermission {
                                viewModel.startCamera(DocumentType.SALARY_SLIP, cameraLauncher)
                            }
                        },
                        onGalleryClick = { viewModel.openGallery(DocumentType.SALARY_SLIP, filePickerLauncher) },
                        onDeleteClick = { viewModel.removeDocument(DocumentType.SALARY_SLIP) },
                        processingState = processingState
                    )
                }
                
                FormSectionDivider()
                
                // Income Tax Return Section
                LaunchedEffect(Unit) {
                    viewModel.startSectionTiming(Screen.DocumentUpload.route, "INCOME_TAX_RETURN")
                }

                AnimatedFormSection(
                    title = "Income Tax Return",
                    subtitle = "Latest IT return filing for income verification",
                    visible = true
                ) {
                    EnhancedDocumentUploadCard(
                        documentType = DocumentType.INCOME_TAX_RETURN,
                        document = state.incomeTaxReturn,
                        onCameraClick = { 
                            checkCameraPermission {
                                viewModel.startCamera(DocumentType.INCOME_TAX_RETURN, cameraLauncher)
                            }
                        },
                        onGalleryClick = { viewModel.openGallery(DocumentType.INCOME_TAX_RETURN, filePickerLauncher) },
                        onDeleteClick = { viewModel.removeDocument(DocumentType.INCOME_TAX_RETURN) },
                        processingState = processingState
                    )
                }
                
                FormSectionDivider()
                
                // Form 26AS Section
                LaunchedEffect(Unit) {
                    viewModel.startSectionTiming(Screen.DocumentUpload.route, "FORM_26AS")
                }

                AnimatedFormSection(
                    title = "Form 26AS",
                    subtitle = "Tax credit statement from income tax department",
                    visible = true
                ) {
                    EnhancedDocumentUploadCard(
                        documentType = DocumentType.FORM_26AS,
                        document = state.form26AS,
                        onCameraClick = { 
                            checkCameraPermission {
                                viewModel.startCamera(DocumentType.FORM_26AS, cameraLauncher)
                            }
                        },
                        onGalleryClick = { viewModel.openGallery(DocumentType.FORM_26AS, filePickerLauncher) },
                        onDeleteClick = { viewModel.removeDocument(DocumentType.FORM_26AS) },
                        processingState = processingState
                    )
                }
                
                FormSectionDivider()
                
                // Document Processing Status Summary Section (NEW FOR v1.5.0)
                if (processingStatusMap.isNotEmpty()) {
                    AnimatedFormSection(
                        title = "Document Processing Status",
                        subtitle = "Real-time status of document analysis",
                        visible = true
                    ) {
                        DocumentProcessingStatusSummary(
                            processingStatusMap = processingStatusMap,
                            documents = listOfNotNull(
                                state.bankStatement,
                                state.salarySlip,
                                state.incomeTaxReturn,
                                state.form26AS
                            )
                        )
                    }
                    
                    FormSectionDivider()
                }
                
                // Action Button
                Button(
                    onClick = {
                        if (state.hasAnyDocument) {
                            viewModel.completeSectionTimings()
                            viewModel.saveProgress()
                            onNextClick()
                        } else {
                            showNoDocumentWarning = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text("Continue")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null
                    )
                }
                
                // Progress indicator
                if (state.hasAnyDocument) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Document Upload Progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Count uploaded documents
                        val uploadedCount = listOf(
                            state.bankStatement,
                            state.salarySlip,
                            state.incomeTaxReturn,
                            state.form26AS
                        ).count { it != null }
                        
                        val uploadProgress = uploadedCount / 4f
                        
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "$uploadedCount of 4 documents uploaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Extra space at the bottom for the AI Assistant bubble
                Spacer(modifier = Modifier.height(80.dp))
            }
            
            // AI Assistant bubble
            AIAssistantBubble(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = { viewModel.showAIAssistant() }
            )
            
            // Show error dialog if there's an error
            if (processingState is DocumentProcessingState.Error) {
                val errorState = processingState as DocumentProcessingState.Error
                AlertDialog(
                    onDismissRequest = { /* Reset error state */ },
                    title = { Text(text = "Error") },
                    text = { Text(text = errorState.message) },
                    confirmButton = {
                        TextButton(onClick = { /* Reset error state */ }) {
                            Text(text = "OK")
                        }
                    }
                )
            }
            
            // Warning dialog for no documents
            if (showNoDocumentWarning) {
                AlertDialog(
                    onDismissRequest = { showNoDocumentWarning = false },
                    title = { Text(text = "No Documents Uploaded") },
                    text = { 
                        Text(
                            text = "You haven't uploaded any documents. Supporting documents greatly improve your chances of loan approval.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showNoDocumentWarning = false
                                viewModel.saveProgress()
                                onNextClick()
                            }
                        ) {
                            Text(text = "Proceed Anyway")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showNoDocumentWarning = false }) {
                            Text(text = "Upload Documents")
                        }
                    }
                )
            }
        }
    }
}

/**
 * NEW FOR v1.5.0: Document processing status summary section
 * Displays the status of document processing by the backend LLM API
 */
@Composable
fun DocumentProcessingStatusSummary(
    processingStatusMap: Map<DocumentType, ProcessingStatusInfo>,
    documents: List<Document>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Display each document's processing status
            documents.forEach { document ->
                val statusInfo = processingStatusMap[document.documentType]
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Document type icon and name
                    Icon(
                        imageVector = getDocumentTypeIcon(document.documentType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = getDocumentTypeName(document.documentType),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Status indicator
                    if (statusInfo != null) {
                        when (statusInfo.status) {
                            ProcessingStatus.PROCESSING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            ProcessingStatus.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            ProcessingStatus.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            ProcessingStatus.ONGOING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    Text(
                                        text = "Taking longer...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        // No status yet
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Waiting",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Add a divider if not the last item
                if (document != documents.last()) {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            
            // Add explanatory text if any document is still processing
            if (processingStatusMap.any { it.value.status == ProcessingStatus.PROCESSING || 
                                         it.value.status == ProcessingStatus.ONGOING }) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Documents are being analyzed. You can continue while processing completes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Helper function to get document type name
 */
@Composable
fun getDocumentTypeName(documentType: DocumentType): String {
    return when (documentType) {
        DocumentType.BANK_STATEMENT -> "Bank Statement"
        DocumentType.SALARY_SLIP -> "Salary Slip"
        DocumentType.INCOME_TAX_RETURN -> "Income Tax Return"
        DocumentType.FORM_26AS -> "Form 26AS"
        else -> documentType.name.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}




/**
 * Document preview component
 */
@Composable
fun DocumentPreviewContent(
    document: Document,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document icon based on file type
            Icon(
                imageVector = getFileTypeIcon(document.fileType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Document filename
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = formatFileSize(document.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        
        // Status indicator
        when (document.documentStatus) {
            com.loansai.unassisted.domain.model.DocumentStatus.PROCESSED,
            com.loansai.unassisted.domain.model.DocumentStatus.VERIFIED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Document successfully processed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            com.loansai.unassisted.domain.model.DocumentStatus.ERROR,
            com.loansai.unassisted.domain.model.DocumentStatus.REJECTED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Error processing document. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            com.loansai.unassisted.domain.model.DocumentStatus.PROCESSING -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Processing document...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            else -> {
                // No specific status to show
            }
        }
    }
}

/**
 * Get icon for document type
 */
@Composable
fun getDocumentTypeIcon(documentType: DocumentType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (documentType) {
        DocumentType.BANK_STATEMENT -> Icons.Default.AccountBalance
        DocumentType.SALARY_SLIP -> Icons.Default.Description
        DocumentType.INCOME_TAX_RETURN -> Icons.Default.Description
        DocumentType.FORM_26AS -> Icons.Default.Description
        else -> Icons.Default.Description
    }
}

/**
 * Get icon for file type
 */
@Composable
fun getFileTypeIcon(fileType: com.loansai.unassisted.domain.model.FileType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (fileType) {
        com.loansai.unassisted.domain.model.FileType.PDF -> Icons.Default.PictureAsPdf
        com.loansai.unassisted.domain.model.FileType.JPG, 
        com.loansai.unassisted.domain.model.FileType.PNG -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * Format file size to human-readable string
 */
private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes < 1024) return "$sizeInBytes B"
    val exp = (Math.log(sizeInBytes.toDouble()) / Math.log(1024.0)).toInt()
    val prefix = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", sizeInBytes / Math.pow(1024.0, exp.toDouble()), prefix)
}