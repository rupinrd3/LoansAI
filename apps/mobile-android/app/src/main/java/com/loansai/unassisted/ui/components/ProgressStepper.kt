package com.loansai.unassisted.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loansai.unassisted.domain.model.ApplicationStep

/**
 * Progress stepper component for tracking application progress
 */
@Composable
fun ProgressStepper(
    currentStep: ApplicationStep,
    completedSteps: List<ApplicationStep>,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false
) {
    if (isVertical) {
        VerticalProgressStepper(
            currentStep = currentStep,
            completedSteps = completedSteps,
            modifier = modifier
        )
    } else {
        HorizontalProgressStepper(
            currentStep = currentStep,
            completedSteps = completedSteps,
            modifier = modifier
        )
    }
}

/**
 * Overloaded ProgressStepper that accepts Int parameters instead of ApplicationStep
 */
@Composable
fun ProgressStepper(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false
) {
    // Calculate completion progress based on current step
    val completionProgress = (currentStep.toFloat() / totalSteps)
    val animatedProgress by animateFloatAsState(
        targetValue = completionProgress,
        label = "progressAnimation"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Progress percentage text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Application Progress",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${(completionProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Step circles with connecting lines
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Draw step circles with connecting lines
            for (i in 1..totalSteps) {
                val isCompleted = i < currentStep
                val isCurrent = i == currentStep
                
                StepCircle(
                    stepNumber = i,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent
                )
                
                // Draw connector line (except after the last step)
                if (i < totalSteps) {
                    val nextStepCompleted = i + 1 <= currentStep
                    
                    StepConnector(
                        isCompleted = isCompleted && nextStepCompleted
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Current step name
        Text(
            text = formatStepName(currentStep),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Horizontal progress stepper
 */
@Composable
fun HorizontalProgressStepper(
    currentStep: ApplicationStep,
    completedSteps: List<ApplicationStep>,
    modifier: Modifier = Modifier
) {
    // Define all steps in order
    val allSteps = listOf(
        ApplicationStep.PAN_VERIFICATION,
        ApplicationStep.PERSONAL_INFO,
        ApplicationStep.EMPLOYMENT_DETAILS,
        ApplicationStep.DOCUMENT_UPLOAD,
        ApplicationStep.LOAN_OFFER,
        ApplicationStep.EMPLOYMENT_VERIFICATION,
        ApplicationStep.REVIEW_AND_SUBMIT
    )
    
    // Calculate progress percentage
    val completionProgress = (completedSteps.size.toFloat() / allSteps.size)
    val animatedProgress by animateFloatAsState(
        targetValue = completionProgress,
        label = "progressAnimation"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Progress percentage text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Application Progress",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${(completionProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Step circles with connecting lines
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Draw step circles with connecting lines
            for (i in allSteps.indices) {
                val step = allSteps[i]
                val isCompleted = completedSteps.contains(step)
                val isCurrent = step == currentStep
                
                StepCircle(
                    stepNumber = i + 1,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent
                )
                
                // Draw connector line (except after the last step)
                if (i < allSteps.size - 1) {
                    val nextStepCompleted = completedSteps.contains(allSteps[i + 1])
                    
                    StepConnector(
                        isCompleted = isCompleted && (nextStepCompleted || currentStep == allSteps[i + 1])
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Current step name
        Text(
            text = formatStepName(currentStep),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Vertical progress stepper
 */
@Composable
fun VerticalProgressStepper(
    currentStep: ApplicationStep,
    completedSteps: List<ApplicationStep>,
    modifier: Modifier = Modifier
) {
    // Define all steps in order
    val allSteps = listOf(
        ApplicationStep.PAN_VERIFICATION,
        ApplicationStep.PERSONAL_INFO,
        ApplicationStep.EMPLOYMENT_DETAILS,
        ApplicationStep.DOCUMENT_UPLOAD,
        ApplicationStep.LOAN_OFFER,
        ApplicationStep.EMPLOYMENT_VERIFICATION,
        ApplicationStep.REVIEW_AND_SUBMIT
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Display each step with status
        for (i in allSteps.indices) {
            val step = allSteps[i]
            val isCompleted = completedSteps.contains(step)
            val isCurrent = step == currentStep
            
            // Step row with indicator and text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step indicator
                StepCircle(
                    stepNumber = i + 1,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent,
                    size = 28.dp
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Step text
                Column {
                    Text(
                        text = formatStepName(step),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent || isCompleted) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isCompleted -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    
                    // Optional step description
                    if (isCurrent) {
                        Text(
                            text = getStepDescription(step),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Connector line (except after the last step)
            if (i < allSteps.size - 1) {
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .width(2.dp)
                        .height(24.dp)
                        .background(
                            color = if (isCompleted) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

/**
 * Individual step circle component
 */
@Composable
fun StepCircle(
    stepNumber: Int,
    isCompleted: Boolean,
    isCurrent: Boolean,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.primary
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "bgColorAnimation"
    )
    
    val textColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.onPrimary
            isCurrent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        label = "textColorAnimation"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            // Check icon for completed step
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(size / 2)
            )
        } else {
            // Step number for incomplete step
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

/**
 * Connector line between steps
 */
@Composable
fun StepConnector(isCompleted: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
        label = "connectorColorAnimation"
    )
    
    Canvas(
        modifier = modifier
            .width(24.dp)
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Format step name for display based on ApplicationStep enum
 */
private fun formatStepName(step: ApplicationStep): String {
    return when (step) {
        ApplicationStep.PAN_VERIFICATION -> "PAN Verification"
        ApplicationStep.PERSONAL_INFO -> "Personal Information"
        ApplicationStep.EMPLOYMENT_DETAILS -> "Employment Details"
        ApplicationStep.DOCUMENT_UPLOAD -> "Document Upload"
        ApplicationStep.LOAN_OFFER -> "Loan Offer"
        ApplicationStep.EMPLOYMENT_VERIFICATION -> "Employment Verification"
        ApplicationStep.REVIEW_AND_SUBMIT -> "Review & Submit"
        ApplicationStep.LOGIN -> "Login"
        ApplicationStep.PRIVACY_POLICY -> "Privacy Policy"
        ApplicationStep.HOME -> "Home"
        ApplicationStep.BUREAU_CONFIRMATION -> "Bureau Confirmation"
        ApplicationStep.KEY_FACT_SHEET -> "Key Fact Sheet"
        ApplicationStep.APPLICATION_SUBMITTED -> "Application Submitted"
        else -> step.name
    }
}

/**
 * Format step name for display based on step number
 */
private fun formatStepName(stepNumber: Int): String {
    return when (stepNumber) {
        1 -> "PAN Verification"
        2 -> "Personal Information"
        3 -> "Employment Details"
        4 -> "Document Upload"
        5 -> "Loan Offer"
        6 -> "Employment Verification"
        7 -> "Review & Submit"
        else -> "Step $stepNumber"
    }
}

/**
 * Get step description
 */
private fun getStepDescription(step: ApplicationStep): String {
    return when (step) {
        ApplicationStep.PAN_VERIFICATION -> "Verify your PAN card details"
        ApplicationStep.PERSONAL_INFO -> "Fill in your personal information"
        ApplicationStep.EMPLOYMENT_DETAILS -> "Provide your employment details"
        ApplicationStep.DOCUMENT_UPLOAD -> "Upload required documents"
        ApplicationStep.LOAN_OFFER -> "Review your personalized loan offer"
        ApplicationStep.EMPLOYMENT_VERIFICATION -> "Verify your employment details"
        ApplicationStep.REVIEW_AND_SUBMIT -> "Review and submit your application"
        ApplicationStep.LOGIN -> "Login to your account"
        ApplicationStep.PRIVACY_POLICY -> "Review privacy policy"
        ApplicationStep.HOME -> "Home screen"
        ApplicationStep.BUREAU_CONFIRMATION -> "Confirm bureau details"
        ApplicationStep.KEY_FACT_SHEET -> "Review key fact sheet"
        ApplicationStep.APPLICATION_SUBMITTED -> "Application submitted"
        else -> "Step description"
    }
}