package com.loansai.unassisted.ui.screens.personal

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loansai.unassisted.ui.components.*
import com.loansai.unassisted.domain.model.Gender
import android.widget.Toast
import java.time.LocalDate
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import com.loansai.unassisted.util.constants.IndianStates
import java.time.format.DateTimeFormatter

/**
 * Modern Personal Information Screen with continuous scrolling form
 */
@Composable
fun PersonalInfoScreen(
    onNavigateToEmploymentDetails: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PersonalInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Format date for display
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    
    // State for permanent address section visibility
    var showPermanentAddress by remember { mutableStateOf(false) }
    
    // States for permanent address
    var permanentAddressLine by remember { mutableStateOf("") }
    var permanentPinCode by remember { mutableStateOf("") }
    var permanentCity by remember { mutableStateOf("") }
    var permanentState by remember { mutableStateOf("") }
    
    // Date picker: Define a function to show the date picker dialog with Material 3 styling if available.
    fun showDatePicker() {
        val currentDate = LocalDate.now()
        val initialDate = uiState.dateOfBirth ?: currentDate.minusYears(25)
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                viewModel.updateDateOfBirth(selectedDate)
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        // Set maximum and minimum selectable dates
        val maxDate = currentDate.minusYears(18)
        datePickerDialog.datePicker.maxDate = maxDate.toEpochDay() * 24 * 60 * 60 * 1000
        val minDate = currentDate.minusYears(65)
        datePickerDialog.datePicker.minDate = minDate.toEpochDay() * 24 * 60 * 60 * 1000
        datePickerDialog.show()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content using ContinuousFormContainer
        ContinuousFormContainer(
            title = "Personal Information",
            subtitle = "Please share some details about yourself",
            progress = 2f / 7f, // Personal info is step 2 of 7
            isLoading = uiState.isLoading,
            modifier = Modifier.fillMaxSize()
        ) {
            // Modern back button
            Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) {
                ModernBackButton(
                    onBack = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Personal Details Section
            AnimatedFormSection(
                title = "Personal Details",
                subtitle = "Basic information for your application",
                visible = true,
                initiallyVisible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Full Name
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (uiState.fullNameError != null) 
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
                                "Full Name *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.fullName,
                                onValueChange = { viewModel.updateFullName(it) },
                                placeholder = { Text("Enter your full name") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null
                                    )
                                },
                                isError = uiState.fullNameError != null,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = if (uiState.fullNameError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = if (uiState.fullNameError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                maxLines = 1
                            )
                            if (uiState.fullNameError != null) {
                                Text(
                                    text = uiState.fullNameError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // Date of Birth Field with clickable icon to show date picker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (uiState.dateOfBirthError != null) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            // Make the entire card clickable
                            .clickable { showDatePicker() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        // Row is used to combine text field display and trailing icon for clarity
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.dateOfBirth?.format(dateFormatter) ?: "Select your birth date",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = if (uiState.dateOfBirthError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { showDatePicker() }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Select date of birth"
                                )
                            }
                        }
                    }
                    if (uiState.dateOfBirthError != null) {
                        Text(
                            text = uiState.dateOfBirthError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                    
                    // Gender with equal weights for options so they appear on one line
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (uiState.genderError != null) 
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
                                "Gender *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = uiState.gender == Gender.MALE,
                                        onClick = { viewModel.updateGender(Gender.MALE) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Male",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = uiState.gender == Gender.FEMALE,
                                        onClick = { viewModel.updateGender(Gender.FEMALE) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Female",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = uiState.gender == Gender.OTHER,
                                        onClick = { viewModel.updateGender(Gender.OTHER) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Other",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            if (uiState.genderError != null) {
                                Text(
                                    text = uiState.genderError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // Email
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (uiState.emailError != null) 
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
                                "Email Address *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = { viewModel.updateEmail(it) },
                                placeholder = { Text("Enter your email address") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null
                                    )
                                },
                                isError = uiState.emailError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = if (uiState.emailError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = if (uiState.emailError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                maxLines = 1
                            )
                            if (uiState.emailError != null) {
                                Text(
                                    text = uiState.emailError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // Alternative phone (optional)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = if (uiState.alternatePhoneError != null) 
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
                                "Alternative Phone Number",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.alternatePhone,
                                onValueChange = { viewModel.updateAlternatePhone(it) },
                                placeholder = { Text("Enter alternative phone") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null
                                    )
                                },
                                isError = uiState.alternatePhoneError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = if (uiState.alternatePhoneError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = if (uiState.alternatePhoneError != null) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                maxLines = 1
                            )
                            
                            if (uiState.alternatePhoneError != null) {
                                Text(
                                    text = uiState.alternatePhoneError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "This will be used as a backup contact number",
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
            
            // Current Address Section
            AnimatedFormSection(
                title = "Current Address",
                subtitle = "Where you currently reside",
                visible = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Address field
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
                                "Address *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.addressLine1,
                                onValueChange = { viewModel.updateAddressLine1(it) },
                                placeholder = { Text("House no., Building, Street, Area, Landmark") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
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
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                maxLines = 1
                            )
                            if (uiState.addressLine1Error != null) {
                                Text(
                                    text = uiState.addressLine1Error ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    // Row for PIN Code and City - ensuring both are one row in height using maxLines and weight modifiers
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
                                    color = if (uiState.pinCodeError != null) 
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
                                    value = uiState.pinCode,
                                    onValueChange = { 
                                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                            viewModel.updatePinCode(it) 
                                        }
                                    },
                                    placeholder = { Text("Pincode") },
                                    isError = uiState.pinCodeError != null,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedIndicatorColor = if (uiState.pinCodeError != null) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = if (uiState.pinCodeError != null) 
                                            MaterialTheme.colorScheme.error 
                                        else 
                                            MaterialTheme.colorScheme.outline
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    maxLines = 1
                                )
                                if (uiState.pinCodeError != null) {
                                    Text(
                                        text = uiState.pinCodeError ?: "",
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
                                    singleLine = true,
                                    maxLines = 1
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
                    
                    // State search field
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
                    
                    // Checkbox for permanent address
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = !showPermanentAddress,
                            onCheckedChange = { showPermanentAddress = !it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Permanent Address is same as Current Address",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            // Permanent Address Section (optional)
            AnimatedVisibility(
                visible = showPermanentAddress,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AnimatedFormSection(
                    title = "Permanent Address",
                    subtitle = "Your permanent residential address",
                    visible = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Permanent address fields will be similar to current address
                        // (Not fully implemented in this example)
                    }
                }
            }
            
            FormSectionDivider()
            
            // Success animation when saving is successful
            AnimatedVisibility(
                visible = uiState.saveSuccess,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Information saved! Proceeding to next step...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Extra space for AI Assistant bubble
            Spacer(modifier = Modifier.height(80.dp))
        }
        
        // AI Assistant bubble
        AIAssistantBubble(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
        
        // Floating Action Button for Continue
        FloatingActionButton(
            onClick = {
                focusManager.clearFocus() // Hide keyboard
                viewModel.validateAndSavePersonalInfo()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 90.dp), // Position above AI bubble
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, 
                contentDescription = "Continue"
            )
        }
        
        // Navigate when ready
        LaunchedEffect(uiState.navigateToEmploymentDetails) {
            if (uiState.navigateToEmploymentDetails) {
                onNavigateToEmploymentDetails()
                viewModel.resetNavigation()
            }
        }
    }
}