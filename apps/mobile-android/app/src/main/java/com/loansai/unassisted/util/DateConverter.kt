package com.loansai.unassisted.util

import com.google.firebase.Timestamp
import com.loansai.unassisted.util.logger.AppLogger
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

/**
 * Utility class to convert between LocalDateTime and Firestore Timestamp
 */
object DateConverter {
    
    /**
     * Convert LocalDateTime to Firestore Timestamp
     */
    fun toTimestamp(localDateTime: LocalDateTime): Timestamp {
        return try {
            val epochSeconds = localDateTime.toEpochSecond(ZoneOffset.UTC)
            val nanos = localDateTime.nano
            Timestamp(epochSeconds, nanos)
        } catch (e: Exception) {
            AppLogger.e("Error converting LocalDateTime to Timestamp: ${e.message}", e)
            Timestamp.now()
        }
    }
    
    /**
     * Convert Firestore Timestamp to LocalDateTime
     */
    fun fromTimestamp(timestamp: Timestamp): LocalDateTime {
        return try {
            LocalDateTime.ofEpochSecond(
                timestamp.seconds,
                timestamp.nanoseconds,
                ZoneOffset.UTC
            )
        } catch (e: Exception) {
            AppLogger.e("Error converting Timestamp to LocalDateTime: ${e.message}", e)
            LocalDateTime.now()
        }
    }
    
    /**
     * Parse a value from Firestore that might be a timestamp, date, or string
     * and convert it to LocalDateTime
     */
    fun parseFirestoreValue(value: Any?): LocalDateTime? {
        return when (value) {
            is Timestamp -> fromTimestamp(value)
            is Date -> LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
            is String -> try {
                LocalDateTime.parse(value)
            } catch (e: Exception) {
                AppLogger.e("Failed to parse date string: $value", e)
                null
            }
            else -> null
        }
    }
}