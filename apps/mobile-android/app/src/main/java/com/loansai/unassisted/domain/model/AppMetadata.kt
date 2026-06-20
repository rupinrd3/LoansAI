package com.loansai.unassisted.domain.model

import java.time.LocalDateTime

/**
 * Data class for application metadata tracking
 */

// Note: These classes are now defined in MetadataModels.kt
// This file is kept for backward compatibility but should be
// considered deprecated

/**
 * File metadata for document uploads
 */
data class FileMetadata(
    val fileName: String,
    val fileSize: Long
)