package com.loansai.unassisted.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.util.logger.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for file-related operations
 */
object FileUtils {
    
    private const val TEMP_FILE_PREFIX = "loansai_"
    private const val IMAGE_QUALITY = 90
    
    /**
     * Create a temporary file in the app's cache directory
     *
     * @param context The application context
     * @param extension The file extension (e.g., "jpg", "pdf")
     * @return The created File object
     */
    fun createTempFile(context: Context, extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${TEMP_FILE_PREFIX}${timeStamp}"
        return File.createTempFile(fileName, ".$extension", context.cacheDir)
    }
    
    /**
     * Get URI from file using FileProvider
     *
     * @param context The application context
     * @param file The file to get URI for
     * @return The content URI
     */
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Get MIME type from URI
     *
     * @param context The application context
     * @param uri The URI to get MIME type for
     * @return The MIME type or null if unknown
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))
        }
    }
    
    /**
     * Get file extension from URI
     *
     * @param context The application context
     * @param uri The URI to get extension for
     * @return The file extension
     */
    fun getFileExtension(context: Context, uri: Uri): String {
        val mimeType = getMimeType(context, uri) ?: return ""
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: ""
    }
    
    /**
     * Determine FileType from URI
     *
     * @param context The application context
     * @param uri The URI to analyze
     * @return The determined FileType
     */
    fun getFileTypeFromUri(context: Context, uri: Uri): FileType {
        val mimeType = getMimeType(context, uri) ?: ""
        
        return when {
            mimeType.contains("pdf") -> FileType.PDF
            mimeType.contains("jpg") || mimeType.contains("jpeg") -> FileType.JPG
            mimeType.contains("png") -> FileType.PNG
            else -> FileType.OTHER
        }
    }
    
    /**
     * Get file name from URI
     *
     * @param context The application context
     * @param uri The URI to get name for
     * @return The file name
     */
    fun getFileName(context: Context, uri: Uri): String {
        // Try to get the display name from content provider
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
        
        // Fallback to extracting from path
        val path = uri.path
        if (path != null) {
            val cut = path.lastIndexOf('/')
            if (cut != -1) {
                return path.substring(cut + 1)
            }
        }
        
        // Fallback to a timestamped name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val extension = getFileExtension(context, uri)
        return "doc_${timeStamp}.${extension}"
    }
    
    /**
     * Get file size from URI
     *
     * @param context The application context
     * @param uri The URI to get size for
     * @return The file size in bytes
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        // Try to get size from content provider
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    return it.getLong(sizeIndex)
                }
            }
        }
        
        // Fallback to opening the file and checking length
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            val fileSize = descriptor?.length ?: 0L
            descriptor?.close()
            return fileSize
        } catch (e: Exception) {
            AppLogger.e("Error getting file size: ${e.message}", e)
            return 0L
        }
    }
    
    /**
     * Copy file from URI to a local file
     *
     * @param context The application context
     * @param sourceUri The source URI
     * @param destFile The destination file
     * @return True if successful, false otherwise
     */
    fun copyUriToFile(context: Context, sourceUri: Uri, destFile: File): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val outputStream = FileOutputStream(destFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            
            true
        } catch (e: Exception) {
            AppLogger.e("Error copying file: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if a file is an image
     *
     * @param context The application context
     * @param uri The URI to check
     * @return True if it's an image, false otherwise
     */
    fun isImage(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri) ?: return false
        return mimeType.startsWith("image/")
    }
    
    /**
     * Check if a file is a PDF
     *
     * @param context The application context
     * @param uri The URI to check
     * @return True if it's a PDF, false otherwise
     */
    fun isPdf(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri) ?: return false
        return mimeType == "application/pdf"
    }
    
    /**
     * Format file size for display
     *
     * @param sizeBytes The size in bytes
     * @return Formatted string (e.g., "1.2 MB")
     */
    fun formatFileSize(sizeBytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = sizeBytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }
    
    /**
     * Read content from URI as string
     *
     * @param context The application context
     * @param uri The URI to read from
     * @return The content as string
     */
    fun readTextFromUri(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        return inputStream?.bufferedReader()?.use { it.readText() } ?: ""
    }
    
    /**
     * Load bitmap from URI with sample size calculation for memory efficiency
     *
     * @param context The application context
     * @param uri The URI to load bitmap from
     * @param maxWidth Maximum width constraint
     * @param maxHeight Maximum height constraint
     * @return The loaded bitmap
     */
    fun loadSampledBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int = 2048,
        maxHeight: Int = 2048
    ): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            AppLogger.e("Error loading bitmap: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calculate optimal sample size for loading large bitmaps efficiently
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}