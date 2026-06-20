package com.loansai.unassisted.ui.components

import android.Manifest
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.loansai.unassisted.service.voice.VoiceRecognitionCallback
import com.loansai.unassisted.service.voice.impl.GoogleVoiceRecognitionService
import com.loansai.unassisted.util.logger.AppLogger
import androidx.compose.runtime.DisposableEffect

/**
 * Voice input button component with speech recognition functionality
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceInputButton(
    onVoiceResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    
    // Voice recognition service
    val voiceRecognitionService = remember {
        GoogleVoiceRecognitionService(context)
    }
    
    // Microphone permission state
    val micPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )
    
    // Animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "voiceAnimation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500)
        ),
        label = "scale"
    )
    
    // Voice recognition callback
    val voiceCallback = remember {
        object : VoiceRecognitionCallback {
            override fun onListeningStarted() {
                isListening = true
            }
            
            override fun onResults(results: List<String>) {
                isListening = false
                if (results.isNotEmpty()) {
                    onVoiceResult(results[0])
                }
            }
            
            override fun onError(error: String) {
                isListening = false
                AppLogger.e("Voice recognition error: $error")
            }
        }
    }
    
    // Stop listening when leaving the composition
    LaunchedEffect(Unit) {
        // No need to do anything during initialization
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Ripple effect when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
        
        IconButton(
            onClick = {
                if (onClick != null) {
                    onClick()
                } else {
                    when {
                        // If we have permission, toggle listening
                        micPermissionState.status.isGranted -> {
                            if (isListening) {
                                voiceRecognitionService.stopListening()
                                isListening = false
                            } else {
                                voiceRecognitionService.startListening(
                                    callback = voiceCallback
                                )
                            }
                        }
                        // Otherwise request permission
                        else -> {
                            micPermissionState.launchPermissionRequest()
                        }
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicNone,
                contentDescription = "Voice Input",
                tint = if (isListening) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            DisposableEffect(Unit) {
                onDispose {
                    voiceRecognitionService.destroy()
                }
            }            
        }
    }
}