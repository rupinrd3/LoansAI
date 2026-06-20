package com.loansai.unassisted.ui.screens.offer

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.ui.components.*
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex

/**
 * Loan Offer Screen with continuous scrolling form
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanOfferScreen(
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: LoanOfferViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // Track when user navigates away from screen
    DisposableEffect(key1 = viewModel) {
        // This is called when the composable enters the composition
        
        // This is called when the composable leaves the composition
        onDispose {
            // End screen visit tracking when user navigates away
            viewModel.endScreenVisit()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Empty title - we'll display it in the content */ },
                navigationIcon = {
                    // Modern back button with a circle background using ChevronLeft
                    IconButton(
                        onClick = {
                            // End screen visit tracking before navigating back
                            viewModel.endScreenVisit()
                            onBackClick()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Using AutoMirrored version for Chevron Left
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
        val currentStep = 5 // Loan offer is step 5
        val totalSteps = 7 
        val progress = currentStep.toFloat() / totalSteps.toFloat()

        Box(modifier = Modifier.fillMaxSize()) {
            // Main content using ContinuousFormContainer
            ContinuousFormContainer(
                title = "Loan Offer",
                subtitle = "Customize your loan to fit your needs",
                progress = progress,
                isLoading = state.offerStatus == LoanOfferUI.Status.LOADING,
                modifier = Modifier.padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                )
            ) {
                // Content based on offer status
                when (state.offerStatus) {
                    LoanOfferUI.Status.APPROVED -> {
                        ApprovedOfferContent(
                            state = state,
                            onLoanAmountChange = viewModel::updateLoanAmount,
                            onTenureChange = viewModel::updateTenure
                        )
                    }
                    
                    LoanOfferUI.Status.REJECTED -> {
                        RejectedOfferContent(
                            errorMessage = state.errorMessage
                        )
                    }
                    
                    LoanOfferUI.Status.REFERRAL -> {
                        ReferralOfferContent(
                            state = state,
                            onLoanAmountChange = viewModel::updateLoanAmount,
                            onTenureChange = viewModel::updateTenure
                        )
                    }
                    
                    LoanOfferUI.Status.ERROR -> {
                        // Show Referral content for error cases as requested
                        ReferralOfferContent(
                            state = state,
                            onLoanAmountChange = viewModel::updateLoanAmount,
                            onTenureChange = viewModel::updateTenure
                        )
                    }
                    
                    LoanOfferUI.Status.LOADING -> {
                        // Loading is handled by the ContinuousFormContainer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Calculating your personalized loan offer...",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Action button (for all cases now)
                if (state.offerStatus != LoanOfferUI.Status.LOADING) {
                    FormSectionDivider()
                    
                    if (state.offerStatus == LoanOfferUI.Status.ERROR) {
                        // Add retry button for error state
                        Button(
                            onClick = { viewModel.retryLoanOffer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Loan Calculation")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Button(
                        onClick = {
                            viewModel.saveProgress() 
                            onNextClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text("Continue to Employment Verification")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
                
                // Extra space at the bottom for the AI Assistant bubble
                Spacer(modifier = Modifier.height(100.dp))
            }
            
            // AI Assistant bubble - Fixed positioning to avoid overlap with navigation
            AIAssistantBubble(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp) // Increased bottom padding to avoid overlap
                    .zIndex(10f), // Add higher z-index to ensure it's above other elements
                onClick = { viewModel.showAIAssistant() }
            )

            // Skip to next screen button (for dev/testing only)
            IconButton(
                onClick = { 
                    viewModel.bypassApproval()
                    viewModel.saveProgress() // This will track metadata before navigation
                    onNextClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Screen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


/**
 * Content for approved loan offers
 */
@Composable
private fun ApprovedOfferContent(
    state: LoanOfferState,
    onLoanAmountChange: (Float) -> Unit,
    onTenureChange: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Congratulations card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Congratulations!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Your loan has been approved",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            FormSectionDivider()
            
            // Loan Amount Section
            AnimatedFormSection(
                title = "Customize Your Loan",
                subtitle = "Adjust the amount and tenure to suit your needs",
                visible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Loan Amount Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current selected amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Loan Amount",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = formatCurrency(state.selectedLoanAmount.toInt()),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Slider
                        Slider(
                            value = state.selectedLoanAmount,
                            onValueChange = onLoanAmountChange,
                            valueRange = state.minLoanAmount..state.maxLoanAmount,
                            steps = ((state.maxLoanAmount - state.minLoanAmount) / 5000).toInt(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        // Min and max labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatCurrency(state.minLoanAmount.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = formatCurrency(state.maxLoanAmount.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Tenure Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current selected tenure
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Loan Tenure",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "${state.selectedTenure.toInt()} months",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Slider
                        Slider(
                            value = state.selectedTenure,
                            onValueChange = onTenureChange,
                            valueRange = state.minTenure..state.maxTenure,
                            steps = (state.maxTenure - state.minTenure).toInt(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        // Min and max labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.minTenure.toInt()} months",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "${state.maxTenure.toInt()} months",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            FormSectionDivider()
            
            // EMI Details Section
            AnimatedFormSection(
                title = "Loan Summary",
                subtitle = "Based on your selected amount and tenure",
                visible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Monthly EMI highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Monthly EMI",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = formatCurrency(state.calculatedEMI),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Other loan details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LoanDetailRow(
                            label = "Interest Rate",
                            value = "${state.interestRate}%"
                        )
                        
                        LoanDetailRow(
                            label = "Processing Fee",
                            value = formatCurrency(state.processingFee)
                        )
                        
                        LoanDetailRow(
                            label = "Total Interest",
                            value = calculateTotalInterest(state.calculatedEMI, state.selectedTenure.toInt(), state.selectedLoanAmount.toInt())
                        )
                        
                        LoanDetailRow(
                            label = "Total Repayment",
                            value = calculateTotalRepayment(state.calculatedEMI, state.selectedTenure.toInt()),
                            highlight = true
                        )
                    }
                }
            }
            
            // Terms and conditions notice
            Text(
                text = "By proceeding, you agree to the loan terms and conditions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Content for rejected loan offers
 */
@Composable
private fun RejectedOfferContent(
    errorMessage: String = "We're sorry, but we are unable to offer you a loan at this time based on the information provided."
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Application Not Approved",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Common reasons include credit history, income requirements, or existing loan obligations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { /* Navigate to customer support */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = "Contact Support")
                }
            }
        }
    }
}

/**
 * Content for referral loan offers - Updated to include sliders
 */
@Composable
private fun ReferralOfferContent(
    state: LoanOfferState,
    onLoanAmountChange: (Float) -> Unit,
    onTenureChange: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pending,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Additional Review Needed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    
                    Text(
                        text = state.errorMessage.ifEmpty { 
                            "Your application requires additional review by our team. Please select your preferred loan amount and tenure below."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            FormSectionDivider()
            
            // Loan Amount and Tenure Sliders - Similar to Approved content
            AnimatedFormSection(
                title = "Select Your Preferred Loan",
                subtitle = "Choose your ideal loan amount and tenure",
                visible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Loan Amount Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current selected amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Requested Loan Amount",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = formatCurrency(state.selectedLoanAmount.toInt()),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Slider
                        Slider(
                            value = state.selectedLoanAmount,
                            onValueChange = onLoanAmountChange,
                            valueRange = state.minLoanAmount..state.maxLoanAmount,
                            steps = ((state.maxLoanAmount - state.minLoanAmount) / 5000).toInt(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        // Min and max labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatCurrency(state.minLoanAmount.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = formatCurrency(state.maxLoanAmount.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Tenure Slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current selected tenure
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Requested Loan Tenure",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "${state.selectedTenure.toInt()} months",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Slider
                        Slider(
                            value = state.selectedTenure,
                            onValueChange = onTenureChange,
                            valueRange = state.minTenure..state.maxTenure,
                            steps = (state.maxTenure - state.minTenure).toInt(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        // Min and max labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.minTenure.toInt()} months",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "${state.maxTenure.toInt()} months",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            FormSectionDivider()
            
            // EMI Details Section - Similar to Approved content
            AnimatedFormSection(
                title = "Loan Summary",
                subtitle = "Based on your selected amount and tenure",
                visible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Monthly EMI highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Estimated Monthly EMI",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = formatCurrency(state.calculatedEMI),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Other loan details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LoanDetailRow(
                            label = "Interest Rate",
                            value = "${state.interestRate}%"
                        )
                        
                        LoanDetailRow(
                            label = "Processing Fee",
                            value = formatCurrency(state.processingFee)
                        )
                        
                        LoanDetailRow(
                            label = "Total Interest",
                            value = calculateTotalInterest(state.calculatedEMI, state.selectedTenure.toInt(), state.selectedLoanAmount.toInt())
                        )
                        
                        LoanDetailRow(
                            label = "Total Repayment",
                            value = calculateTotalRepayment(state.calculatedEMI, state.selectedTenure.toInt()),
                            highlight = true
                        )
                    }
                }
            }
            
            // Terms and conditions notice
            Text(
                text = "By proceeding, you agree to the loan terms and conditions. Your application will be reviewed by our team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Row for displaying loan detail
 */
@Composable
private fun LoanDetailRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = value,
            style = if (highlight) 
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) 
            else 
                MaterialTheme.typography.bodyMedium,
            color = if (highlight) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Format currency to Indian Rupee format
 */
private fun formatCurrency(amount: Int): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.currency = Currency.getInstance("INR")
    return formatter.format(amount.toDouble())
}

/**
 * Calculate total interest paid
 */
private fun calculateTotalInterest(monthlyEmi: Int, tenure: Int, principal: Int): String {
    val totalPayment = monthlyEmi * tenure
    val totalInterest = totalPayment - principal
    return formatCurrency(totalInterest)
}

/**
 * Calculate total repayment amount
 */
private fun calculateTotalRepayment(monthlyEmi: Int, tenure: Int): String {
    val totalPayment = monthlyEmi * tenure
    return formatCurrency(totalPayment)
}