package com.loansai.unassisted.di

import android.content.Context
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for application-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides PreferencesDataSource
     */
    @Provides
    @Singleton
    fun providePreferencesDataSource(@ApplicationContext context: Context): PreferencesDataSource {
        return PreferencesDataSource(context)
    }
    
    // Add any other application-level dependencies here that aren't network-related
}