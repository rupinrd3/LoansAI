package com.loansai.unassisted.data.local.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.util.constants.PreferenceConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.loansai.unassisted.util.logger.AppLogger
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDateTime
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.ApplicationStep
import com.google.gson.reflect.TypeToken
import com.loansai.unassisted.domain.model.Tradeline


/**
 * Extension property for DataStore
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PreferenceConstants.DATA_STORE_NAME
)

private data class ApplicationCache(
    val application: LoanApplication,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Enhanced DataSource for handling preferences and application caching using DataStore
 */
@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    private val gson = Gson()
    
    /**
     * Save a string value
     */
    suspend fun saveString(key: String, value: String) {
        val preferencesKey = stringPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }
    
    /**
     * Get a string value
     */
    suspend fun getString(key: String): String? {
        val preferencesKey = stringPreferencesKey(key)
        var result: String? = null
        dataStore.edit { preferences ->
            result = preferences[preferencesKey]
        }
        return result
    }
    
    /**
     * Get a string value as Flow
     */
    fun getStringFlow(key: String): Flow<String?> {
        val preferencesKey = stringPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences[preferencesKey]
        }
    }
    
    /**
     * Save a boolean value
     */
    suspend fun saveBoolean(key: String, value: Boolean) {
        val preferencesKey = booleanPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }
    
    /**
     * Get a boolean value
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val preferencesKey = booleanPreferencesKey(key)
        var result: Boolean = defaultValue
        dataStore.edit { preferences ->
            result = preferences[preferencesKey] ?: defaultValue
        }
        return result
    }
    
    /**
     * Get a boolean value as Flow
     */
    fun getBooleanFlow(key: String, defaultValue: Boolean = false): Flow<Boolean> {
        val preferencesKey = booleanPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences[preferencesKey] ?: defaultValue
        }
    }
    
    /**
     * Save an integer value
     */
    suspend fun saveInt(key: String, value: Int) {
        val preferencesKey = intPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }
    
    /**
     * Get an integer value
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int {
        val preferencesKey = intPreferencesKey(key)
        var result: Int = defaultValue
        dataStore.edit { preferences ->
            result = preferences[preferencesKey] ?: defaultValue
        }
        return result
    }
    
    /**
     * Get an integer value as Flow
     */
    fun getIntFlow(key: String, defaultValue: Int = 0): Flow<Int> {
        val preferencesKey = intPreferencesKey(key)
        return dataStore.data.map { preferences ->
            preferences[preferencesKey] ?: defaultValue
        }
    }
    
    /**
     * Remove a preference
     */
    suspend fun remove(key: String) {
        val stringKey = stringPreferencesKey(key)
        val booleanKey = booleanPreferencesKey(key)
        val intKey = intPreferencesKey(key)
        
        dataStore.edit { preferences ->
            if (preferences.contains(stringKey)) {
                preferences.remove(stringKey)
            }
            if (preferences.contains(booleanKey)) {
                preferences.remove(booleanKey)
            }
            if (preferences.contains(intKey)) {
                preferences.remove(intKey)
            }
        }
    }
    
    /**
     * Clear all preferences
     */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // Authentication-related helpers
    
    /**
     * Save current user's authentication token
     */
    suspend fun saveAuthToken(token: String) {
        saveString(PreferenceConstants.PREF_AUTH_TOKEN, token)
    }
    
    /**
     * Get current user's authentication token
     */
    suspend fun getAuthToken(): String? {
        return getString(PreferenceConstants.PREF_AUTH_TOKEN)
    }
    
    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Flow<Boolean> {
        return getStringFlow(PreferenceConstants.PREF_AUTH_TOKEN)
            .map { token -> !token.isNullOrEmpty() }
    }
    
    // Application data helpers
    
    /**
     * Save current application ID
     */
    suspend fun saveCurrentApplicationId(applicationId: String) {
        saveString(PreferenceConstants.PREF_CURRENT_APPLICATION_ID, applicationId)
    }
    
    /**
     * Get current application ID
     */
    suspend fun getCurrentApplicationId(): String? {
        return getString(PreferenceConstants.PREF_CURRENT_APPLICATION_ID)
    }
    
    /**
     * Save simplified application data cache
     */
    suspend fun cacheApplicationData(application: LoanApplication) {
        val cacheData = ApplicationCache(
            application = application,
            timestamp = System.currentTimeMillis()
        )
        val json = gson.toJson(cacheData)
        saveString(PreferenceConstants.PREF_APPLICATION_CACHE, json)
        
        // Also save the application ID separately for quick access
        saveString(PreferenceConstants.PREF_CURRENT_APPLICATION_ID, application.id)
    }


    suspend fun getCachedApplicationData(): LoanApplication? {
        try {
            val json = getString(PreferenceConstants.PREF_APPLICATION_CACHE)
            AppLogger.d("Retrieved cached application JSON: ${json?.take(100)}...")
            
            if (json.isNullOrEmpty()) {
                AppLogger.d("No cached application data found")
                return null
            }
            
            try {
                val cacheData = gson.fromJson(json, ApplicationCache::class.java)
                
                // Check if cache is too old (more than 7 days)
                val isExpired = System.currentTimeMillis() - cacheData.timestamp > 7 * 24 * 60 * 60 * 1000
                if (isExpired) {
                    AppLogger.w("Cached application data is expired but will still be used")
                }
                
                // Create a dummy application if the one from cache is incomplete
                if (cacheData.application.id.isBlank()) {
                    AppLogger.w("Cached application has blank ID, returning null")
                    return null
                }
                
                AppLogger.d("Successfully retrieved cached application: ${cacheData.application.id}")
                return cacheData.application
                
            } catch (e: Exception) {
                AppLogger.e("Error parsing cached application data: ${e.message}", e)
                
                // Try to recover using whatever information we have
                try {
                    // Try to extract JSON manually if possible
                    val appIdRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val appIdMatch = appIdRegex.find(json)
                    val appId = appIdMatch?.groupValues?.get(1) ?: getString(PreferenceConstants.PREF_CURRENT_APPLICATION_ID)
                    
                    if (!appId.isNullOrEmpty()) {
                        AppLogger.d("Creating fallback application with extracted ID: $appId")
                        return createFallbackApplication(appId)
                    }
                } catch (e2: Exception) {
                    AppLogger.e("Error in fallback recovery: ${e2.message}", e2)
                    // Continue to final fallback
                }
                
                // Try to recover using just the stored application ID
                val currentAppId = getString(PreferenceConstants.PREF_CURRENT_APPLICATION_ID)
                if (!currentAppId.isNullOrEmpty()) {
                    AppLogger.d("Creating fallback application with ID: $currentAppId")
                    return createFallbackApplication(currentAppId)
                }
                
                return null
            }
        } catch (e: Exception) {
            AppLogger.e("Error accessing cache: ${e.message}", e)
            return null
        }
    }

    // Add this helper method
    private fun createFallbackApplication(id: String): LoanApplication {
        val userId = FirebaseAuth.getInstance().currentUser?.phoneNumber ?: "unknown-user"
        
        return LoanApplication(
            id = id,
            userId = userId,
            createdAt = LocalDateTime.now(),
            lastUpdatedAt = LocalDateTime.now(),
            applicationStatus = ApplicationStatus.CREATED,
            currentStep = ApplicationStep.PAN_VERIFICATION,
            completedSteps = emptyList()
        )
    }
    
    /**
     * Clear cached application data
     */
    suspend fun clearCachedApplicationData() {
        remove(PreferenceConstants.PREF_APPLICATION_CACHE)
    }
    /**
    * Get the selected OCR service
    */
    suspend fun getSelectedOCRService(): String {
        return getString(PreferenceConstants.PREF_SELECTED_OCR_SERVICE) ?: "ml_kit"
    }

    /**
    * Save the selected OCR service
    */
    suspend fun saveSelectedOCRService(service: String) {
        saveString(PreferenceConstants.PREF_SELECTED_OCR_SERVICE, service)
    }

    /**
    * Check if AI assistant is enabled
    */
    suspend fun isAIAssistantEnabled(): Boolean {
        return getBoolean(PreferenceConstants.PREF_AI_ASSISTANT_ENABLED, true)
    }

    /**
    * Set AI assistant enabled status
    */
    suspend fun setAIAssistantEnabled(enabled: Boolean) {
        saveBoolean(PreferenceConstants.PREF_AI_ASSISTANT_ENABLED, enabled)
    }

    /**
    * Clear all preference data
    */
    suspend fun clearAll() {
        clear()
    }


    /**
    * Get the application context
    */
    fun getContext(): Context {
        return context
    }

    

    /**
    * Save tradelines for a PAN number in local preferences
    */
    suspend fun saveTradelinesForPan(panNumber: String, tradelines: List<Tradeline>) {
        try {
            val key = "tradelines_${panNumber}"
            val json = gson.toJson(tradelines)
            saveString(key, json)
            AppLogger.d("Saved ${tradelines.size} tradelines to preferences for PAN: $panNumber")
        } catch (e: Exception) {
            AppLogger.e("Error saving tradelines to preferences: ${e.message}", e)
        }
    }

    /**
    * Get cached tradelines for a PAN number
    */
    suspend fun getTradelinesForPan(panNumber: String): List<Tradeline>? {
        try {
            val key = "tradelines_${panNumber}"
            val json = getString(key)
            
            if (json.isNullOrEmpty()) {
                AppLogger.d("No cached tradelines found for PAN: $panNumber")
                return null
            }
            
            val type = object : TypeToken<List<Tradeline>>() {}.type
            val tradelines: List<Tradeline> = gson.fromJson(json, type)
            AppLogger.d("Retrieved ${tradelines.size} tradelines from preferences for PAN: $panNumber")
            return tradelines
        } catch (e: Exception) {
            AppLogger.e("Error retrieving tradelines from preferences: ${e.message}", e)
            return null
        }
    }

    /**
    * Save the PAN number associated with an application ID
    */
    suspend fun savePanNumberForApplication(applicationId: String, panNumber: String) {
        try {
            val key = "pan_number_${applicationId}"
            saveString(key, panNumber)
            AppLogger.d("Saved PAN number $panNumber for application: $applicationId")
        } catch (e: Exception) {
            AppLogger.e("Error saving PAN number for application: ${e.message}", e)
        }
    }

    /**
    * Get the cached PAN number for an application ID
    */
    suspend fun getPanNumberForApplication(applicationId: String): String? {
        try {
            val key = "pan_number_${applicationId}"
            val panNumber = getString(key)
            if (!panNumber.isNullOrEmpty()) {
                AppLogger.d("Retrieved PAN number $panNumber for application: $applicationId")
            }
            return panNumber
        } catch (e: Exception) {
            AppLogger.e("Error retrieving PAN number for application: ${e.message}", e)
            return null
        }
    }


}