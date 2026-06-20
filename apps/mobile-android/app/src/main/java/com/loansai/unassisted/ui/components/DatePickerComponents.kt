package com.loansai.unassisted.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Date picker field with a selectable date
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    minDate: LocalDate = LocalDate.of(1900, 1, 1),
    maxDate: LocalDate = LocalDate.now(),
    required: Boolean = false
) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    var showDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.format(formatter) ?: "",
            onValueChange = { },
            label = { 
                Text(
                    text = if (required) "$label *" else label
                ) 
            },
            readOnly = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (errorMessage != null) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            isError = errorMessage != null
        )
        
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
    
    // This is a simple dialog - in a real app, you'd use DatePicker from Material3
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Date") },
            text = { 
                Text("Please note: This is a simplified date picker for demo purposes.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val today = LocalDate.now()
                    val selectedDate = if (today.isAfter(maxDate)) maxDate else today
                    onDateSelected(selectedDate)
                    showDialog = false
                }) {
                    Text("Select Today")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Year picker field with a selectable year
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearPickerField(
    label: String,
    year: Int?,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    minYear: Int = 1900,
    maxYear: Int = LocalDate.now().year,
    required: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = year?.toString() ?: "",
            onValueChange = { },
            label = { 
                Text(
                    text = if (required) "$label *" else label
                ) 
            },
            readOnly = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (errorMessage != null) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            isError = errorMessage != null
        )
        
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
    
    // Simple dialog for year selection
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Year") },
            text = { 
                Text("Please note: This is a simplified year picker for demo purposes.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentYear = LocalDate.now().year
                    val selectedYear = if (currentYear > maxYear) maxYear else currentYear
                    onYearSelected(selectedYear)
                    showDialog = false
                }) {
                    Text("Select Current Year")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}