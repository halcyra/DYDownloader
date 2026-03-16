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
        versionCode = 2
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
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
    implementation(libs.commons.io)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.picasso)
    implementation(libs.photoview)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
