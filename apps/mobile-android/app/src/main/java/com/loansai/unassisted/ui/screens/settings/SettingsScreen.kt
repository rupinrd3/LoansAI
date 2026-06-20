package com.loansai.unassisted.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loansai.unassisted.R
import com.loansai.unassisted.ui.components.AIAssistantBubble
import com.loansai.unassisted.ui.components.AnimatedSections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.tooling.preview.Preview
import com.loansai.unassisted.service.ImageResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        // Back icon
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedSections {
                    // OCR Service Selection
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.ocr_service_selection),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Column {
                                // ML Kit option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.selectedOCRService == OCRService.ML_KIT,
                                        onClick = { viewModel.selectOCRService(OCRService.ML_KIT) }
                                    )
                                    
                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                        Text(
                                            text = stringResource(id = R.string.ml_kit_ocr),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = stringResource(id = R.string.ml_kit_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                // Document AI option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = state.selectedOCRService == OCRService.DOCUMENT_AI,
                                        onClick = { viewModel.selectOCRService(OCRService.DOCUMENT_AI) }
                                    )
                                    
                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                        Text(
                                            text = stringResource(id = R.string.document_ai_ocr),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = stringResource(id = R.string.document_ai_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Document Upload Settings (NEW SECTION)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Document Upload Settings",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Enable Document Upload",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                Switch(
                                    checked = state.documentUploadEnabled,
                                    onCheckedChange = { viewModel.toggleDocumentUpload(it) }
                                )
                            }
                            
                            AnimatedVisibility(
                                visible = state.documentUploadEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Text(
                                        text = "Image Resolution",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    
                                    Column {
                                        // Original Resolution option
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = state.documentResolution == ImageResolution.ORIGINAL,
                                                onClick = { viewModel.setDocumentResolution(ImageResolution.ORIGINAL) }
                                            )
                                            
                                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                                Text(
                                                    text = "Original Size",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = "Upload images at their original resolution",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        // Medium Resolution (2MP) option
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = state.documentResolution == ImageResolution.MEDIUM_2MP,
                                                onClick = { viewModel.setDocumentResolution(ImageResolution.MEDIUM_2MP) }
                                            )
                                            
                                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                                Text(
                                                    text = "2 Megapixels",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = "Reduce to ~2MP (1920x1080) to save space",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        // Low Resolution (0.5MP) option
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = state.documentResolution == ImageResolution.LOW_05MP,
                                                onClick = { viewModel.setDocumentResolution(ImageResolution.LOW_05MP) }
                                            )
                                            
                                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                                Text(
                                                    text = "0.5 Megapixels",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = "Reduce to ~0.5MP (800x600) to save data",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // AI Assistant Settings
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.ai_assistant_settings),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.enable_ai_assistant),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                Switch(
                                    checked = state.aiAssistantEnabled,
                                    onCheckedChange = { viewModel.toggleAIAssistant(it) }
                                )
                            }
                        }
                    }
                    
                    // App Information
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.app_information),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.version),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = state.appVersion,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.build),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = state.buildNumber,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    
                    // Legal Information
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.legal_information),
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            TextButton(
                                onClick = { viewModel.openPrivacyPolicy() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(id = R.string.privacy_policy))
                            }
                            
                            TextButton(
                                onClick = { viewModel.openTermsAndConditions() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(id = R.string.terms_and_conditions))
                            }
                        }
                    }
                    
                    // Clear data (for development/testing)
                    if (state.isDebugBuild) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.development_options),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                Button(
                                    onClick = { viewModel.confirmClearData() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.clear_all_data))
                                }
                            }
                        }
                    }
                }
            }
            
            // AI Assistant bubble (if enabled)
            if (state.aiAssistantEnabled) {
                AIAssistantBubble(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    onClick = { viewModel.showAIAssistant() }
                )
            }
            
            // Clear data confirmation dialog
            if (state.showClearDataDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissClearDataDialog() },
                    title = { Text(text = stringResource(id = R.string.clear_all_data)) },
                    text = { Text(text = stringResource(id = R.string.clear_data_confirmation)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearAllData()
                                viewModel.dismissClearDataDialog()
                            }
                        ) {
                            Text(text = stringResource(id = R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissClearDataDialog() }) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

enum class OCRService {
    ML_KIT,
    DOCUMENT_AI
}