plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "snapshotor.provider.appmanager"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        aidl = true
    }
    sourceSets {
        getByName("main") {
            aidl {
                srcDirs("src/main/aidl")
            }
        }
    }
}

dependencies {
    implementation(project(":api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Root service dependencies
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)

    // Hidden API
    implementation(project(":hiddenapi"))
    implementation(project(":systemapi"))
    implementation("tiiehenry.nota.toolkit:android-common:+")

    // Native library
    implementation(project(":native"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Compression
    implementation(libs.zstd.jni)
}
