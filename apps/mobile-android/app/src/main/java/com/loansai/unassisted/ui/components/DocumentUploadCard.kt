package com.loansai.unassisted.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.ui.screens.documents.DocumentProcessingState
// import org.w3c.dom.DocumentType
import java.io.File
import com.loansai.unassisted.domain.model.DocumentType


/**
 * Document upload card component with preview and status
 */
@Composable
fun DocumentUploadCard(
    title: String,
    description: String,
    documentType: DocumentType,
    document: Document? = null,
    processingState: DocumentProcessingState? = null,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDeleteClick: (Document) -> Unit,
    onRetryClick: (Document) -> Unit,
    onViewClick: (Document) -> Unit,
    modifier: Modifier = Modifier
) {
    val isProcessing = processingState is DocumentProcessingState.Processing || 
                      processingState is DocumentProcessingState.Uploading
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = getDocumentTypeIcon(documentType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Status icon based on document status
                document?.let {
                    DocumentStatusIcon(status = it.documentStatus)
                }
                
                // Show loading indicator during processing
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Display document preview or upload options
            if (document != null) {
                DocumentPreview(
                    document = document,
                    onDeleteClick = { onDeleteClick(document) },
                    onRetryClick = { onRetryClick(document) },
                    onViewClick = { onViewClick(document) }
                )
            } else {
                // Show upload options or progress indicator
                AnimatedVisibility(
                    visible = !isProcessing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    UploadOptions(
                        onCameraClick = onCameraClick,
                        onGalleryClick = onGalleryClick
                    )
                }
                
                // Show processing state
                AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = when(processingState) {
                                    is DocumentProcessingState.Processing -> "Processing document..."
                                    is DocumentProcessingState.Uploading -> "Uploading document..."
                                    else -> "Processing..."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Show error message
                AnimatedVisibility(
                    visible = processingState is DocumentProcessingState.Error,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (processingState is DocumentProcessingState.Error) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = processingState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Document status icon based on status
 */
@Composable
fun DocumentStatusIcon(status: DocumentStatus) {
    val icon = when (status) {
        DocumentStatus.UPLOADED -> Icons.Outlined.CloudUpload
        DocumentStatus.PROCESSING -> null // Show progress indicator instead
        DocumentStatus.PROCESSED, 
        DocumentStatus.VERIFIED -> Icons.Default.FilePresent
        DocumentStatus.REJECTED, 
        DocumentStatus.ERROR -> Icons.Default.Error
        else -> Icons.Outlined.CloudUpload
    }
    
    val tint = when (status) {
        DocumentStatus.PROCESSED, 
        DocumentStatus.VERIFIED -> MaterialTheme.colorScheme.primary
        DocumentStatus.REJECTED, 
        DocumentStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    if (status == DocumentStatus.PROCESSING) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = status.name,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Document preview section
 */
@Composable
fun DocumentPreview(
    document: Document,
    onDeleteClick: () -> Unit,
    onRetryClick: () -> Unit,
    onViewClick: () -> Unit
) {
    Column {
        // Preview image or file icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onViewClick() },
            contentAlignment = Alignment.Center
        ) {
            when (document.fileType) {
                FileType.JPG, FileType.PNG -> {
                    // Image preview
                    document.localUri?.let { uri ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Document preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    } ?: FileTypeIcon(document.fileType)
                }
                else -> {
                    // File type icon for non-images
                    FileTypeIcon(document.fileType)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // File name and size
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = formatFileSize(document.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show processing status
                if (document.documentStatus == DocumentStatus.PROCESSING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
            
            // Action buttons
            Row {
                IconButton(onClick = { onViewClick() }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "View",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = { onDeleteClick() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                // Show retry button if error
                if (document.documentStatus == DocumentStatus.ERROR || 
                    document.documentStatus == DocumentStatus.REJECTED) {
                    IconButton(onClick = { onRetryClick() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Show error message
        if (document.documentStatus == DocumentStatus.ERROR || 
            document.documentStatus == DocumentStatus.REJECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Failed to process document. Please try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Upload options section with camera and gallery buttons
 */
@Composable
fun UploadOptions(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onCameraClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Take Photo",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Camera")
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        OutlinedButton(
            onClick = onGalleryClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Choose File",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse")
        }
    }
}

/**
 * File type icon based on file type
 */
@Composable
fun FileTypeIcon(fileType: FileType) {
    val icon = when (fileType) {
        FileType.PDF -> Icons.Outlined.PictureAsPdf
        FileType.JPG -> Icons.Outlined.Image
        FileType.PNG -> Icons.Outlined.Image
        FileType.TIFF -> Icons.Outlined.Image
        FileType.OTHER -> Icons.Outlined.Description
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = fileType.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Get icon for document type
 */
private fun getDocumentTypeIcon(documentType: DocumentType): ImageVector {
    return when (documentType) {
        DocumentType.PAN_CARD -> Icons.Default.PhotoLibrary
        DocumentType.ID_CARD -> Icons.Default.PhotoLibrary
        DocumentType.BANK_STATEMENT -> Icons.Outlined.Description
        DocumentType.SALARY_SLIP -> Icons.Outlined.Description
        DocumentType.INCOME_TAX_RETURN -> Icons.Outlined.Description
        DocumentType.FORM_26AS -> Icons.Outlined.PictureAsPdf
        DocumentType.ADDRESS_PROOF -> Icons.Default.PhotoLibrary
        DocumentType.OTHER -> Icons.Outlined.Description
    }
}

/**
 * Format file size to human readable string
 */
private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes < 1024) return "$sizeInBytes B"
    val exp = (Math.log(sizeInBytes.toDouble()) / Math.log(1024.0)).toInt()
    val prefix = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", sizeInBytes / Math.pow(1024.0, exp.toDouble()), prefix)
}