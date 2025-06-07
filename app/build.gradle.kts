plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wapp.wearmusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wapp.wearmusic"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Enable core library desugaring for Horologist libraries
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Add desugaring support for Java 8+ features
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    /* ---------- Wear Compose ---------- */

    // 2. Foundation now hosts Swipe-to-Dismiss, ScalingLazyColumn, etc.
    implementation("androidx.wear.compose:compose-foundation:1.5.0-beta03")

    // 3. Material 2 for Wear (Buttons, Text, etc.)
    implementation("androidx.wear.compose:compose-material:1.5.0-beta03")

    // 4. (Optional) Material 3 wrapper – comment in if you start migrating
    // implementation("androidx.wear.compose:compose-material3")

    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    // Updated Wear Tiles dependencies
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.3.0")
    debugImplementation("androidx.wear.tiles:tiles-renderer:1.5.0")
    implementation("com.google.android.horologist:horologist-compose-tools:0.7.14-beta")
    implementation("com.google.android.horologist:horologist-tiles:0.7.14-beta")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    
    // Media3 dependencies
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")

    // Media notifications support
    implementation(libs.androidx.media)
    
    // Lifecycle components
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // Coil for image loading
    implementation(libs.coil.compose)
    
    // Horologist for Wear OS specific components
    implementation("com.google.android.horologist:horologist-media-ui:0.7.14-beta")
    implementation("com.google.android.horologist:horologist-media:0.7.14-beta")
    implementation("com.google.android.horologist:horologist-compose-layout:0.7.14-beta")
    
    // Use standard Material icons instead of Wear OS specific ones
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // androidTestImplementation(platform("androidx.wear.compose:compose-foundation:1.5.0-beta03"))
    // androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.tiles.tooling)
}