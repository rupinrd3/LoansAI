package com.loansai.unassisted.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.ui.components.*
import com.loansai.unassisted.viewmodel.AIAssistantViewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri

/**
 * Enhanced Home screen with continuous scrolling design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartNewApplication: () -> Unit,
    onResumeApplication: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    aiViewModel: AIAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Request AI suggestions for the home screen
    LaunchedEffect(key1 = Unit) {
        aiViewModel.getSuggestions("HOME")
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
                title = {
                    Text(
                        text = "Loans AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState(message = "Loading your applications...")
                }
                uiState.fatalError != null -> {
                    ErrorState(
                        message = uiState.fatalError ?: "An unknown error occurred",
                        onRetry = { viewModel.loadUserApplications() }
                    )
                }
                else -> {
                    // Main content using ContinuousFormContainer with reduced top padding
                    ContinuousFormContainer(
                        title = "",  // Title is already in the TopAppBar
                        progress = 0f, // No progress for home screen
                        isLoading = false,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp) // Reduced top padding to fix spacing issue
                        ) {
                            // Welcome banner directly in the column with reduced spacing
                            WelcomeBanner(userName = uiState.userName)

                            Spacer(modifier = Modifier.height(16.dp))

                            // Start New Application button directly in the main column
                            Button(
                                onClick = {
                                    if (uiState.currentApplication != null) {
                                        viewModel.showVoidConfirmation()
                                    } else {
                                        viewModel.createNewApplication(onSuccess = onStartNewApplication)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text("Start New Loan Application")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Complete your application in just a few minutes with our simplified process.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Application Card (if exists)
                            if (uiState.currentApplication != null) {
                                EnhancedApplicationCard(
                                    application = uiState.currentApplication!!,
                                    onResumeClick = { 
                                        // Call verifyAndResumeApplication instead of directly navigating
                                        viewModel.verifyAndResumeApplication { route ->
                                            onResumeApplication(route)
                                        }
                                    },
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            FormSectionDivider()

                            // Features Overview section
                            AnimatedFormSection(
                                title = "Why Choose Our Loans",
                                visible = true
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FeatureCard(
                                            icon = Icons.Outlined.Speed,
                                            title = "Fast Approval",
                                            subtitle = "Get instant offers",
                                            modifier = Modifier.weight(1f)
                                        )

                                        FeatureCard(
                                            icon = Icons.Outlined.Description,
                                            title = "Minimal Docs",
                                            subtitle = "Easy verification",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FeatureCard(
                                            icon = Icons.Outlined.Payments,
                                            title = "Competitive Rates",
                                            subtitle = "Starting at 10.99%",
                                            modifier = Modifier.weight(1f)
                                        )

                                        FeatureCard(
                                            icon = Icons.Outlined.Timer,
                                            title = "Flexible Tenure",
                                            subtitle = "12 to 60 months",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            FormSectionDivider()

                            // Loan Eligibility section
                            AnimatedFormSection(
                                title = "Check Your Eligibility",
                                visible = true
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Find out how much loan you can get based on your profile",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Eligibility criteria
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EligibilityCriteriaItem(text = "Minimum age: 21 years")
                                        EligibilityCriteriaItem(text = "Minimum income: ₹25,000 per month")
                                        EligibilityCriteriaItem(text = "Credit score: 680+")
                                        EligibilityCriteriaItem(text = "Employment: Minimum 1 year")
                                    }

                                    OutlinedButton(
                                        onClick = { /* Open eligibility calculator */ },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                        )
                                    ) {
                                        Text("Check Eligibility")

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.Default.Calculate,
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            FormSectionDivider()

                            // Need Help section
                            AnimatedFormSection(
                                title = "Need Help?",
                                visible = true
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        HelpOptionCard(
                                            icon = Icons.Default.Chat,
                                            title = "Chat Support",
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                // Open AI Assistant chat
                                                coroutineScope.launch {
                                                    aiViewModel.getSuggestions("HOME_HELP")
                                                }
                                            }
                                        )

                                        HelpOptionCard(
                                            icon = Icons.Default.Call,
                                            title = "Call Us",
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                // Launch phone dialer with support number
                                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:+911234567890") // Replace with actual support number
                                                }
                                                if (intent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        HelpOptionCard(
                                            icon = Icons.Default.Email,
                                            title = "Email Support",
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                // Launch email compose with support email
                                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("mailto:support@loansai.com") // Replace with actual support email
                                                    putExtra(Intent.EXTRA_SUBJECT, "Loan Application Support Request")
                                                }
                                                if (intent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )

                                        HelpOptionCard(
                                            icon = Icons.Default.Help,
                                            title = "FAQ",
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                // Open FAQ section or page
                                                coroutineScope.launch {
                                                    aiViewModel.getSuggestions("HOME_FAQ")
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Extra space at the bottom for the AI Assistant bubble
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Show confirmation dialog for voiding existing application
                    if (uiState.showVoidConfirmation) {
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissVoidConfirmation() },
                            title = { Text("Start New Application?") },
                            text = {
                                Text(
                                    "Starting a new application will void your existing application. Are you sure you want to continue?",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.dismissVoidConfirmation()
                                        viewModel.createNewApplication(onSuccess = onStartNewApplication)
                                    }
                                ) {
                                    Text("Continue")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { viewModel.dismissVoidConfirmation() }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            // AI Assistant bubble moved inside the Box so that align() works within BoxScope
            AIAssistantBubble(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Enhanced welcome banner with animation
 */
@Composable
private fun WelcomeBanner(userName: String) {
    val nameToDisplay = if (userName.isNotEmpty()) userName else "there"

    // Animation for welcome text
    var startAnimation by remember { mutableStateOf(false) }
    val textSlideAnim by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -100f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "textSlideAnim"
    )
    val textAlphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(700),
        label = "textAlphaAnim"
    )

    // Trigger animation
    LaunchedEffect(key1 = true) {
        startAnimation = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp) // Reduced vertical padding to fix spacing
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    translationY = textSlideAnim
                    alpha = textAlphaAnim
                }
        ) {
            Text(
                text = "Welcome, $nameToDisplay!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Let's get your loan journey started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Enhanced application card with progress
 */
@Composable
private fun EnhancedApplicationCard(
    application: LoanApplication,
    onResumeClick: () -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    // Calculate progress percentage
    val progress = remember(application) {
        val completedSteps = application.completedSteps.size.toFloat()
        val totalSteps = 7f // Total number of steps in the loan application process
        (completedSteps / totalSteps).coerceIn(0f, 1f)
    }

    // Animate progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progressAnimation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Resume button at the top
            Button(
                onClick = onResumeClick, // Use the passed onResumeClick function
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(text = "Resume Application")
            }

            // Divider line
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Application details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Application ID
                DetailRow(
                    label = "Application ID:",
                    value = application.id.take(8)
                )

                // Last updated
                DetailRow(
                    label = "Last Updated:",
                    value = application.lastUpdatedAt.format(dateFormatter)
                )

                // Status
                DetailRow(
                    label = "Status:",
                    value = application.applicationStatus.name.replace("_", " "),
                    valueColor = getStatusColor(application.applicationStatus)
                )

                // Current step
                Text(
                    text = "Current Step: ${application.currentStep.name.replace("_", " ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${(animatedProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Use the animatedProgress directly (not as a lambda)
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Feature card with consistent styling
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Help option card with consistent styling
 */
@Composable
private fun HelpOptionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit // Added onClick parameter
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick), // Use the passed onClick function
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Eligibility criteria item with icon
 */
@Composable
private fun EligibilityCriteriaItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Detail row with label and value
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

/**
 * Get appropriate color for application status
 */
@Composable
private fun getStatusColor(status: ApplicationStatus): Color {
    return when (status) {
        ApplicationStatus.APPROVED -> MaterialTheme.colorScheme.primary
        ApplicationStatus.REJECTED -> MaterialTheme.colorScheme.error
        ApplicationStatus.CANCELLED,
        ApplicationStatus.EXPIRED -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }
}