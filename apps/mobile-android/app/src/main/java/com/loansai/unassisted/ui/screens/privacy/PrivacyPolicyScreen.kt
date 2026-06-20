package com.loansai.unassisted.ui.screens.privacy

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loansai.unassisted.R
import com.loansai.unassisted.ui.components.*
import com.loansai.unassisted.viewmodel.AIAssistantViewModel
import kotlinx.coroutines.launch

/**
 * Enhanced Privacy Policy screen with continuous scrolling design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateToHome: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PrivacyPolicyViewModel = hiltViewModel(),
    aiViewModel: AIAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Request AI suggestions for the privacy policy screen
    LaunchedEffect(key1 = Unit) {
        aiViewModel.getSuggestions("PRIVACY_POLICY")
    }
    
    // Navigate to home if consent is successful
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
        topBar = {
            TopAppBar(
                title = { /* Empty title - we'll display it in the content */ },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState(message = "Loading privacy policy...")
                }
                uiState.fatalError != null -> {
                    ErrorState(
                        message = uiState.fatalError ?: "An unknown error occurred",
                        onRetry = { /* Reload privacy policy */ }
                    )
                }
                else -> {
                    // Using ContinuousFormContainer for consistent UI
                    ContinuousFormContainer(
                        title = stringResource(id = R.string.privacy_policy_title),
                        subtitle = "Please review our privacy policy before proceeding",
                        progress = 0f, // No progress indicator needed for this screen
                        isLoading = false,
                        modifier = Modifier.fillMaxSize(),
                        // Fixed: Explicitly provide Alignment.CenterHorizontally instead of relying on implicit casting
                        titleAlignment = Alignment.CenterHorizontally
                    ) {
                        // Animated introduction card
                        AnimatedFormSection(
                            title = "", //"Privacy Policy",
                            subtitle = "", //"Please take a moment to review our privacy policy",
                            visible = true,
                            initiallyVisible = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Icon and description
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "Your privacy matters to us",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "We collect and process your personal information to provide you with the best loan experience. Here's how we handle your data:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        FormSectionDivider()
                        
                        // Information We Collect
                        AnimatedFormSection(
                            title = "Information We Collect",
                            visible = true
                        ) {
                            PolicyContent(
                                content = "We collect personal information such as your name, contact details, " +
                                        "identification information, financial information, and employment details " +
                                        "to process your loan application. This includes information from your " +
                                        "credit reports and verification of your identity."
                            )
                        }
                        
                        FormSectionDivider()
                        
                        // How We Use Your Information
                        AnimatedFormSection(
                            title = "How We Use Your Information",
                            visible = true
                        ) {
                            PolicyContent(
                                content = "We use your information to process your loan application, verify your " +
                                        "identity, assess your creditworthiness, communicate with you about your " +
                                        "application, and comply with legal and regulatory requirements."
                            )
                        }
                        
                        FormSectionDivider()
                        
                        // Information Sharing
                        AnimatedFormSection(
                            title = "Information Sharing",
                            visible = true
                        ) {
                            PolicyContent(
                                content = "We may share your information with credit bureaus, identity verification " +
                                        "services, financial institutions, service providers, and regulatory authorities " +
                                        "as required by law or to process your application."
                            )
                        }
                        
                        FormSectionDivider()
                        
                        // Data Security
                        AnimatedFormSection(
                            title = "Data Security",
                            visible = true
                        ) {
                            PolicyContent(
                                content = "We implement appropriate technical and organizational measures to protect " +
                                        "your personal information from unauthorized access, disclosure, alteration, " +
                                        "or destruction."
                            )
                        }
                        
                        FormSectionDivider()
                        
                        // Your Rights
                        AnimatedFormSection(
                            title = "Your Rights",
                            visible = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PolicyContent(
                                    content = "You have the right to access, correct, and delete your personal information, " +
                                            "as well as the right to restrict or object to certain processing of your information. " +
                                            "You may also have the right to data portability."
                                )
                                
                                // Read full policy button
                                TextButton(
                                    onClick = { /* Open full policy */ },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.read_full_policy),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        FormSectionDivider()
                        
                        // Consent Card with animated effects
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Your Consent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                Text(
                                    text = "By clicking 'I Agree', you confirm that you have read and understood our Privacy Policy and consent to the collection and processing of your personal information as described.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                                
                                Button(
                                    onClick = { viewModel.acceptPrivacyPolicy() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.i_agree),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                        
                        // Extra space at the bottom for the AI Assistant bubble
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
            
            // AI Assistant bubble (always visible)
            AIAssistantBubble(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Policy content section with animation
 */
@Composable
fun PolicyContent(
    content: String,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "contentAlpha"
    )
    
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer(alpha = alphaAnim)
        )
    }
}