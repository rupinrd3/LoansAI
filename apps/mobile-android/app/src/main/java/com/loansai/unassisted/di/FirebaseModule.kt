package com.loansai.unassisted.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions // <-- Import added
import com.google.firebase.functions.ktx.functions     // <-- Import added
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing Firebase-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Ensures Firebase is initialized before providing any Firebase services
     */
    @Provides
    @Singleton
    fun provideFirebaseApp(
        @ApplicationContext context: Context
    ): FirebaseApp {
        // Ensure initialization only happens once.
        return FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
    }

    /**
     * Provides FirebaseAuth instance
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    /**
     * Provides FirebaseFirestore instance
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Consider if offline persistence is needed
            .build()
        return Firebase.firestore.apply {
            firestoreSettings = settings
        }
    }

    /**
     * Provides FirebaseStorage instance
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return Firebase.storage
    }

    /**
     * Provides Firebase Realtime Database instance with specific region
     */
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        // Specify the database URL if it's not the default one
        // Example for Singapore region:
        return FirebaseDatabase.getInstance("https://loansai-default-rtdb.asia-southeast1.firebasedatabase.app").apply {
            // Enable offline persistence if needed
             setPersistenceEnabled(true)
        }
        // If using the default database URL:
        // return Firebase.database.apply { setPersistenceEnabled(true) }
    }

    /**
     * Provides FirebaseFunctions instance
     */
    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions {
        // You might want to specify a region if needed, e.g., Firebase.functions("asia-southeast1")
        return Firebase.functions
    }
}