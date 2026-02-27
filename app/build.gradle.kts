plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    alias(libs.plugins.refine)
}

android {
    namespace = "tiiehenry.android.app.snapshotor"
    compileSdk = 36

    defaultConfig {
        applicationId = "tiiehenry.android.app.snapshotor"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":api"))
    
    // Provider implementations
    implementation(project(":provider-appmanager"))
     implementation(project(":provider-filesystem"))
    implementation(project(":provider-datasyncer"))
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

    // Root service libraries
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}