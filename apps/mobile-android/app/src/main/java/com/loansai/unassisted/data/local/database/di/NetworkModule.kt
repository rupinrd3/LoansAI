package com.loansai.unassisted.data.local.database.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.data.remote.api.AIApi
import com.loansai.unassisted.data.remote.api.AuthApi
import com.loansai.unassisted.data.remote.api.CamundaApi
import com.loansai.unassisted.data.remote.api.DocumentApi
import com.loansai.unassisted.data.remote.api.EmployerApi
import com.loansai.unassisted.data.remote.api.LlmProcessingApi
import com.loansai.unassisted.data.remote.api.LoanApi
import com.loansai.unassisted.data.remote.api.PANApi
import com.loansai.unassisted.data.remote.interceptor.AuthInterceptor
import com.loansai.unassisted.util.constants.ApiConstants
import com.loansai.unassisted.data.local.source.PreferencesDataSource 
import com.loansai.unassisted.util.logger.AppLogger
import com.loansai.unassisted.data.remote.api.BREApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.inject.Qualifier
import javax.inject.Named


/**
 * Qualifier annotation for main Retrofit instance
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainRetrofit

/**
 * Qualifier annotation for LLM API Retrofit instance
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LlmRetrofit

/**
 * Dagger Hilt module for network-related dependencies
 * Updated for v1.5.0 with Backend LLM API support
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides Gson for JSON serialization/deserialization
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create()
    }

    /**
     * Provides HttpLoggingInterceptor for API call logging
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * Provides AuthInterceptor for adding auth tokens to requests
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(preferencesDataSource: PreferencesDataSource): AuthInterceptor {
        // Manually set the field right after creation
        val interceptor = AuthInterceptor()
        interceptor.preferencesDataSource = preferencesDataSource
        return interceptor
    }

    /**
     * Provides OkHttpClient with enhanced logging and interceptors
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                AppLogger.d("REQUEST URL: ${request.url}")
                AppLogger.d("REQUEST METHOD: ${request.method}")
                
                val response = chain.proceed(request)
                
                AppLogger.d("RESPONSE CODE: ${response.code}")
                
                if (!response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string() ?: "Empty response body"
                        AppLogger.e("ERROR RESPONSE: $responseBody")
                        // Re-create the response since we've consumed the body
                        return@addInterceptor response.newBuilder()
                            .body(ResponseBody.create(response.body?.contentType(), responseBody))
                            .build()
                    } catch (e: Exception) {
                        AppLogger.e("Error reading response body: ${e.message}")
                    }
                }
                
                response
            }
            .connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)

        // In debug mode, add SSL trust for development environment
        if (BuildConfig.DEBUG) {
            try {
                // Create a trust manager that does not validate certificate chains
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                // Install the all-trusting trust manager
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                
                // Create a SSL socket factory with our all-trusting manager
                val sslSocketFactory = sslContext.socketFactory
                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return builder.build()
    }

    /**
     * Provides Main Retrofit instance - single instance for all APIs
     */
    @Provides
    @Singleton
    @MainRetrofit
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        val baseUrl = if (BuildConfig.DEBUG) {
            ApiConstants.BASE_URL_DEV
        } else {
            ApiConstants.BASE_URL
        }
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provides Retrofit instance for Backend LLM API (could be on a different server/port)
     * NEW for v1.5.0
     */
    @Provides
    @Singleton
    @LlmRetrofit
    fun provideBackendLlmRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        val baseUrl = if (BuildConfig.DEBUG) {
            ApiConstants.BACKEND_LLM_API_URL_DEV
        } else {
            ApiConstants.BACKEND_LLM_API_URL
        }
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provides Camunda API client
     */
    @Provides
    @Singleton
    fun provideCamundaApi(): CamundaApi {
        // Create a dedicated Retrofit instance for Camunda
        val camundaUrl = "https://camunda-8-7-440765277306.us-central1.run.app/"
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(camundaUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
        
        return retrofit.create(CamundaApi::class.java)
    }

    // API providers
    @Provides
    @Singleton
    fun provideAuthApi(@MainRetrofit retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLoanApi(@MainRetrofit retrofit: Retrofit): LoanApi {
        return retrofit.create(LoanApi::class.java)
    }

    @Provides
    @Singleton
    fun providePANApi(@MainRetrofit retrofit: Retrofit): PANApi {
        return retrofit.create(PANApi::class.java)
    }

    @Provides
    @Singleton
    fun provideEmployerApi(@MainRetrofit retrofit: Retrofit): EmployerApi {
        return retrofit.create(EmployerApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDocumentApi(@MainRetrofit retrofit: Retrofit): DocumentApi {
        return retrofit.create(DocumentApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAIApi(@MainRetrofit retrofit: Retrofit): AIApi {
        return retrofit.create(AIApi::class.java)
    }

    /**
    * Provides the BRE API client for direct Python worker
    */
    @Provides
    @Singleton
    fun provideBREApi(okHttpClient: OkHttpClient, gson: Gson): BREApi {
        // Cloud Run URL validated from terminal tests
        val breUrl = "https://bre-worker-440765277306.us-central1.run.app/"
        
        // Create a dedicated client with extended timeouts for BRE
        val breHttpClient = okHttpClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)  // Increase timeout for BRE calls
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                AppLogger.d("BRE API Request: ${request.method} ${request.url}")
                
                val response = chain.proceed(request)
                AppLogger.d("BRE API Response: ${response.code} for ${request.url}")
                
                if (!response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string() ?: "Empty response body"
                        AppLogger.e("BRE API ERROR: ${response.code} - $responseBody")
                        // Re-create response since we consumed the body
                        return@addInterceptor response.newBuilder()
                            .body(ResponseBody.create(response.body?.contentType(), responseBody))
                            .build()
                    } catch (e: Exception) {
                        AppLogger.e("Error reading BRE response body: ${e.message}")
                    }
                }
                response
            }
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(breUrl)
            .client(breHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        val breApi = retrofit.create(BREApi::class.java)
        
        // Validate the API client was created successfully
        AppLogger.d("BRE API client created successfully for URL: $breUrl")
        
        return breApi
    }


    /**
    * Provides a specific OkHttpClient for Brevo API communications
    */
    @Provides
    @Singleton
    @Named("brevoClient")
    fun provideBrevoHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                AppLogger.d("Brevo API Request: ${request.method} ${request.url}")
                
                val response = chain.proceed(request)
                
                if (!response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string() ?: "Empty response body"
                        AppLogger.e("Brevo API Error: ${response.code} - $responseBody")
                        
                        // Re-create response since we consumed the body
                        return@addInterceptor response.newBuilder()
                            .body(ResponseBody.create(response.body?.contentType(), responseBody))
                            .build()
                    } catch (e: Exception) {
                        AppLogger.e("Error reading Brevo response: ${e.message}")
                    }
                } else {
                    AppLogger.d("Brevo API Success: ${response.code}")
                }
                
                response
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }



    /**
     * Provides API interface for Backend LLM Processing services
     * NEW for v1.5.0
     */
    @Provides
    @Singleton
    fun provideLlmProcessingApi(@LlmRetrofit retrofit: Retrofit): LlmProcessingApi {
        return retrofit.create(LlmProcessingApi::class.java)
    }
}