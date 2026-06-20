package com.loansai.unassisted.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A handler that detects swipe gestures for back navigation
 */
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

/**
 * A subtle back navigation indicator at the top of the screen
 */
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

/**
 * Modern back navigation button with chevron icon
 */
@Composable
fun ModernBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}