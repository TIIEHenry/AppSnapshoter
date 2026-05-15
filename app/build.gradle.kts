plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    id("kotlin-kapt")
    alias(libs.plugins.refine)
}

android {
    namespace = "tiiehenry.android.app.snapshot"
    compileSdk = 36

    defaultConfig {
        applicationId = "tiiehenry.android.app.snapshot"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        }
    }
}

dependencies {
    implementation(project(":api"))
    
    // Provider implementations
    implementation(project(":provider"))
    implementation(project(":hiddenapi"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Jetpack Components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    
    // MMKV
    implementation(libs.mmkv)
    
    // FastJSON2
    implementation(libs.fastjson2)
    
    // Glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DocumentFile
    implementation(libs.androidx.documentfile)

    // Root service libraries
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}