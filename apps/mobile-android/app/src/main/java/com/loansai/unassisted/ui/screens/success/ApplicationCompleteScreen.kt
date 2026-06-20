package com.loansai.unassisted.ui.screens.success

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.ui.components.*
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.background
import kotlinx.coroutines.delay



/**
 * Application Complete screen with success animation and next steps
 */
@Composable
fun ApplicationCompleteScreen(
    onHomeClick: () -> Unit,
    viewModel: ApplicationCompleteViewModel = hiltViewModel()
) {
    val successColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Animation states
    var playAnimation by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition(label = "backgroundPulse")
    
    // Pulsing background animation
    val backgroundScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Success check mark animation
    val checkScale by animateFloatAsState(
        targetValue = if (playAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )
    
    // Confetti animation
    LaunchedEffect(Unit) {
        // Trigger the animations
        playAnimation = true
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backgroundScale
                    scaleY = backgroundScale
                },
            contentAlignment = Alignment.Center
        ) {
            // Success content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Success animation
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(successColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = successColor,
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            }
                    )
                }
                
                // Success text
                Text(
                    text = "Application Submitted!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Your loan application has been successfully submitted for processing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                // Application Reference Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Application Reference",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "LOAN-2025-04892",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "Please save this reference number for future inquiries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Next steps card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "What Happens Next?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Steps list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            NextStep(
                                number = 1,
                                title = "Application Review",
                                description = "Our team will review your application details and documents."
                            )
                            
                            NextStep(
                                number = 2,
                                title = "Verification Call",
                                description = "You may receive a call for verification within 24-48 hours."
                            )
                            
                            NextStep(
                                number = 3,
                                title = "Final Approval",
                                description = "Once verified, final approval and disbursement will be processed."
                            )
                        }
                    }
                }
                
                // Back to home button
                Button(
                    onClick = onHomeClick,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Home")
                }
            }
        }
        
        // Confetti animation (simulated with colorful dots)
        if (playAnimation) {
            SuccessConfetti()
        }
        
        // AI Assistant bubble (optional on this screen)
        AIAssistantBubble(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/**
 * Single step item for the "What Happens Next" section
 */
@Composable
private fun NextStep(
    number: Int,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Step content
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Colorful confetti effect for success celebration
 */
@Composable
private fun SuccessConfetti() {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        Color(0xFFFFC107), // Amber
        Color(0xFF4CAF50)  // Green
    )
    
    // Create 30 confetti particles
    for (i in 0 until 30) {
        val delay = i * 50 // Staggered start
        val duration = (2000..4000).random()
        val color = colors[i % colors.size]
        
        ConfettiParticle(
            delay = delay,
            duration = duration,
            color = color
        )
    }
}

/**
 * Single confetti particle with animation
 */
@Composable
private fun ConfettiParticle(
    delay: Int,
    duration: Int,
    color: Color
) {
    val particleSize = (8..16).random().dp
    
    // Random starting position at the top of the screen
    val initialOffsetX = (-100..100).random().dp
    
    // Animation values
    val animatable = remember { Animatable(0f) }
    
    LaunchedEffect(key1 = true) {
        delay(delay.toLong())
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = duration,
                easing = FastOutLinearInEasing
            )
        )
    }
    
    // Calculate particle position based on animation progress
    val offsetY = 700.dp * animatable.value
    val offsetX = initialOffsetX + (50.dp * kotlin.math.sin(10 * animatable.value))
    val rotation = 360f * animatable.value
    val alpha = 1f - min(1f, max(0f, animatable.value))
    
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(particleSize)
            .clip(RoundedCornerShape(50))
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
            }
            .background(color)
    )
}