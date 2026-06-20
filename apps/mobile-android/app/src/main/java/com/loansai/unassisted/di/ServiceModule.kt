package com.loansai.unassisted.di

import android.content.Context
import com.aallam.openai.client.OpenAI
import com.google.gson.Gson
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.remote.api.AIApi
import com.loansai.unassisted.data.remote.api.CamundaApi
import com.loansai.unassisted.domain.usecase.ai.impl.GPTAIAssistantService
import com.loansai.unassisted.service.ai.AIAssistantService
import com.loansai.unassisted.service.ocr.OCRService
import com.loansai.unassisted.service.ocr.impl.MLKitOCRService
import com.loansai.unassisted.service.voice.VoiceRecognitionService
import com.loansai.unassisted.service.voice.impl.GoogleVoiceRecognitionService
import com.loansai.unassisted.util.constants.PreferenceConstants
import com.loansai.unassisted.service.camunda.CamundaService
import com.loansai.unassisted.service.appwrite.AppwriteService
import com.loansai.unassisted.service.camunda.impl.CamundaServiceImpl
import com.loansai.unassisted.service.appwrite.impl.AppwriteServiceImpl
import com.loansai.unassisted.data.local.database.di.NetworkModule
import com.loansai.unassisted.service.bre.DirectBREProcessingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Named
import javax.inject.Singleton
import com.loansai.unassisted.service.DocumentProcessingService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import io.appwrite.Client
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.loansai.unassisted.service.ai.impl.GeminiDocumentService
import com.loansai.unassisted.data.remote.api.BREApi
import com.loansai.unassisted.service.bre.DirectBREProcessingServiceImpl
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import com.loansai.unassisted.service.email.BrevoEmailService


/**
 * Interface for Direct BRE Service
 */
interface DirectBREService {
    suspend fun calculateOffer(applicationId: String, data: Map<String, Any?>): Flow<Resource<Map<String, Any?>>>
}

/**
 * Implementation of Direct BRE Service
 */
class DirectBREServiceImpl @Inject constructor(
    private val breApi: BREApi
) : DirectBREService {
    override suspend fun calculateOffer(
        applicationId: String, 
        data: Map<String, Any?>
    ): Flow<Resource<Map<String, Any?>>> = flow {
        emit(Resource.Loading())
        
        try {
            AppLogger.d("Sending BRE request: $data")
            val response = breApi.calculateOffer(data as Map<String, @JvmSuppressWildcards Any>)
            AppLogger.d("BRE response: isSuccessful=${response.isSuccessful}, code=${response.code()}")
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!))
            } else {
                emit(Resource.Error("Failed to calculate offer: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Error calculating offer: ${e.message}"))
        }
    }
}



/**
 * Dagger Hilt module for service-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    /**
     * Provides ML Kit OCR service
     */
    @Provides
    @Singleton
    @Named("MLKitOCR")
    fun provideMLKitOCRService(
        @ApplicationContext context: Context, 
        documentProcessingService: DocumentProcessingService
    ): OCRService {
        return MLKitOCRService(context, documentProcessingService)
    }

    /**
     * Provides the selected OCR service based on user preferences
     */
    @Provides
    @Singleton
    fun provideSelectedOCRService(
        preferencesDataSource: PreferencesDataSource,
        @Named("MLKitOCR") mlKitOCRService: OCRService
    ): OCRService {
        // Get the selected OCR service type from preferences
        val serviceType = runBlocking {
            preferencesDataSource.getString(PreferenceConstants.PREF_OCR_SERVICE_TYPE)
                ?: PreferenceConstants.OCR_SERVICE_ML_KIT
        }
        
        // Return the appropriate service based on the type
        return when (serviceType) {
            PreferenceConstants.OCR_SERVICE_ML_KIT -> mlKitOCRService
            // Add other OCR services here when implemented
            else -> mlKitOCRService // Default to ML Kit
        }
    }

    /**
     * Provides Google Voice Recognition service
     */
    @Provides
    @Singleton
    fun provideVoiceRecognitionService(
        @ApplicationContext context: Context
    ): VoiceRecognitionService {
        return GoogleVoiceRecognitionService(context)
    }
    
    /**
     * Provides GPT AI Assistant Service implementation
     */
    @Provides
    @Singleton
    fun provideGPTAIAssistantService(
        aiApi: AIApi,
        openAI: OpenAI,
        gson: Gson
    ): GPTAIAssistantService {
        return GPTAIAssistantService(aiApi, openAI, gson)
    }
    
    /**
     * Provides AI Assistant Service
     */
    @Provides
    @Singleton
    fun provideAIAssistantService(
        gptAIAssistantService: GPTAIAssistantService
    ): AIAssistantService {
        return gptAIAssistantService
    }

    /**
    * Provides Document Processing Service
    */
    @Provides
    @Singleton
    fun provideDocumentProcessingService(
        @ApplicationContext context: Context
    ): DocumentProcessingService {
        return DocumentProcessingService(context)
    }

    /**
     * Provides Camunda Service
     */
    @Provides
    @Singleton
    fun provideCamundaService(
        camundaApi: CamundaApi
    ): CamundaService {
        return CamundaServiceImpl(camundaApi)
    }

    /**
     * Provides Appwrite Service
     */
    @Provides
    @Singleton
    fun provideAppwriteService(
        firestore: FirebaseFirestore, // Keep this for injection
        gson: Gson, // Keep this for injection
        client: Client // Keep this for injection
    ): AppwriteService {
        // Pass client and firestore to the constructor
        return AppwriteServiceImpl(client, firestore)
    }

    @Provides
    @Singleton
    fun provideGeminiDocumentService(
        @ApplicationContext context: Context
    ): GeminiDocumentService {
        return GeminiDocumentService(context)
    }

    /**
     * Provides Document Processing Service with BRE integration
     */
    @Provides
    @Singleton
    fun provideDirectBREProcessingService(
        breApi: BREApi
    ): DirectBREProcessingService {
        return DirectBREProcessingServiceImpl(breApi)
    }

    /**
     * Provides Direct BRE Service
     */
    @Provides
    @Singleton
    fun provideDirectBREService(breApi: BREApi): DirectBREService {
        return DirectBREServiceImpl(breApi)
    }

    /**
    * Provides BrevoEmailService for OTP emails
    */
    @Provides
    @Singleton
    fun provideBrevoEmailService(
        @Named("brevoClient") okHttpClient: OkHttpClient
    ): BrevoEmailService {
        return BrevoEmailService(okHttpClient)
    }

}