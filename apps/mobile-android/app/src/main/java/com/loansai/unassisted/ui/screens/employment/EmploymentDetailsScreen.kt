package com.loansai.unassisted.ui.screens.employment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loansai.unassisted.domain.model.Employer
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.ui.components.*
import java.text.NumberFormat
import java.util.*

/**
 * Employment Details Screen with continuous scrolling form
 */
@Composable
fun EmploymentDetailsScreen(
    onNavigateToDocumentUpload: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EmploymentDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content using ContinuousFormContainer
        ContinuousFormContainer(
            title = "Employment Details",
            subtitle = "Tell us about your employment status",
            progress = 3f / 7f, // Employment details is step 3 of 7
            isLoading = uiState.isLoading,
            modifier = Modifier.fillMaxSize()
        ) {
            // Back navigation indicator
            BackNavIndicator(onBack = onNavigateBack)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Employment Type Section
            AnimatedFormSection(
                title = "Employment Type",
                subtitle = "Select your current employment status",
                visible = true,
                initiallyVisible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Employment Type options
                    EmploymentTypeSelector(
                        selectedType = uiState.employmentType,
                        onTypeSelected = { viewModel.updateEmploymentType(it) }
                    )
                }
            }
            
            FormSectionDivider()
            
            // Employer Details Section - conditionally visible based on employment type
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.employmentType != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                when (uiState.employmentType) {
                    EmploymentType.PRIVATE_SECTOR -> {
                        // Private Sector Fields
                        AnimatedFormSection(
                            title = "Employer Details",
                            subtitle = "Information about your employer",
                            visible = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Employer Search
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Employer Name *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        EmployerSearchField(
                                            searchQuery = uiState.employerSearchQuery,
                                            onSearchQueryChange = { viewModel.searchEmployers(it) },
                                            selectedEmployer = uiState.selectedEmployer,
                                            searchResults = uiState.employerSearchResults,
                                            onEmployerSelected = { viewModel.selectEmployer(it) },
                                            isSearching = uiState.isSearching
                                        )
                                    }
                                }
                                
                                // Work Email for Private Sector (Required)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.workEmailError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Work Email *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        OutlinedTextField(
                                            value = uiState.workEmail,
                                            onValueChange = { viewModel.updateWorkEmail(it) },
                                            placeholder = { Text("Enter your official email address") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Email,
                                                    contentDescription = null
                                                )
                                            },
                                            isError = uiState.workEmailError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Email,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.workEmailError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.workEmailError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        
                                        if (uiState.workEmailError != null) {
                                            Text(
                                                text = uiState.workEmailError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    EmploymentType.GOVERNMENT -> {
                        // Government Entity Fields
                        AnimatedFormSection(
                            title = "Government Employment",
                            subtitle = "Details about your government position",
                            visible = true
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Government Entity Name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.governmentEntityNameError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Government Entity Name *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        OutlinedTextField(
                                            value = uiState.governmentEntityName,
                                            onValueChange = { viewModel.updateGovernmentEntityName(it) },
                                            placeholder = { Text("Enter government organization name") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Business,
                                                    contentDescription = null
                                                )
                                            },
                                            isError = uiState.governmentEntityNameError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.governmentEntityNameError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.governmentEntityNameError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        
                                        if (uiState.governmentEntityNameError != null) {
                                            Text(
                                                text = uiState.governmentEntityNameError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Work Email - Optional for Government
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.workEmailError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Work Email (Optional)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        OutlinedTextField(
                                            value = uiState.workEmail,
                                            onValueChange = { viewModel.updateWorkEmail(it) },
                                            placeholder = { Text("Enter your official email address") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Email,
                                                    contentDescription = null
                                                )
                                            },
                                            isError = uiState.workEmailError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Email,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.workEmailError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.workEmailError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        
                                        if (uiState.workEmailError != null) {
                                            Text(
                                                text = uiState.workEmailError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Designation - Required for Government
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.designationError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Designation *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        OutlinedTextField(
                                            value = uiState.designation,
                                            onValueChange = { viewModel.updateDesignation(it) },
                                            placeholder = { Text("Enter your job title") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Work,
                                                    contentDescription = null
                                                )
                                            },
                                            isError = uiState.designationError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.designationError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.designationError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        
                                        if (uiState.designationError != null) {
                                            Text(
                                                text = uiState.designationError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Department - Optional
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Department (Optional)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        OutlinedTextField(
                                            value = uiState.department,
                                            onValueChange = { viewModel.updateDepartment(it) },
                                            placeholder = { Text("Enter your department name") },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                    }
                                }
                                
                                // NOTE: Employee ID field is completely removed as per requirements
                            }
                        }
                    }
                    else -> {
                        // Handle other employment types here if needed
                    }
                }
            }
            
            // Only show the following sections if an employment type is selected
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.employmentType != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column {
                    FormSectionDivider()
                    
                    // Office Address Section
                    AnimatedFormSection(
                        title = "Office Address",
                        subtitle = "Where your workplace is located",
                        visible = true
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Combined Address Field (Line 1 & 2 merged)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (uiState.addressLine1Error != null) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Office Address *",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedTextField(
                                        value = uiState.addressLine1,
                                        onValueChange = { viewModel.updateAddressLine1(it) },
                                        placeholder = { Text("Building name, Street, Area, Landmark") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null
                                            )
                                        },
                                        isError = uiState.addressLine1Error != null,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedIndicatorColor = if (uiState.addressLine1Error != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.primary,
                                            unfocusedIndicatorColor = if (uiState.addressLine1Error != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outline
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    
                                    if (uiState.addressLine1Error != null) {
                                        Text(
                                            text = uiState.addressLine1Error ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Include all address details including building name, street, area, and landmark",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Row for PIN Code and City
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // PIN Code
                                Card(
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.postalCodeError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "PIN Code *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = uiState.postalCode,
                                            onValueChange = { 
                                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                                    viewModel.updatePostalCode(it) 
                                                }
                                            },
                                            placeholder = { Text("Pincode") },
                                            isError = uiState.postalCodeError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.postalCodeError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.postalCodeError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        if (uiState.postalCodeError != null) {
                                            Text(
                                                text = uiState.postalCodeError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // City
                                Card(
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (uiState.cityError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "City *",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = uiState.city,
                                            onValueChange = { viewModel.updateCity(it) },
                                            placeholder = { Text("Enter city name") },
                                            isError = uiState.cityError != null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedIndicatorColor = if (uiState.cityError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                unfocusedIndicatorColor = if (uiState.cityError != null) 
                                                    MaterialTheme.colorScheme.error 
                                                else 
                                                    MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        if (uiState.cityError != null) {
                                            Text(
                                                text = uiState.cityError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // State - Modern searchable dropdown implementation
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "State" + if (uiState.stateError != null) " *" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                                StateSearchField(
                                    value = uiState.state,
                                    onValueChange = { viewModel.updateState(it) },
                                    isError = uiState.stateError != null,
                                    errorMessage = uiState.stateError,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    FormSectionDivider()
                    
                    // Income & Obligations Section
                    AnimatedFormSection(
                        title = "Income & Loan Obligations",
                        subtitle = "Your financial information",
                        visible = true
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Monthly Salary
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (uiState.monthlySalaryError != null) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Monthly Salary (₹) *",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedTextField(
                                        value = uiState.monthlySalary,
                                        onValueChange = { newValue ->
                                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                                viewModel.updateMonthlySalary(newValue)
                                            }
                                        },
                                        placeholder = { Text("Enter your monthly salary") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Money,
                                                contentDescription = null
                                            )
                                        },
                                        isError = uiState.monthlySalaryError != null,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedIndicatorColor = if (uiState.monthlySalaryError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.primary,
                                            unfocusedIndicatorColor = if (uiState.monthlySalaryError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outline
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    
                                    if (uiState.monthlySalaryError != null) {
                                        Text(
                                            text = uiState.monthlySalaryError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else if (uiState.monthlySalary.isNotEmpty()) {
                                        // Format the amount with rupee symbol
                                        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                                        formatter.currency = Currency.getInstance("INR")
                                        Text(
                                            text = "Amount: ${formatter.format(uiState.monthlySalary.toLongOrNull() ?: 0)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Monthly EMI
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (uiState.monthlyEmiError != null) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Current Monthly EMI (₹) *",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedTextField(
                                        value = uiState.monthlyEmi,
                                        onValueChange = { newValue ->
                                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                                viewModel.updateMonthlyEmi(newValue)
                                            }
                                        },
                                        placeholder = { Text("Enter your current monthly EMI") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Money,
                                                contentDescription = null
                                            )
                                        },
                                        isError = uiState.monthlyEmiError != null,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedIndicatorColor = if (uiState.monthlyEmiError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.primary,
                                            unfocusedIndicatorColor = if (uiState.monthlyEmiError != null) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.outline
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    
                                    if (uiState.monthlyEmiError != null) {
                                        Text(
                                            text = uiState.monthlyEmiError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else if (uiState.monthlyEmi.isNotEmpty()) {
                                        // Format the amount with rupee symbol
                                        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
                                        formatter.currency = Currency.getInstance("INR")
                                        Text(
                                            text = "Amount: ${formatter.format(uiState.monthlyEmi.toLongOrNull() ?: 0)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Enter 0 if you don't have any current loans",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    FormSectionDivider()
                    
                    // Action Button
                    Button(
                        onClick = {
                            focusManager.clearFocus() // Hide keyboard
                            viewModel.validateAndSaveEmploymentDetails()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        enabled = uiState.employmentType != null
                    ) {
                        Text("Continue to Document Upload")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
            
            // Extra space at the bottom for the AI Assistant bubble
            Spacer(modifier = Modifier.height(100.dp))
        }
        
        // AI Assistant bubble - fixed position with proper spacing
        AIAssistantBubble(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 30.dp) // Add extra padding to avoid overlap with buttons
        )
        
        // Show navigation to next screen
        LaunchedEffect(uiState.navigateToDocumentUpload) {
            if (uiState.navigateToDocumentUpload) {
                onNavigateToDocumentUpload()
                viewModel.resetNavigation()
            }
        }
    }
}

/**
 * Employment Type Selector with radio buttons
 */
@Composable
private fun EmploymentTypeSelector(
    selectedType: EmploymentType?,
    onTypeSelected: (EmploymentType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Select your employment type *",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Using a vertical layout for better spacing
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Private Sector Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTypeSelected(EmploymentType.PRIVATE_SECTOR) }
                    .border(
                        width = 1.dp,
                        color = if (selectedType == EmploymentType.PRIVATE_SECTOR)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == EmploymentType.PRIVATE_SECTOR)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == EmploymentType.PRIVATE_SECTOR,
                        onClick = { onTypeSelected(EmploymentType.PRIVATE_SECTOR) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = "Private Sector",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Text(
                            text = "Employed by a private company",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Government Entity Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTypeSelected(EmploymentType.GOVERNMENT) }
                    .border(
                        width = 1.dp,
                        color = if (selectedType == EmploymentType.GOVERNMENT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == EmploymentType.GOVERNMENT)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == EmploymentType.GOVERNMENT,
                        onClick = { onTypeSelected(EmploymentType.GOVERNMENT) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = "Government",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Text(
                            text = "Employed by government or public sector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Other Options (Self-employed, Business Owner, etc.) can be added similarly
        }
    }
}

/**
 * Employer search field with results dropdown
 */
@Composable
fun EmployerSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedEmployer: Employer?,
    searchResults: List<Employer>,
    onEmployerSelected: (Employer) -> Unit,
    isSearching: Boolean
) {
    var showSearchResults by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search field
        Box {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    onSearchQueryChange(it)
                    showSearchResults = it.length >= 3
                },
                placeholder = { Text("Start typing to search (min 3 characters)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (searchQuery.length >= 3) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            // Search results dropdown
            androidx.compose.animation.AnimatedVisibility(
                visible = showSearchResults && searchResults.isNotEmpty() && searchQuery.length >= 3,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp)
                        .heightIn(max = 200.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    LazyColumn {
                        items(searchResults) { employer ->
                            EmployerSearchItem(
                                employer = employer,
                                onSelect = {
                                    onEmployerSelected(employer)
                                    showSearchResults = false
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Show selected employer card if one is selected
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedEmployer != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            selectedEmployer?.let { employer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Selected Employer:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = employer.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        employer.industry?.let { industry ->
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Industry: $industry",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual employer item in search results
 */
@Composable
fun EmployerSearchItem(
    employer: Employer,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = employer.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            employer.industry?.let { industry ->
                Text(
                    text = industry,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}