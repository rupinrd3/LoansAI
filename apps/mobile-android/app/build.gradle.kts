import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
} else {
    // Fallback to gradle.properties for now
    println("Warning: keystore.properties not found, using gradle.properties")
}


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.firebase.crashlytics")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.loansai.unassisted"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.loansai.unassisted"
        minSdk = 32
        targetSdk = 35
        versionCode = 10003
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Use keystoreProperties if available, otherwise fallback to project properties
        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            "\"${keystoreProperties.getProperty("openai_api_key") ?: project.findProperty("openai_api_key") ?: ""}\""
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${keystoreProperties.getProperty("gemini_api_key") ?: project.findProperty("gemini_api_key") ?: ""}\""
        )
        buildConfigField(
            "String",
            "BREVO_API_KEY",
            "\"${keystoreProperties.getProperty("brevo_api_key") ?: project.findProperty("brevo_api_key") ?: ""}\""
        )
    }

    // Add signing config
    signingConfigs {
        create("release") {
            storeFile = file("/media/Adata_Data/Loan_App_Project/loansai-keystore.jks")
            storePassword = keystoreProperties.getProperty("storePassword") ?: System.getenv("LOANSAI_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: System.getenv("LOANSAI_KEY_ALIAS") ?: ""
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: System.getenv("LOANSAI_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // Fix deprecated kotlinOptions warning
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildToolsVersion = "35.0.0"
    ndkVersion = "29.0.13113456 rc1"
}

kapt {
    correctErrorTypes = true
    useBuildCache = false
    javacOptions {
        option("-source", "17")
        option("-target", "17")
    }
    arguments {
        arg("verbose", "true")
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
//    implementation(libs.androidx.ktx)
    
    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.ui:ui-graphics:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    // implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    // implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    

    
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    // implementation("com.google.firebase:firebase-crashlytics-gradle")
    implementation("com.google.firebase:firebase-installations")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-database")
    // implementation("com.google.gms:google-services")

    
       
    // Retrofit & Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Room Database
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // ksp("androidx.room:room-compiler:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1")
    
    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-android-compiler:2.55")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // OCR - ML Kit
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    
    // Voice Recognition
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    
    // Lottie for animations
    implementation("com.airbnb.android:lottie-compose:6.1.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.1")

    // Add Splash Screen support
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Add Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // OpenAI client dependencies
    implementation("com.aallam.openai:openai-client:3.6.3")
    implementation("io.ktor:ktor-client-android:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
    implementation("io.ktor:ktor-serialization-gson:2.3.8")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // CameraX for better camera handling (optional but recommended)
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // Image Processing
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Appwrite SDK
    implementation("io.appwrite:sdk-for-android:7.0.0")
    // implementation("io.appwrite:sdk-for-kotlin:7.0.0")


}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}