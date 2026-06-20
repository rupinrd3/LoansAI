package com.loansai.unassisted.util.context

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * Utility class to hold application context and current activity reference
 * Needed for Firebase Phone Authentication which requires an activity context
 */
object ApplicationContextProvider {
    private var applicationContext: Context? = null
    private var currentActivity = WeakReference<Activity>(null)

    /**
     * Initialize with application context
     * Call this from Application.onCreate()
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Get the application context
     */
    fun getApplicationContext(): Context {
        return applicationContext ?: throw IllegalStateException("ApplicationContextProvider not initialized")
    }

    /**
     * Set current activity reference
     * Call this from each Activity.onCreate()
     */
    fun setCurrentActivity(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    /**
     * Get current activity
     * May return null if activity has been garbage collected
     */
    fun getCurrentActivity(): Activity? {
        return currentActivity.get()
    }
}