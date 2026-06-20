package com.loansai.unassisted.ui.screens.verification

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.ui.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.loansai.unassisted.ui.screens.verification.VerificationMethod

/**
 * Employment Verification Screen with continuous scrolling form
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EmploymentVerificationScreen(
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: EmploymentVerificationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // Dialog state
    var showSkipVerificationDialog by remember { mutableStateOf(false) }
    
    // Camera permission for ID card scanning
    val cameraPermission = rememberPermissionState(permission = android.Manifest.permission.CAMERA)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Empty title - we'll display it in the content */ },
                navigationIcon = {
                    // Modern back button with circle background
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        val currentStep = 6 // Employment verification is step 6
        val totalSteps = 7 
        val progress = currentStep.toFloat() / totalSteps.toFloat()

        Box(modifier = Modifier.fillMaxSize()) {
            // Main content using ContinuousFormContainer
            ContinuousFormContainer(
                title = "Employment Verification",
                subtitle = "Verify your employment details to proceed",
                progress = progress,
                isLoading = state.isLoading,
                modifier = Modifier.padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                )
            ) {
                // Verification Methods Section
                AnimatedFormSection(
                    title = "Verification Methods",
                    subtitle = "Choose how you want to verify your employment",
                    visible = true,
                    initiallyVisible = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        VerificationMethodSelector(
                            selectedMethod = state.selectedMethod,
                            onMethodSelected = { viewModel.selectMethod(it) }
                        )
                    }
                }
                
                FormSectionDivider()
                
                // Verification Content based on selected method
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (state.selectedMethod) {
                        VerificationMethod.EMAIL -> EmailVerificationSection(
                            workEmail = state.workEmail,
                            onWorkEmailChange = { viewModel.updateWorkEmail(it) },
                            workEmailError = state.workEmailError,
                            otp = state.otp,
                            onOtpChange = { viewModel.updateOTP(it) },
                            otpError = state.otpError,
                            otpSent = state.otpSent,
                            onSendOtp = { viewModel.sendOTP() },
                            onResendOtp = { viewModel.resendOTP() },
                            onVerifyOtp = { viewModel.verifyOTP() },
                            isVerified = state.isVerified,
                            isVerifying = state.isLoading,
                            isPrivateSector = viewModel.isPrivateSector()
                        )
                        
                        VerificationMethod.ID_CARD -> IDCardVerificationSection(
                            idCardScanned = state.idCardScanned,
                            idCardDocument = state.idCardDocument,
                            onScanClick = { 
                                if (cameraPermission.status.isGranted) {
                                    viewModel.scanIDCard()
                                } else {
                                    cameraPermission.launchPermissionRequest()
                                }
                            },
                            onSelectClick = { viewModel.selectIDCard() },
                            onRemoveClick = { viewModel.removeIDCard() },
                            isVerified = state.isVerified
                        )
                        
                        else -> {
                            // Handle other verification methods
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "This verification method is not yet implemented",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { viewModel.selectMethod(VerificationMethod.EMAIL) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Try Email Verification Instead")
                                    }
                                }
                            }
                        }
                    }
                }
                
                FormSectionDivider()
                
                // Action Button
                Button(
                    onClick = {
                        if (state.isVerified) {
                            viewModel.saveProgress()
                            onNextClick()
                        } else {
                            // Show confirmation dialog
                            showSkipVerificationDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text("Continue to Review & Submit")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
                
                // Extra space at the bottom for the AI Assistant bubble
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            // AI Assistant bubble - adjusted position and z-index
            AIAssistantBubble(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .zIndex(10f), // Higher z-index to ensure it appears above other elements
                onClick = { viewModel.showAIAssistant() }
            )
            
            // Skip verification confirmation dialog
            if (showSkipVerificationDialog) {
                AlertDialog(
                    onDismissRequest = { showSkipVerificationDialog = false },
                    title = { Text("Proceed Without Verification?") },
                    text = { 
                        Text(
                            "Proceeding without employment verification may delay your loan application approval process. Are you sure you want to continue?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSkipVerificationDialog = false
                                viewModel.saveProgress()
                                onNextClick()
                            }
                        ) {
                            Text("Proceed Anyway")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSkipVerificationDialog = false }
                        ) {
                            Text("Go Back")
                        }
                    }
                )
            }
            
            // Error dialog if there's an error
            if (state.error != null) {
                AlertDialog(
                    onDismissRequest = { 
                        // Add this to clear the error when dialog is dismissed
                        viewModel.clearError() 
                    },
                    title = { Text("Verification Error") },
                    text = { Text(state.error!!) },
                    confirmButton = {
                        TextButton(onClick = { 
                            // Add this to clear the error when OK is pressed
                            viewModel.clearError() 
                        }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Verification method selector with radio buttons
 */
@Composable
private fun VerificationMethodSelector(
    selectedMethod: VerificationMethod,
    onMethodSelected: (VerificationMethod) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Email Verification Option
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onMethodSelected(VerificationMethod.EMAIL) }
                .border(
                    width = 1.dp,
                    color = if (selectedMethod == VerificationMethod.EMAIL)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedMethod == VerificationMethod.EMAIL)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMethod == VerificationMethod.EMAIL,
                    onClick = { onMethodSelected(VerificationMethod.EMAIL) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = "Work Email Verification",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Verify using your official work email address",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // ID Card Verification Option
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onMethodSelected(VerificationMethod.ID_CARD) }
                .border(
                    width = 1.dp,
                    color = if (selectedMethod == VerificationMethod.ID_CARD)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedMethod == VerificationMethod.ID_CARD)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMethod == VerificationMethod.ID_CARD,
                    onClick = { onMethodSelected(VerificationMethod.ID_CARD) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = "Employee ID Card",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Scan your official employee ID card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Email verification section with updated work email field behavior
 */
@Composable
private fun EmailVerificationSection(
    workEmail: String,
    onWorkEmailChange: (String) -> Unit,
    workEmailError: String?,
    otp: String,
    onOtpChange: (String) -> Unit,
    otpError: String?,
    otpSent: Boolean,
    onSendOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    isVerified: Boolean,
    isVerifying: Boolean,
    isPrivateSector: Boolean
) {
    AnimatedFormSection(
        title = "Email Verification",
        subtitle = "We'll send a verification code to your work email",
        visible = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Work Email input
            if (!isVerified) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (workEmailError != null) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Work Email *",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = workEmail,
                            onValueChange = onWorkEmailChange,
                            placeholder = { Text("Enter your official email address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null
                                )
                            },
                            isError = workEmailError != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = if (workEmailError != null) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = if (workEmailError != null) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = isPrivateSector, // Make it read-only if user selected Private Sector
                            enabled = !isPrivateSector  // Disable it if user selected Private Sector
                        )
                        
                        if (workEmailError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = workEmailError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // Display note explaining why field is readonly for private sector employees
                        if (isPrivateSector) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This email address has been pre-filled from your employment details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Button(
                    onClick = onSendOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = workEmail.isNotEmpty() && workEmailError == null && !otpSent && !isVerifying
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Send Verification Code")
                }
            }
            
            // OTP Verification
            AnimatedVisibility(
                visible = otpSent && !isVerified,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "We've sent a verification code to $workEmail",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            if (workEmail.endsWith("@example.com") || 
                                workEmail.endsWith("@test.com") || 
                                workEmail.endsWith("@hdfcbank.com")) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "For testing, use code: 123456",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (otpError != null) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Verification Code *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = otp,
                                onValueChange = { 
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        onOtpChange(it)
                                    }
                                },
                                placeholder = { Text("Enter 6-digit code") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Password,
                                        contentDescription = null
                                    )
                                },
                                isError = otpError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = if (otpError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = if (otpError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            
                            if (otpError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = otpError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onResendOtp
                        ) {
                            Text("Resend Code")
                        }
                        
                        Button(
                            onClick = onVerifyOtp,
                            enabled = otp.length == 6 && !isVerifying
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Verify")
                        }
                    }
                }
            }
            
            // Verification Success
            AnimatedVisibility(
                visible = isVerified,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Text(
                            text = "Employment Verified!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = "Your employment details have been successfully verified.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * ID Card verification section
 */
@Composable
private fun IDCardVerificationSection(
    idCardScanned: Boolean,
    idCardDocument: com.loansai.unassisted.domain.model.Document?,
    onScanClick: () -> Unit,
    onSelectClick: () -> Unit,
    onRemoveClick: () -> Unit,
    isVerified: Boolean
) {
    AnimatedFormSection(
        title = "ID Card Verification",
        subtitle = "Scan your employee ID card",
        visible = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!idCardScanned) {
                // ID Card upload options
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Please capture or upload a clear image of your employee ID card",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    
                        Text(
                            text = "Make sure all details like your name, employee ID, and company name are clearly visible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Camera option
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Scan ID"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan ID Card")
                    }
                    
                    // Gallery option
                    OutlinedButton(
                        onClick = onSelectClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Select from Gallery"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Image")
                    }
                }
            } else {
                // ID Card preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // This would be replaced with actual image in real app
                        Text(
                            text = "ID Card Preview",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        // Delete button
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                // Rescan button
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Rescan"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rescan ID Card")
                }
            }
            
            // Verification Success
            AnimatedVisibility(
                visible = isVerified,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Text(
                            text = "Employment Verified!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Text(
                            text = "Your employment details have been successfully verified using your ID card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}