package com.loansai.unassisted.di

import android.util.Log
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.loansai.unassisted.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Dagger Hilt module for OpenAI related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object OpenAIModule {

    /**
     * Provides OpenAI client
     * 
     * @param apiKey The OpenAI API Key
     * @return OpenAI client instance
     */
    @Provides
    @Singleton
    fun provideOpenAIClient(apiKey: String): OpenAI {
        return OpenAI(
            config = OpenAIConfig(
                token = apiKey,
                timeout = Timeout(socket = 60.seconds),
                logging = LoggingConfig(
                    logLevel = if (BuildConfig.DEBUG) LogLevel.All else LogLevel.None
                )
            )
        )
    }
    
    /**
     * Provides OpenAI API Key
     * In production, you should securely store and retrieve this key
     * 
     * @return OpenAI API Key
     */
    // TODO: Replace with your actual API key or a secure method to retrieve it
    // In production, consider storing this in a more secure location
@Provides @Singleton
fun provideOpenAIApiKey(): String {
  val key = BuildConfig.OPENAI_API_KEY
  Log.d("OpenAIModule", "API key prefix: ${key.take(10)}…")
  return key
}


}