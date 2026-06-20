package com.loansai.unassisted.ui.screens.bureau

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.domain.model.TradelineItem
import com.loansai.unassisted.ui.components.ContinuousFormContainer
import com.loansai.unassisted.ui.components.ModernBackButton
import com.loansai.unassisted.util.logger.AppLogger
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle 
import androidx.lifecycle.repeatOnLifecycle 



/**
 * Bureau Confirmation Screen
 * 
 * This screen displays tradelines from the bureau report and allows the user
 * to confirm or update EMI amounts for open loans. The user can also provide
 * comments explaining any discrepancies or providing additional context.
 * 
 * The screen submits the data to the backend for LLM-based obligation recalculation.
 */
@Composable
fun BureauConfirmationScreen(
    viewModel: BureauConfirmationViewModel = hiltViewModel(),
    onNextClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // Show tutorial dialog on first visit
    var showTutorial by remember { mutableStateOf(true) }
    
    // Effect to check for completed recalculation or auto-navigate
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(uiState.processingState, uiState.autoNavigate, lifecycleOwner) {
        // repeatOnLifecycle ensures this coroutine respects the composable's lifecycle
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
             // Combine conditions: Navigate if autoNavigate is true (handles errors/timeouts)
             // OR if processing just completed successfully explicitly.
             if (uiState.autoNavigate || uiState.processingState == ProcessingState.PROCESSING_COMPLETE) {
                  // Check autoNavigate first as it handles timeouts/errors implicitly now via ViewModel
                  if(uiState.autoNavigate) {
                     AppLogger.d("Navigation triggered by autoNavigate flag.")
                 } else {
                      // This path is now mainly for explicit success completion
                      AppLogger.d("Navigation triggered by PROCESSING_COMPLETE state.")
                  }
                 onNextClick()
                 // Consider resetting autoNavigate flag in ViewModel if needed after navigation,
                 // though navigating away usually makes this unnecessary.
                 // viewModel.resetAutoNavigate() // Example if needed
             }
        }
    }
    
    // Main content
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ContinuousFormContainer(
            title = if (uiState.noTradelinesFound) 
                "No Active Loans Found" 
            else 
                "Confirm Your EMIs",
            subtitle = if (uiState.noTradelinesFound) 
                "We couldn't find any active loans in your bureau report" 
            else 
                "Please verify the monthly EMI amounts for your active loans",
            progress = 0.6f,
            isLoading = uiState.isLoading
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Info card at the top
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.noTradelinesFound) Icons.Default.Info else Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (uiState.noTradelinesFound) 
                                "No active loans were found in your credit report. You can proceed to the next step." 
                            else 
                                "Verifying your EMI amounts helps us accurately calculate your loan eligibility.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Different content based on whether tradelines were found
                if (uiState.noTradelinesFound) {
                    // Show "No tradelines" state with option to continue
                    NoTradelinesContent(viewModel)
                } else {
                    // Normal tradeline list view
                    TradelinesContent(uiState, viewModel, focusManager)
                }
                
                // Error message if any
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    uiState.errorMessage?.let { error ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    ModernBackButton(onBack = onBackClick)
                    

                    // Continue button - Now disabled during processing
                    Button(
                        onClick = { 
                            AppLogger.d("Continue button clicked")
                            if (uiState.noTradelinesFound) {
                                viewModel.continueWithoutTradelines()
                            } else {
                                viewModel.submitObligationRefinement()
                            }
                        },
                        enabled = !uiState.isLoading && 
                                uiState.processingState == ProcessingState.IDLE && 
                                (!uiState.hasValidationErrors || uiState.noTradelinesFound),
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Continue")
                        Spacer(modifier = Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Skip button for development only
                if (uiState.isDevMode) {
                    TextButton(
                        onClick = { 
                            AppLogger.d("Skip (Dev) button clicked")
                            viewModel.continueWithoutTradelines() 
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp)
                    ) {
                        Text("Skip (Dev Only)")
                    }
                }
                
                // Extra space at bottom
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Add the processing overlay
    ProcessingOverlay(
        state = uiState,
        onRetry = { viewModel.retryProcessing() }
    )
    
    // Tutorial dialog
    if (showTutorial && !uiState.noTradelinesFound) {
        BureauConfirmationTutorialDialog(
            onDismiss = { showTutorial = false }
        )
    }
}

/**
 * Content to display when no tradelines are found
 */
@Composable
fun NoTradelinesContent(viewModel: BureauConfirmationViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No loans found",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No active loans found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "We couldn't find any active loans in your credit bureau report. You can proceed to the next step.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Content to display when tradelines are found
 */
@Composable
fun TradelinesContent(
    uiState: BureauConfirmationUiState,
    viewModel: BureauConfirmationViewModel,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    // Tradeline list
    if (uiState.tradelines.isEmpty() && !uiState.noTradelinesFound) {
        // Show loading state
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = "No active loans found in your bureau report",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Summary information
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Found ${uiState.tradelines.size} active loans",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Please verify or update the monthly EMI amount for each loan below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Tradeline list with input fields for EMI
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 350.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = uiState.tradelines,
                key = { it.id }
            ) { tradeline ->
                TradelineItemRow(
                    tradeline = tradeline,
                    emiValue = uiState.emiValues[tradeline.id] ?: "",
                    isError = uiState.emiErrors.contains(tradeline.id),
                    onEmiChange = { newValue ->
                        viewModel.updateEmiValue(tradeline.id, newValue)
                    },
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Comments section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Additional Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Please explain any differences or if you've closed any loans recently",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Multi-line comments field
            OutlinedTextField(
                value = uiState.comments,
                onValueChange = viewModel::updateComments,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Enter your comments here...") },
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

/**
 * Individual tradeline item row with EMI input
 */
@Composable
fun TradelineItemRow(
    tradeline: TradelineItem,
    emiValue: String,
    isError: Boolean,
    onEmiChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Row with lender information
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lender name and account type
            Column(
                modifier = Modifier.weight(2f)
            ) {
                Text(
                    text = tradeline.memberName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tradeline.accountType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Account Number (last 4 digits)
            val maskedAccount = if (tradeline.accountNumber.length > 4) {
                "xxxx${tradeline.accountNumber.takeLast(4)}"
            } else {
                tradeline.accountNumber
            }
            
            Text(
                text = maskedAccount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Row with balance and EMI
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Balance column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Current Balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹${tradeline.currentBalance}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // EMI input column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Monthly EMI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // EMI input field
                OutlinedTextField(
                    value = emiValue,
                    onValueChange = { newValue ->
                        // Allow only numbers and empty string
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            onEmiChange(newValue)
                        }
                    },
                    placeholder = { Text("Enter EMI amount") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onNext() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp
                    ),
                    prefix = { Text("₹") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                // Show error if needed
                if (isError) {
                    Text(
                        text = "Required field",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
    
    Divider(
        modifier = Modifier.padding(top = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Tutorial dialog shown on first visit
 */
@Composable
fun BureauConfirmationTutorialDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Confirm Your EMIs")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "We've found active loans in your credit report. Please verify your monthly EMI amounts for accurate loan eligibility calculation."
                )
                
                Text(
                    "For closed loans or if there are any discrepancies, please add a comment in the section below."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Got it")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * UI overlay showing processing status
 */
@Composable
private fun ProcessingOverlay(
    state: BureauConfirmationUiState,
    onRetry: () -> Unit
) {
    if (state.processingState != ProcessingState.IDLE) {
        // Translucent background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Different content based on processing state
                    when (state.processingState) {
                        ProcessingState.SAVING_DATA, ProcessingState.GEMINI_PROCESSING -> {
                            // Show progress indicator
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = state.processingMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        ProcessingState.PROCESSING_COMPLETE -> {
                            // Show success icon
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Processing complete!",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = "Continuing to next step...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        ProcessingState.PROCESSING_ERROR -> {
                            // Show error icon
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Processing Error",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = state.processingMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                        
                        else -> { /* No content for IDLE */ }
                    }
                }
            }
        }
    }
}