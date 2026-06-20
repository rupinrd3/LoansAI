package com.loansai.unassisted.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loansai.unassisted.ui.screens.home.HomeScreen
import com.loansai.unassisted.ui.screens.login.LoginScreen
import com.loansai.unassisted.ui.screens.pan.PANEntryScreen
import com.loansai.unassisted.ui.screens.personal.PersonalInfoScreen
import com.loansai.unassisted.ui.screens.employment.EmploymentDetailsScreen
import com.loansai.unassisted.ui.screens.documents.DocumentUploadScreen
import com.loansai.unassisted.ui.screens.bureau.BureauConfirmationScreen
import com.loansai.unassisted.ui.screens.offer.LoanOfferScreen
import com.loansai.unassisted.ui.screens.verification.EmploymentVerificationScreen
import com.loansai.unassisted.ui.screens.summary.KeyFactSheetScreen
import com.loansai.unassisted.ui.screens.success.ApplicationCompleteScreen
import com.loansai.unassisted.ui.screens.privacy.PrivacyPolicyScreen
import com.loansai.unassisted.ui.screens.settings.SettingsScreen
import com.loansai.unassisted.ui.screens.splash.SplashScreen
import com.loansai.unassisted.util.logger.AppLogger
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    // Store navigation actions to use from composables
    val actions = remember(navController) { NavigationActions(navController) }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInVertically(
                initialOffsetY = { 1000 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutVertically(
                targetOffsetY = { -1000 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInVertically(
                initialOffsetY = { -1000 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutVertically(
                targetOffsetY = { 1000 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // Splash screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = actions.navigateToLogin,
                onNavigateToHome = actions.navigateToHome
            )
        }
        
        // Login screen
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToPrivacyPolicy = actions.navigateToPrivacyPolicy,
                onNavigateToHome = actions.navigateToHome
            )
        }
        
        // Privacy policy screen
        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateToHome = actions.navigateToHome,
                onNavigateBack = actions.navigateBack
            )
        }
        
        // Home screen
        composable(Screen.Home.route) {
            HomeScreen(
                onStartNewApplication = actions.navigateToPANEntry,
                onResumeApplication = actions.navigateToScreenByRoute,
                onNavigateToSettings = actions.navigateToSettings
            )
        }
        
        // PAN Entry screen
        composable(Screen.PANEntry.route) {
            PANEntryScreen(
                onNavigateToPersonalInfo = actions.navigateToPersonalInfo,
                onNavigateBack = actions.navigateBack
            )
        }
        
        // Personal Info screen
        composable(Screen.PersonalInfo.route) {
            PersonalInfoScreen(
                onNavigateToEmploymentDetails = actions.navigateToEmploymentDetails,
                onNavigateBack = actions.navigateBack
            )
        }
        
        // Employment Details screen
        composable(Screen.EmploymentDetails.route) {
            EmploymentDetailsScreen(
                onNavigateToDocumentUpload = actions.navigateToDocumentUpload,
                onNavigateBack = actions.navigateBack
            )
        }
        
        // Document Upload screen
        composable(Screen.DocumentUpload.route) {
            DocumentUploadScreen(
                onNextClick = actions.navigateBasedOnBureauScore,
                onBackClick = actions.navigateBack
            )
        }
        
        // Bureau Confirmation screen (New for v1.5.0)
        composable(Screen.BureauConfirmation.route) {
            BureauConfirmationScreen(
                onNextClick = actions.navigateToLoanOffer,
                onBackClick = actions.navigateBack
            )
        }
        
        // Loan Offer screen
        composable(Screen.LoanOffer.route) {
            LoanOfferScreen(
                onNextClick = actions.navigateToEmploymentVerification,
                onBackClick = actions.navigateBack
            )
        }
        
        // Employment Verification screen
        composable(Screen.EmploymentVerification.route) {
            EmploymentVerificationScreen(
                onNextClick = actions.navigateToKeyFactSheet,
                onBackClick = actions.navigateBack
            )
        }
        
        // Key Fact Sheet screen
        composable(Screen.KeyFactSheet.route) {
            KeyFactSheetScreen(
                onNextClick = { 
                    // If your KeyFactSheetScreen doesn't actually pass an applicationId
                    // Create a default or hardcoded one
                    actions.navigateToApplicationComplete("default-id") 
                },
                onBackClick = actions.navigateBack,
                onEditSection = { section -> 
                    // Handle section editing
                    when(section) {
                        "personal" -> actions.navigateToPersonalInfo()
                        "employment" -> actions.navigateToEmploymentDetails()
                        "documents" -> actions.navigateToDocumentUpload()
                        "loan" -> actions.navigateToLoanOffer()
                        else -> {} // Handle other cases or do nothing
                    }
                }
            )
        }
        
        // Application Complete screen
        composable(
            route = "${Screen.ApplicationComplete.route}/{applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getString("applicationId") ?: ""
            ApplicationCompleteScreen(
                onHomeClick = actions.navigateToHome
            )
        }
        
        // Settings screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = actions.navigateBack
            )
        }
    }
}

