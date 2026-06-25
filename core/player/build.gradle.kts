import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    // Kotlin plugin is built-in to AGP 9.0
}

extensions.configure<LibraryExtension> {
    namespace = "com.wapp.wearmusic.core.player"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
