plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hhst.dydownloader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hhst.dydownloader"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        // Picasso bundles notification support; this app only uses its image views.
        disable += "NotificationPermission"
        disable += "AndroidGradlePluginVersion"
        disable += "GradleDependency"
        disable += "AlwaysShowAction"
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.room.runtime)
    implementation(libs.picasso)
    implementation(libs.photoview)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
