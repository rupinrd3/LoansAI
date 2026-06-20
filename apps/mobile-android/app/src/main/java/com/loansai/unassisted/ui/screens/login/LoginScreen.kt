package com.loansai.unassisted.ui.screens.login

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loansai.unassisted.R
import com.loansai.unassisted.ui.components.*
import com.loansai.unassisted.util.extensions.formatPhoneNumber
import com.loansai.unassisted.util.extensions.isValidMobileNumber
import kotlinx.coroutines.launch

/**
 * Login Screen with enhanced UI for mobile number verification
 */
@Composable
fun LoginScreen(
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    
    // Handle navigation events
    LaunchedEffect(key1 = uiState.navigateToPrivacyPolicy) {
        if (uiState.navigateToPrivacyPolicy) {
            onNavigateToPrivacyPolicy()
            viewModel.resetNavigation()
        }
    }
    
    LaunchedEffect(key1 = uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            onNavigateToHome()
            viewModel.resetNavigation()
        }
    }
    
    // Show error messages
    LaunchedEffect(key1 = uiState.error) {
        uiState.error?.let { error ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = error)
                viewModel.clearError()
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Only show error state if there's a fatal error
            when {
                uiState.fatalError != null -> {
                    ErrorState(
                        message = uiState.fatalError ?: "An unknown error occurred",
                        onRetry = { viewModel.retry() }
                    )
                }
                
                uiState.isLoading && !uiState.isOtpSent -> {
                    LoadingState(message = "Connecting...")
                }
                
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Logo and App Info
                        LoginHeader()
                        
                        Spacer(modifier = Modifier.height(40.dp))
                        
                        // Login Card
                        LoginCard(
                            phoneNumber = uiState.phoneNumber,
                            onPhoneNumberChange = { viewModel.updatePhoneNumber(it) },
                            isPhoneNumberValid = uiState.isPhoneNumberValid,
                            phoneNumberError = uiState.phoneNumberError,
                            otp = uiState.otp,
                            onOtpChange = { viewModel.updateOtp(it) },
                            otpError = uiState.otpError,
                            isOtpSent = uiState.isOtpSent,
                            isVerifyingOtp = uiState.isLoading && uiState.isOtpSent,
                            onRequestOtp = {
                                focusManager.clearFocus()
                                viewModel.requestOtp()
                            },
                            onVerifyOtp = {
                                focusManager.clearFocus()
                                viewModel.verifyOtp()
                            },
                            onResendOtp = { viewModel.requestOtp() }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Privacy policy text
                        Text(
                            text = "By continuing, you agree to our Privacy Policy and Terms of Service",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Login header with logo and title
 */
@Composable
private fun LoginHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo icon with squircle shape
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(SquircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            // Use the new drawable resource
            Image(
                painter = painterResource(id = R.drawable.app_icon_256),
                contentDescription = "App Logo",
                modifier = Modifier.size(80.dp), // Larger size to fill the squircle
                contentScale = ContentScale.Crop // Changed to crop to fill the squircle
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Personal Loans Simplified",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Login card with phone or OTP verification
 */
@Composable
private fun LoginCard(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    isPhoneNumberValid: Boolean,
    phoneNumberError: String?,
    otp: String,
    onOtpChange: (String) -> Unit,
    otpError: String?,
    isOtpSent: Boolean,
    isVerifyingOtp: Boolean,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isOtpSent) "Verify OTP" else "Enter Your Phone Number",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Phone number input section
            AnimatedVisibility(
                visible = !isOtpSent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { newValue ->
                            // Only allow numbers and limit to 10 digits
                            if (newValue.length <= 10 && newValue.all { it.isDigit() }) {
                                onPhoneNumberChange(newValue)
                            }
                        },
                        label = { Text("Phone Number") },
                        placeholder = { Text("10-digit mobile number") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onRequestOtp() }
                        ),
                        isError = phoneNumberError != null,
                        supportingText = { 
                            if (phoneNumberError != null) {
                                Text(phoneNumberError)
                            } else {
                                Text("Please enter your 10-digit mobile number")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onRequestOtp,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = phoneNumber.isValidMobileNumber(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text("Request OTP")
                    }
                }
            }
            
            // OTP verification section
            AnimatedVisibility(
                visible = isOtpSent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // OTP sent message
                    Text(
                        text = "OTP sent to ${phoneNumber.formatPhoneNumber()}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Improved OTP input using BasicTextField with custom decoration
                    ModernOtpInput(
                        otp = otp,
                        onOtpChange = onOtpChange,
                        error = otpError
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onVerifyOtp,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = otp.length == 6 && !isVerifyingOtp,
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verify OTP")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = onResendOtp
                    ) {
                        Text("Resend OTP")
                    }
                }
            }
        }
    }
}

/**
 * A modern OTP input component with individual digit boxes
 */
@Composable
fun ModernOtpInput(
    otp: String,
    onOtpChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Text explaining what to do
        Text(
            text = "Enter 6-digit OTP",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // OTP field with custom presentation
        BasicTextField(
            value = otp,
            onValueChange = { newValue ->
                // Only allow numbers and limit to 6 digits
                if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                    onOtpChange(newValue)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontSize = 1.sp, // Very small to hide the actual text
                color = Color.Transparent // Make the text invisible
            ),
            decorationBox = { innerTextField ->
                // This is where we create our custom visual representation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create 6 digit boxes
                    for (i in 0 until 6) {
                        val digit = when {
                            i < otp.length -> otp[i].toString()
                            else -> ""
                        }
                        
                        val isActive = i == otp.length
                        
                        OtpDigitBox(
                            digit = digit,
                            isActive = isActive,
                            isError = error != null
                        )
                    }
                    
                    // Place the actual text field above but make it invisible
                    innerTextField()
                }
            }
        )
        
        // Focus the text field on initial composition
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Safely handle focus request exceptions
            }
        }

        // Error message
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * Single OTP digit box
 */
@Composable
private fun OtpDigitBox(
    digit: String,
    isActive: Boolean,
    isError: Boolean
) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        digit.isNotEmpty() -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    
    Box(
        modifier = Modifier
            .size(45.dp)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Squircle shape for app icon - a rounded square that looks like iOS app icons
 */
val SquircleShape: Shape = GenericShape { size, _ ->
    val cornerRadius = size.width * 0.25f

    // Start at top-left corner
    moveTo(cornerRadius, 0f)
    
    // Top side and top-right corner
    lineTo(size.width - cornerRadius, 0f)
    quadraticBezierTo(size.width, 0f, size.width, cornerRadius)
    
    // Right side and bottom-right corner
    lineTo(size.width, size.height - cornerRadius)
    quadraticBezierTo(size.width, size.height, size.width - cornerRadius, size.height)
    
    // Bottom side and bottom-left corner
    lineTo(cornerRadius, size.height)
    quadraticBezierTo(0f, size.height, 0f, size.height - cornerRadius)
    
    // Left side and close path to top-left corner
    lineTo(0f, cornerRadius)
    quadraticBezierTo(0f, 0f, cornerRadius, 0f)
    
    close()
}