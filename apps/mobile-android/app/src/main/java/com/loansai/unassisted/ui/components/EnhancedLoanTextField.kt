package com.loansai.unassisted.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Enhanced text field with validation and micro-interactions
 */
@Composable
fun EnhancedLoanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    supportText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    required: Boolean = false,
    validationFunction: ((String) -> Boolean)? = null,
    supportVoiceInput: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var isValid by remember { mutableStateOf(false) }
    var showValidCheckmark by remember { mutableStateOf(false) }
    
    // Validate input when value changes or field loses focus
    LaunchedEffect(value, isFocused) {
        if (value.isNotEmpty() && validationFunction != null) {
            isValid = validationFunction(value)
            
            // Show checkmark briefly after successful validation when user finishes typing
            if (isValid && !isFocused && value.isNotEmpty()) {
                showValidCheckmark = true
                delay(2000) // Hide after 2 seconds
                showValidCheckmark = false
            }
        } else {
            isValid = false
            showValidCheckmark = false
        }
    }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { 
                Text(
                    text = if (required) "$label *" else label
                ) 
            },
            placeholder = placeholder?.let { { Text(text = it) } },
            leadingIcon = leadingIcon,
            trailingIcon = {
                when {
                    isError && errorMessage != null -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    showValidCheckmark -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Valid",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    trailingIcon != null -> {
                        trailingIcon()
                    }
                }
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            readOnly = readOnly,
            enabled = enabled,
            maxLines = maxLines,
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = if (isValid && !isError) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.outline,
                unfocusedIndicatorColor = if (isValid && !isError && value.isNotEmpty()) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                else 
                    MaterialTheme.colorScheme.outline
            )
        )
        
        // Error or support text
        AnimatedVisibility(
            visible = (isError && errorMessage != null) || (supportText != null && !isError),
            enter = fadeIn(animationSpec = tween(200)) + 
                   expandVertically(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + 
                   shrinkVertically(animationSpec = tween(200))
        ) {
            if (isError && errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (supportText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = supportText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}