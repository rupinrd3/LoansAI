package com.loansai.unassisted.ui.components

import android.Manifest
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.loansai.unassisted.domain.model.OCRScanState

/**
 * OCR scanner button for scanning documents
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OCRScannerButton(
    scanState: OCRScanState,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    resultPreview: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Camera permission state
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )
    
    var showScanSuccess by remember { mutableStateOf(false) }
    
    // Show success check mark briefly when scan succeeds
    LaunchedEffect(scanState) {
        if (scanState is OCRScanState.Success) {
            showScanSuccess = true
            kotlinx.coroutines.delay(1500)
            showScanSuccess = false
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display scan button or loading indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Scan button
            if (scanState !is OCRScanState.Scanning && !showScanSuccess) {
                Button(
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            onScanClick()
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text("Scan Document")
                }
            }
            
            // Loading indicator
            if (scanState is OCRScanState.Scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            
            // Success check mark
            if (showScanSuccess) {
                val scale by animateFloatAsState(
                    targetValue = if (showScanSuccess) 1.2f else 0f,
                    label = "successScale"
                )
                
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .scale(scale)
                        .size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Display error message if scan fails
        if (scanState is OCRScanState.Error) {
            Text(
                text = scanState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        // Display preview of scan result
        if (scanState is OCRScanState.Success && resultPreview != null) {
            resultPreview()
        }
    }
}