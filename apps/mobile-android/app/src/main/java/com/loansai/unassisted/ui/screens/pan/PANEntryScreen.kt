package com.loansai.unassisted.ui.screens.pan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.ui.components.*
import com.loansai.unassisted.util.extensions.formatPAN
import com.loansai.unassisted.util.extensions.isValidPAN
import com.loansai.unassisted.viewmodel.AIAssistantViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import com.loansai.unassisted.util.logger.AppLogger
import androidx.compose.ui.text.style.TextOverflow

/**
 * Enhanced PAN Entry screen with continuous scrolling design
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PANEntryScreen(
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PANViewModel = hiltViewModel(),
    aiViewModel: AIAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Request AI suggestions for the PAN entry screen
    LaunchedEffect(key1 = Unit) {
        aiViewModel.getSuggestions("PAN_ENTRY")
    }

    // Navigate to personal info screen if verification is successful
    LaunchedEffect(key1 = uiState.navigateToPersonalInfo) {
        if (uiState.navigateToPersonalInfo) {
            AppLogger.d("Navigating to Personal Info screen")
            // Small delay to ensure animations are visible
            delay(300)
            onNavigateToPersonalInfo()
            viewModel.resetNavigation()
        }
    }

    // Camera permission state
    val cameraPermission = rememberPermissionState(permission = Manifest.permission.CAMERA)

    // Setup camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                viewModel.processCapturedImage()
            }
        }
    )

    // Setup gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                viewModel.setImageUri(it)
                viewModel.processCapturedImage()
            }
        }
    )

    // Show dialog for edit extracted data
    var showEditDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Add swipe back handler for gesture navigation
        SwipeBackHandler(onBack = onNavigateBack)

        Scaffold(
            topBar = {
                Column {
                    // Use the modern back button from BackNavigation.kt
                    ModernBackButton(onBack = onNavigateBack)

                    TopAppBar(
                        title = { /* Empty title - we'll display it in the content */ },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        )
                    )
                }
            }
        ) { padding ->
            // Calculate progress
            val currentStep = 1 // PAN entry is step 1
            val totalSteps = 7
            val progress = currentStep.toFloat() / totalSteps.toFloat()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && !uiState.isPANVerified && scanState !is OCRScanState.Scanning -> {
                        LoadingState(message = "Verifying PAN details...")
                    }
                    uiState.fatalError != null -> {
                        ErrorState(
                            message = uiState.fatalError ?: "An unknown error occurred",
                            onRetry = { viewModel.retry() }
                        )
                    }
                    else -> {
                        // Main content using ContinuousFormContainer
                        ContinuousFormContainer(
                            title = "PAN Card Details",
                            subtitle = "Enter or scan your PAN card details to proceed",
                            progress = progress,
                            isLoading = scanState is OCRScanState.Scanning,
                            modifier = Modifier.padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                        ) {
                            // Visual separation - Manual PAN Entry Section
                            AnimatedFormSection(
                                title = "Enter PAN Manually",
                                subtitle = "Type your 10-character Permanent Account Number",
                                visible = true,
                                initiallyVisible = true
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Manual PAN entry
                                    EnhancedLoanTextField(
                                        value = uiState.panNumber,
                                        onValueChange = { value ->
                                            // Filter for valid PAN characters and limit to 10 characters
                                            val filtered = value.uppercase().filter { it.isLetterOrDigit() }
                                            if (filtered.length <= 10) {
                                                viewModel.updatePANNumber(filtered)
                                            }
                                        },
                                        label = "PAN Number",
                                        placeholder = "e.g., ABCDE1234F",
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CreditCard,
                                                contentDescription = null
                                            )
                                        },
                                        isError = uiState.panError != null,
                                        errorMessage = uiState.panError,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done,
                                        supportText = "Enter your 10-character PAN number",
                                        required = true,
                                        validationFunction = { input: String -> input.isValidPAN() }
                                    )

                                    // Verify PAN button
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.verifyPAN()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = uiState.isPANValid && !uiState.isPANVerified,
                                        contentPadding = PaddingValues(vertical = 16.dp)
                                    ) {
                                        Text("Verify PAN")
                                    }
                                }
                            }
                            
                            FormSectionDivider()
                            
                            // Visual separation - OCR Scanner Card
                            AnimatedFormSection(
                                title = "Scan PAN Card",
                                subtitle = "Use camera or upload an image of your PAN card",
                                visible = true,
                                initiallyVisible = true
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // OCR Scanner with result preview
                                        when (scanState) {
                                            is OCRScanState.Scanning -> {
                                                // Scanning indicator
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(48.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        strokeWidth = 4.dp
                                                    )

                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    Text(
                                                        text = "Scanning PAN card...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            is OCRScanState.Success -> {
                                                // Result preview
                                                OCRResultPreview(
                                                    panNumber = uiState.panNumber,
                                                    imageUri = (scanState as OCRScanState.Success).uri,
                                                    extractedName = uiState.extractedName,
                                                    extractedDOB = uiState.extractedDOB,
                                                    onAccept = {
                                                        viewModel.setExtractionConfirmed(true)
                                                        viewModel.onContinue()
                                                    },
                                                    onEdit = { showEditDialog = true },
                                                    isConfirmed = uiState.extractionConfirmed,
                                                    showExtractedFields = uiState.showExtractedFields
                                                )
                                            }
                                            is OCRScanState.Error -> {
                                                // Error message and retry options
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Error,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(48.dp)
                                                    )

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Text(
                                                        text = (scanState as OCRScanState.Error).message,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.error,
                                                        textAlign = TextAlign.Center
                                                    )

                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    // This is just the button row code that needs to be updated
                                                    // You'll need to insert this code into the appropriate location in your PANEntryScreen.kt file

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 16.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                if (cameraPermission.status.isGranted) {
                                                                    viewModel.prepareImageCapture(context, cameraLauncher)
                                                                } else {
                                                                    cameraPermission.launchPermissionRequest()
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(56.dp)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.CameraAlt,
                                                                    contentDescription = "Take Photo"
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text("Camera")
                                                            }
                                                        }

                                                        OutlinedButton(
                                                            onClick = { galleryLauncher.launch("image/*") },
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(56.dp)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PhotoLibrary,
                                                                    contentDescription = "Choose from Gallery"
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text("Gallery")
                                                            }
                                                        }
                                                    
                                                    }
                                                }
                                            }
                                            else -> {
                                                // Scan options
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            if (cameraPermission.status.isGranted) {
                                                                viewModel.prepareImageCapture(context, cameraLauncher)
                                                            } else {
                                                                cameraPermission.launchPermissionRequest()
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .widthIn(min = 148.dp) // Ensure minimum width
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CameraAlt,
                                                            contentDescription = "Take Photo"
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Camera")
                                                    }

                                                    OutlinedButton(
                                                        onClick = { galleryLauncher.launch("image/*") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .widthIn(min = 140.dp) // Ensure minimum width
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PhotoLibrary,
                                                            contentDescription = "Choose from Gallery"
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Gallery")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Success animation when verification is complete
                            AnimatedVisibility(
                                visible = uiState.isPANVerified && uiState.bureauReportFetched,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Success animation
                                        val scale by animateFloatAsState(
                                            targetValue = if (uiState.isPANVerified) 1f else 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Verified",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .scale(scale)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                            text = "Verification complete! Proceeding to next step...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Only show verification results if already verified
                            AnimatedVisibility(
                                visible = uiState.isPANVerified,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    FormSectionDivider()

                                    // Verification Results Section
                                    AnimatedFormSection(
                                        title = "Verification Results",
                                        subtitle = "Your PAN details have been verified",
                                        visible = true
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // Verified details
                                            uiState.verifiedPANDetails?.let { details ->
                                                VerifiedDetailsItem(
                                                    label = "Name",
                                                    value = details.name
                                                )

                                                VerifiedDetailsItem(
                                                    label = "PAN Number",
                                                    value = details.panNumber.formatPAN()
                                                )

                                                details.dateOfBirth?.let {
                                                    VerifiedDetailsItem(
                                                        label = "Date of Birth",
                                                        value = it.toString()
                                                    )
                                                }
                                            }

                                            // Bureau report status
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (uiState.bureauReportFetched) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Text(
                                                        text = "Credit Bureau Report Fetched",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                } else {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Text(
                                                        text = "Fetching Credit Bureau Report...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Skip button for development mode
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Skip button in bottom-left
                                IconButton(
                                    onClick = { viewModel.onSkip() },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .size(40.dp)
                                        .alpha(0.6f) // Make it subtle
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Skip PAN verification",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Extra space at the bottom for the AI Assistant bubble
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }

                // AI Assistant bubble
                AIAssistantBubble(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = 30.dp) // Add extra padding to avoid overlap with buttons
                )
            }
        }
    }

    // Edit dialog
    if (showEditDialog) {
        EditExtractedFieldsDialog(
            panNumber = uiState.panNumber,
            extractedName = uiState.extractedName,
            extractedDOB = uiState.extractedDOB,
            onPanNumberChange = { viewModel.updatePANNumber(it) },
            onNameChange = { viewModel.updateExtractedName(it) },
            onDOBChange = { viewModel.updateExtractedDOB(it) },
            onConfirm = {
                viewModel.setExtractionConfirmed(true)
                showEditDialog = false
                viewModel.onContinue()
            },
            onDismiss = {
                showEditDialog = false
            }
        )
    }
}

/**
 * OCR Result Preview with animations
 */
@Composable
fun OCRResultPreview(
    panNumber: String,
    imageUri: Uri?,
    extractedName: String = "",
    extractedDOB: String = "",
    onAccept: () -> Unit = {},
    onEdit: () -> Unit = {},
    isConfirmed: Boolean = false,
    showExtractedFields: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image preview
        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "PAN Card Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Extracted data with validation indicators
        AnimatedVisibility(
            visible = showExtractedFields,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Extracted Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // PAN Number
                if (panNumber.isNotEmpty()) {
                    ExtractedFieldItem(
                        label = "PAN Number",
                        value = panNumber.formatPAN(),
                        isValid = panNumber.isValidPAN()
                    )
                }

                // Name
                if (extractedName.isNotEmpty()) {
                    ExtractedFieldItem(
                        label = "Name",
                        value = extractedName,
                        isValid = extractedName.split(" ").size >= 2
                    )
                }

                // Date of Birth
                if (extractedDOB.isNotEmpty()) {
                    ExtractedFieldItem(
                        label = "Date of Birth",
                        value = extractedDOB,
                        isValid = extractedDOB.matches("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}".toRegex())
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirmation buttons
                if (!isConfirmed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Accept"
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Accept")
                        }

                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Edit")
                        }
                    }
                } else {
                    // Confirmation message
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Information confirmed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerifiedDetailsItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
fun ExtractedFieldItem(
    label: String,
    value: String,
    isValid: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )

        // Value
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Validation icon
        Icon(
            imageVector = if (isValid) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = if (isValid) "Valid" else "Warning",
            tint = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun EditExtractedFieldsDialog(
    panNumber: String,
    extractedName: String,
    extractedDOB: String,
    onPanNumberChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDOBChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit Extracted Information")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PAN Number field
                OutlinedTextField(
                    value = panNumber,
                    onValueChange = { value ->
                        // Filter for valid PAN characters and limit to 10 characters
                        val filtered = value.uppercase().filter { it.isLetterOrDigit() }
                        if (filtered.length <= 10) {
                            onPanNumberChange(filtered)
                        }
                    },
                    label = { Text("PAN Number") },
                    placeholder = { Text("e.g., ABCDE1234F") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Name field
                OutlinedTextField(
                    value = extractedName,
                    onValueChange = { newName -> onNameChange(newName) },
                    label = { Text("Name") },
                    placeholder = { Text("Full name as on PAN card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // DOB field
                OutlinedTextField(
                    value = extractedDOB,
                    onValueChange = { newDOB -> onDOBChange(newDOB) },
                    label = { Text("Date of Birth") },
                    placeholder = { Text("DD/MM/YYYY") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}