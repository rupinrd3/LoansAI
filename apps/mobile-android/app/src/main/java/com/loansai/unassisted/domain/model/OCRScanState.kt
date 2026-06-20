package com.loansai.unassisted.domain.model

import android.net.Uri

/**
 * State class for OCR scanning
 */
sealed class OCRScanState {
    object Initial : OCRScanState()
    object Scanning : OCRScanState()
    data class Success(val text: String, val uri: Uri? = null) : OCRScanState()
    data class Error(val message: String) : OCRScanState()
}

