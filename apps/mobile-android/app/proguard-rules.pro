# Default Proguard rules for Android (often included via getDefaultProguardFile)
# -dontusemixedcaseclassnames # Useful on case-insensitive file systems
# -dontskipnonpubliclibraryclasses
# -verbose

# Keep application entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep classes used by XML layouts
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class * extends android.view.View {
   public void set*(...);
}

# Keep R class members
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep Parcelable classes and their CREATOR fields
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
# More general rule for Parcelables if the above isn't enough
-keep class * implements android.os.Parcelable {
  *; # Keep all fields and methods
}

# General attributes to keep
-keepattributes Signature # For generics
-keepattributes Exceptions # For better stack traces
-keepattributes InnerClasses # Important for some libraries
-keepattributes *Annotation* # For libraries relying on annotations (like Gson, Retrofit, Hilt)

# Firebase
# The broad rule is okay, but Firebase libraries often publish their own consumer Proguard rules.
# If issues arise, check Firebase documentation for more specific rules.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; } # Often needed with Firebase
-dontwarn com.google.android.gms.** # Suppress warnings if GMS Core classes are missing in some build variants

# Retrofit and OkHttp
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.Platform$Java8 # If using Java 8+ features with Retrofit

# Gson
# Essential for serialization/deserialization if you use it.
-keep class com.google.gson.** { *; }
# Keeping model classes is also crucial for Gson.
-keepclassmembers class com.loansai.unassisted.domain.model.** {
  @com.google.gson.annotations.SerializedName <fields>;
  <init>(...); # Keep constructors Gson might use
}
-keepclassmembers class com.loansai.unassisted.data.model.** {
  @com.google.gson.annotations.SerializedName <fields>;
  <init>(...);
}
# Keep enums used with Gson
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    @com.google.gson.annotations.SerializedName <fields>;
}

# Your Application's Model Classes (already present, which is good)
-keep class com.loansai.unassisted.domain.model.** { *; }
-keep class com.loansai.unassisted.data.model.** { *; }

# Hilt / Dagger
# Hilt generally manages its own rules well via its Gradle plugin.
# These are usually safe to have as a fallback.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.DefineComponent class * { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.Provides class * { *; }
-keep @javax.inject.Inject class * { *; }
-keep @javax.inject.Singleton class * { *; }

# OpenAI Client (AAllam) & Ktor (its dependency)
-keep class com.aallam.openai.** { *; }
-keep interface com.aallam.openai.** { *; }
-keep class io.ktor.** { *; } # Ktor is used by the OpenAI client
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.** # Ktor can sometimes have warnings with Proguard

# Google AI Generative Client (Gemini)
-keep class com.google.ai.client.** { *; }
-keep interface com.google.ai.client.** { *; }
# It might use internal kotlinx.serialization, which is usually handled if you keep the client.
# If you face serialization issues with Gemini:
# -keep class kotlinx.serialization.** { *; }
# -keepclassmembers class * { @kotlinx.serialization.Serializable <fields>; }
# -keepclassmembers class * { @kotlinx.serialization.SerialName <fields>; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-keep class kotlin.coroutines.** { *; } # General keep for coroutines
-dontwarn kotlin.coroutines.jvm.internal.* # Often necessary

# Jetpack Compose
# Compose handles most of its Proguard rules automatically if enabled in build.gradle.
# This broad rule can sometimes be removed if app size is a concern and everything works.
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
# Keep specific Compose runtime classes if issues arise
-keepclassmembers class androidx.compose.runtime.Composer { *; }
-keepclassmembers class androidx.compose.runtime.Recomposer { *; }

# Jetpack DataStore
-keep class androidx.datastore.** { *; }

# Appwrite SDK (This is CRITICAL for your reported issue)
-keep class io.appwrite.** { *; }
-keep interface io.appwrite.** { *; }
# If the above is too broad and you want to be more specific (requires knowing Appwrite internals):
# -keep class io.appwrite.services.Databases { *; }
# -keep class io.appwrite.models.Document { *; }
# -keep public class io.appwrite.Client { *; }
# ... and any other specific classes/methods Appwrite SDK might need.
# For now, the broad `io.appwrite.**` is safer given the crash.

# ML Kit (if you use more than just the basic text recognition)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; } # For text recognition
# Add rules for other ML Kit features if you use them.

# Coil (Image Loading) - Usually doesn't need specific rules but good to have if issues arise.
# -keep class coil.** { *; }

# Timber (Logging) - Generally doesn't need rules for runtime if only used for debug.
# -keep class timber.log.Timber { *; }
# -keep class timber.log.Timber$Tree { *; }
# -keep class timber.log.Timber$DebugTree { *; }

# Add rules for any other third-party libraries you use.
# Always check their official documentation for recommended Proguard rules.

# --- Retrofit, OkHttp, Okio, Gson ---
-dontwarn retrofit2.Platform$Java8
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Keep your API interfaces and their methods
-keep interface com.loansai.unassisted.data.remote.api.AuthApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.AIApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.CamundaApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.DocumentApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.EmployerApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.LlmProcessingApi { *; } # Crucial for your crash
-keep interface com.loansai.unassisted.data.remote.api.LoanApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.PANApi { *; }
-keep interface com.loansai.unassisted.data.remote.api.BREApi { *; }

# Keep your Data Transfer Objects (DTOs) and their fields
# This is a general rule; be more specific if needed.
-keep class com.loansai.unassisted.data.model.** { *; }
-keepclassmembers class com.loansai.unassisted.data.model.** { *; }

# If you use @SerializedName annotation from Gson, keep it
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Hilt ---
# Hilt typically manages its own rules, but these are common additions if issues arise.
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class com.loansai.unassisted.Hilt_*.** { *; } # Adjust if your Hilt generated class names differ
-keep class hilt_aggregated_deps.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @dagger.hilt.android.qualifiers.ApplicationContext android.content.Context appContext;
    @dagger.hilt.android.qualifiers.ActivityContext android.content.Context activityContext;
}

# Keep Kotlin metadata for reflection if used by libraries
-keepattributes KotlinDefaultMask, InnerClasses, EnclosingMethod, AnnotationDefault, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations

# Add any other specific rules your other libraries might require.