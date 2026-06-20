package com.loansai.unassisted.data.remote.interceptor

import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.util.constants.ApiConstants
import com.loansai.unassisted.util.constants.PreferenceConstants
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor to add authentication token to requests
 */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    
    @Inject
    lateinit var preferencesDataSource: PreferencesDataSource
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for login and registration endpoints
        if (originalRequest.url.toString().contains("auth")) {
            return chain.proceed(originalRequest)
        }
        
        // Get token from preferences
        val token = runBlocking {
            preferencesDataSource.getString(PreferenceConstants.PREF_AUTH_TOKEN)
        }
        
        // Add token to request if available
        val request = if (token != null && token.isNotEmpty()) {
            originalRequest.newBuilder()
                .header(ApiConstants.HEADER_AUTHORIZATION, "Bearer $token")
                .header(ApiConstants.HEADER_CONTENT_TYPE, "application/json")
                .header(ApiConstants.HEADER_ACCEPT, "application/json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header(ApiConstants.HEADER_CONTENT_TYPE, "application/json")
                .header(ApiConstants.HEADER_ACCEPT, "application/json")
                .build()
        }
        
        return chain.proceed(request)
    }
}