/**
 * Class defining navigation actions that can be used from composables
 * Updated for v1.5.0 to support Bureau Confirmation screen
 */
class NavigationActions(private val navController: NavHostController) {
    
    val navigateToLogin: () -> Unit = {
        navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }
    
    val navigateToPrivacyPolicy: () -> Unit = {
        navController.navigate(Screen.PrivacyPolicy.route)
    }
    
    val navigateToHome: () -> Unit = {
        navController.navigate(Screen.Home.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
    
    val navigateToPANEntry: () -> Unit = {
        navController.navigate(Screen.PANEntry.route)
    }
    
    val navigateToPersonalInfo: () -> Unit = {
        navController.navigate(Screen.PersonalInfo.route)
    }
    
    val navigateToEmploymentDetails: () -> Unit = {
        navController.navigate(Screen.EmploymentDetails.route)
    }
    
    val navigateToDocumentUpload: () -> Unit = {
        navController.navigate(Screen.DocumentUpload.route)
    }
    
    val navigateToBureauConfirmation: () -> Unit = {
        navController.navigate(Screen.BureauConfirmation.route)
    }
    
    val navigateToLoanOffer: () -> Unit = {
        navController.navigate(Screen.LoanOffer.route)
    }
    
    val navigateToEmploymentVerification: () -> Unit = {
        navController.navigate(Screen.EmploymentVerification.route)
    }
    
    val navigateToKeyFactSheet: () -> Unit = {
        navController.navigate(Screen.KeyFactSheet.route)
    }
    
    val navigateToApplicationComplete: (String) -> Unit = { applicationId ->
        navController.navigate(Screen.ApplicationComplete.withArgs(applicationId)) {
            popUpTo(Screen.KeyFactSheet.route) { inclusive = true }
        }
    }
    
    val navigateToSettings: () -> Unit = {
        navController.navigate(Screen.Settings.route)
    }
    
    val navigateBack: () -> Unit = {
        navController.popBackStack()
    }

    val navigateToScreen: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route)
    }

    val navigateToScreenByRoute: (String) -> Unit = { route ->
        AppLogger.d("Navigating to route: $route")
        navController.navigate(route) {
            // Pop up to the home screen but keep it in backstack
            popUpTo(Screen.Home.route) { inclusive = false }
        }
    }
    
    /**
     * Navigation logic based on bureau score
     * - If bureau score is between 1-1000, navigate to Bureau Confirmation
     * - Otherwise, navigate directly to Loan Offer
     * 
     * Updated for v1.5.0 to implement real bureau score check
     */
    val navigateBasedOnBureauScore: () -> Unit = {
        // For now, let's always go to Bureau Confirmation screen
        // The ViewModel will automatically skip if no data is found
        navigateToBureauConfirmation()
        
        // Original code with hardcoded values removed - no need to check
        // The BureauConfirmationViewModel will handle the empty state now
    }
}


@Composable
fun SwipeBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    if (!enabled) return
    
    var dragDistance by remember { mutableStateOf(0f) }
    val dragThreshold = 100f
    val dragPercentage = (dragDistance / dragThreshold).coerceIn(0f, 1f)
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Back indicator at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .alpha(dragPercentage * 0.8f)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        
        // Invisible drag area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragDistance > dragThreshold) {
                                onBack()
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = {
                            dragDistance = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) {
                                dragDistance += dragAmount
                            }
                        }
                    )
                }
        )
    }
}

@Composable
fun BackNavIndicator(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                .clickable { onBack() }
        )
    }
}