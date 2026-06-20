package com.loansai.unassisted.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Service for processing documents and images
 */
@Singleton
class DocumentProcessingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Process an image - compress and resize if needed
     * 
     * @param uri The URI of the image
     * @param targetResolution The target resolution to resize to
     * @param maxFileSizeMB Maximum allowed file size in MB
     * @return The URI of the processed image
     */
    suspend fun processImage(
        uri: Uri, 
        targetResolution: ImageResolution = ImageResolution.ORIGINAL,
        maxFileSizeMB: Float = 4.0f
    ): Uri = withContext(Dispatchers.IO) {
        try {
            // Get input stream from URI
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for image")
            
            // Load bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Check file size
            val fileSize = getFileSize(uri)
            val maxFileSizeBytes = maxFileSizeMB * 1024 * 1024
            
            // If file is already smaller than max size and we don't need to resize, return original
            if (fileSize < maxFileSizeBytes && targetResolution == ImageResolution.ORIGINAL) {
                return@withContext uri
            }
            
            // Calculate sample size for downsampling if needed
            val sampleSize = when (targetResolution) {
                ImageResolution.ORIGINAL -> {
                    if (fileSize < maxFileSizeBytes) 1 else {
                        // Calculate appropriate sample size based on file size
                        max(1, sqrt(fileSize.toFloat() / maxFileSizeBytes).toInt())
                    }
                }
                ImageResolution.MEDIUM_2MP -> {
                    // Target 2MP (approx 1920x1080)
                    val targetPixels = 2_000_000
                    val imagePixels = options.outWidth * options.outHeight
                    max(1, sqrt(imagePixels.toFloat() / targetPixels).toInt())
                }
                ImageResolution.LOW_05MP -> {
                    // Target 0.5MP (approx 800x600)
                    val targetPixels = 500_000
                    val imagePixels = options.outWidth * options.outHeight
                    max(1, sqrt(imagePixels.toFloat() / targetPixels).toInt())
                }
            }
            
            // Decode bitmap with sample size
            val inputStream2 = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for image")
            
            val decodingOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodingOptions)
                ?: throw IllegalStateException("Failed to decode bitmap")
            
            inputStream2.close()
            
            // Create output file
            val outputFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.jpg")
            
            // Compress and save
            val outputStream = FileOutputStream(outputFile)
            
            // Start with higher quality and progressively reduce if needed
            var quality = 95
            var compressedBitmap = bitmap
            
            // Compress with reducing quality until file size is acceptable
            while (true) {
                val byteArrayOutputStream = ByteArrayOutputStream()
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
                
                val compressedSize = byteArrayOutputStream.size()
                
                if (compressedSize <= maxFileSizeBytes || quality <= 30) {
                    // Either we're under the max size or we've reached minimum quality
                    byteArrayOutputStream.writeTo(outputStream)
                    break
                }
                
                // Reduce quality and try again
                quality = max(quality - 10, 30)
            }
            
            outputStream.flush()
            outputStream.close()
            
            // Return URI for the output file
            Uri.fromFile(outputFile)
            
        } catch (e: Exception) {
            AppLogger.e("Error processing image: ${e.message}", e)
            // If processing fails, return original
            uri
        }
    }
    
    /**
     * Validate a document file
     * 
     * @param uri The URI of the document
     * @param fileType The expected file type
     * @return True if valid, false otherwise
     */
    suspend fun validateDocument(uri: Uri, fileType: FileType): Boolean = withContext(Dispatchers.IO) {
        try {
            when (fileType) {
                FileType.PDF -> {
                    // Check PDF header
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val buffer = ByteArray(5)
                    val bytesRead = inputStream?.read(buffer, 0, 5) ?: 0
                    inputStream?.close()
                    
                    if (bytesRead == 5) {
                        val header = String(buffer)
                        header.startsWith("%PDF-")
                    } else {
                        false
                    }
                }
                FileType.JPG, FileType.PNG -> {
                    // For images, try to decode as bitmap
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()
                    
                    options.outWidth > 0 && options.outHeight > 0
                }
                else -> true // Accept other file types without validation
            }
        } catch (e: Exception) {
            AppLogger.e("Error validating document: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get file size from URI
     */
    private fun getFileSize(uri: Uri): Long {
        return try {
            val fileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            val fileSize = fileDescriptor?.length ?: 0L
            fileDescriptor?.close()
            fileSize
        } catch (e: Exception) {
            AppLogger.e("Error getting file size: ${e.message}", e)
            0L
        }
    }
}

/**
 * Target image resolutions
 */
enum class ImageResolution {
    ORIGINAL,      // Keep original resolution, may compress for size
    MEDIUM_2MP,    // Approximately 1920x1080 or equivalent (2 megapixels)
    LOW_05MP       // Approximately 800x600 or equivalent (0.5 megapixels)
}