package com.loansai.unassisted

import android.app.Application
import com.google.firebase.FirebaseApp
import com.loansai.unassisted.util.context.ApplicationContextProvider
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize context provider
        ApplicationContextProvider.init(this)
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize Firebase (make sure this is placed before any Firebase usage)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Timber.e("Firebase initialization error: ${e.message}")
        }
    }
}