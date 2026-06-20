package com.loansai.unassisted.util.extensions

import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Extension functions for Kotlin Flow
 */

/**
 * Converts a Flow to a Resource Flow for cleaner handling of loading, success, and error states
 */
fun <T> Flow<T>.asResourceFlow(): Flow<Resource<T>> = this
    .map { Resource.Success(it) as Resource<T> }
    .onStart { emit(Resource.Loading()) }
    .catch { e ->
        AppLogger.e("Flow error: ${e.message}", e)
        emit(Resource.Error(e.message ?: "Unknown error occurred"))
    }

/**
 * A sealed class representing a resource that is either loading, success, or error
 */
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    class Loading<T> : Resource<T>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = if (this is Success) data else null
    fun errorOrNull(): String? = if (this is Error) message else null
}