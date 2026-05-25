package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

object FirebaseServices {
    private const val TAG = "FirebaseServices"

    fun initialize(context: Context) {
        try {
            // First initialize Firebase if not initialized
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Firebase initialized successfully.")
            }

            // Set up Firebase App Check
            setupAppCheck()

            // Set up Firebase Remote Config
            setupRemoteConfig()

            // Set up Firebase Messaging
            setupMessagingToken()

            // Setup Crashlytics
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Log.d(TAG, "Crashlytics collection enabled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing firebase services: ${e.message}", e)
        }
    }

    private fun setupAppCheck() {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            // Support debug provider factory for development and Play Integrity provider factory for production
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "Firebase App Check initialized with Debug provider (Play Integrity fallback ready).")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up App Check: ${e.message}")
        }
    }

    private fun setupRemoteConfig() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // Fetch every hour
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            // Define defaults
            val defaults = mapOf(
                "maintenance_mode" to false,
                "minimum_app_version" to "1.0",
                "show_premium_banner" to true,
                "default_currency" to "USD",
                "reminder_feature_enabled" to true
            )
            remoteConfig.setDefaultsAsync(defaults)

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote Config fetched. Config params updated: $updated")
                } else {
                    Log.w(TAG, "Remote config fetch failed.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating remote config: ${e.message}")
        }
    }

    private fun setupMessagingToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d(TAG, "Retrieved FCM Token: $token")
                // In a production app, we would send this to the Firestore collection settings/user profile path.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting messaging services: ${e.message}")
        }
    }

    /**
     * Upload User Profile Picture safely to Storage
     * Stores files under users/{uid}/profile/
     */
    suspend fun uploadProfileImage(uid: String, fileUri: Uri, context: Context): String? {
        return try {
            val storageRef = FirebaseStorage.getInstance().reference
            val profileImageRef = storageRef.child("users/$uid/profile/avatar_${System.currentTimeMillis()}.jpg")

            val uploadTask = profileImageRef.putFile(fileUri).await()
            val downloadUrl = uploadTask.metadata?.reference?.downloadUrl?.await()?.toString()
            Log.d(TAG, "Profile picture uploaded successfully. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Profile picture upload failed: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    /**
     * Analytics Tracker Helper
     */
    fun trackEvent(context: Context, eventName: String, params: Map<String, Any> = emptyMap()) {
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.logEvent(eventName) {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> param(key, value)
                        is Long -> param(key, value)
                        is Double -> param(key, value)
                        is Boolean -> param(key, if (value) 1L else 0L)
                    }
                }
            }
            Log.d(TAG, "Analytics Event Logged: $eventName, params=$params")
        } catch (e: Exception) {
            Log.e(TAG, "Failed logging event $eventName: ${e.message}")
        }
    }

    /**
     * Crashlytics Custom Logs & Exception Recorder Helper
     */
    fun logException(exception: Throwable, message: String? = null) {
        try {
            message?.let { FirebaseCrashlytics.getInstance().log(it) }
            FirebaseCrashlytics.getInstance().recordException(exception)
        } catch (e: Exception) {
            Log.e(TAG, "Crashlytics tracking failed: ${e.message}")
        }
    }

    /**
     * Remote config helpers
     */
    fun isMaintenanceMode(): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean("maintenance_mode")
        } catch (e: Exception) {
            false
        }
    }

    fun isReminderFeatureEnabled(): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean("reminder_feature_enabled")
        } catch (e: Exception) {
            true
        }
    }

    fun getShowPremiumBanner(): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean("show_premium_banner")
        } catch (e: Exception) {
            true
        }
    }

    fun getMinimumVersion(): String {
        return try {
            FirebaseRemoteConfig.getInstance().getString("minimum_app_version")
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun getDefaultCurrency(): String {
        return try {
            FirebaseRemoteConfig.getInstance().getString("default_currency")
        } catch (e: Exception) {
            "USD"
        }
    }
}
