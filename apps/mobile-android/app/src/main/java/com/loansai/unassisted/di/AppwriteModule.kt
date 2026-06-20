package com.loansai.unassisted.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.appwrite.Client
import javax.inject.Singleton

/**
 * Dagger Hilt module for Appwrite-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppwriteModule {

    @Provides
    @Singleton
    fun provideAppwriteClient(@ApplicationContext context: Context): Client {
        return Client(context)
            .setEndpoint("https://fra.cloud.appwrite.io/v1")
            .setProject("67fb549a0036c841fb32")
    }

}